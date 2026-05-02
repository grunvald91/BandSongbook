package com.fithealthzone.bandsongbook.media

import android.content.Context
import android.net.Uri
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Единая точка резолвинга playback-URI для аудио-вложений.
 *
 * Логика:
 *  - URL-аудио (нет objectKey + нет contentHash) → вернуть сохранённый remoteUrl.
 *  - Читаемый локальный URI (устройство-источник) → вернуть его.
 *  - Иначе, если sync настроен → получить свежую presigned-ссылку с сервера.
 *    Сохранённый remoteUrl (присланный чужим устройством) — короткоживущий, его
 *    нельзя использовать напрямую.
 *
 *  Весь метод обёрнут в [withTimeoutOrNull]: тухлая сеть больше не морозит UI.
 */
object AudioPlaybackResolver {

    private const val RESOLVE_TIMEOUT_MS = 8_000L

    suspend fun resolve(context: Context, audio: SongAudioEntity): String? {
        return withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            resolveInternal(context, audio)
        }
    }

    private suspend fun resolveInternal(context: Context, audio: SongAudioEntity): String? {
        val local = audio.uri.takeIf { it.isNotBlank() }
        val storedRemote = audio.remoteUrl?.takeIf { it.isNotBlank() }
        val objectKey = audio.objectKey?.takeIf { it.isNotBlank() }
        val contentHash = audio.contentHash?.takeIf { it.isNotBlank() }

        // 1. Аудио-по-ссылке (нет objectKey/contentHash): URL постоянный, играем как есть.
        if (objectKey == null && contentHash == null && storedRemote != null) {
            return storedRemote
        }

        // 2. Устройство-источник — локальный URI всё ещё читается, играем без сети.
        if (local != null && isLocalUriReadable(context, local)) {
            return local
        }

        // 3. Любое другое устройство — всегда берём СВЕЖИЙ presigned-URL с сервера.
        val syncSettings = runCatching {
            AppContainer.settingsRepository.getSyncSettingsSnapshot()
        }.getOrNull()

        if (syncSettings != null &&
            syncSettings.baseUrl.isNotBlank() &&
            syncSettings.groupCode.isNotBlank()
        ) {
            if (objectKey != null) {
                val refreshed = runCatching {
                    withContext(Dispatchers.IO) {
                        AppContainer.syncRepository.resolveAudioDownloadUrl(
                            baseUrl = syncSettings.baseUrl,
                            groupCode = syncSettings.groupCode,
                            authToken = syncSettings.authToken,
                            objectKey = objectKey
                        )
                    }
                }.getOrNull()
                if (!refreshed.isNullOrBlank()) return refreshed
            }

            if (contentHash != null) {
                val byHash = runCatching {
                    withContext(Dispatchers.IO) {
                        AppContainer.syncRepository.resolveAudioByHash(
                            baseUrl = syncSettings.baseUrl,
                            groupCode = syncSettings.groupCode,
                            authToken = syncSettings.authToken,
                            contentHash = contentHash
                        )
                    }
                }.getOrNull()
                byHash?.remoteUrl?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        // 4. Последняя соломинка для данных, синхронизированных до фикса схемы.
        // URL скорее всего протух — ExoPlayer отдаст ошибку, но попробовать стоит.
        return storedRemote
    }

    private fun isLocalUriReadable(context: Context, uriString: String): Boolean {
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (parsed.scheme.isNullOrBlank()) return false
        return runCatching {
            context.contentResolver.openInputStream(parsed)?.use { stream ->
                stream.read() >= 0
            } ?: false
        }.getOrDefault(false)
    }
}
