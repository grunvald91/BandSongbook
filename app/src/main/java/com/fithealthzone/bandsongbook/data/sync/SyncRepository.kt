package com.fithealthzone.bandsongbook.data.sync

import android.net.Uri
import androidx.room.withTransaction
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.AppDatabase
import com.fithealthzone.bandsongbook.data.local.SetlistDao
import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import com.fithealthzone.bandsongbook.data.local.SetlistItemDao
import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongAudioDao
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongDao
import com.fithealthzone.bandsongbook.data.local.SongEntity
import kotlinx.serialization.json.Json

class SyncRepository(
    private val db: AppDatabase,
    private val songDao: SongDao,
    private val audioDao: SongAudioDao,
    private val setlistDao: SetlistDao,
    private val setlistItemDao: SetlistItemDao,
    private val api: SyncApiClient
) {
    companion object {
        private const val MAX_AUDIO_BACKFILL_BYTES = 64L * 1024L * 1024L
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    data class RemoteAudioRef(
        val objectKey: String,
        val remoteUrl: String?
    )

    suspend fun exportSnapshot(memberName: String): SyncSnapshotDto {
        // Вырезаем presigned remoteUrl у серверно-загруженных аудио: эти ссылки
        // короткоживущие и у получателей уже протухают. objectKey+contentHash —
        // канонический указатель, каждый клиент пусть сам резолвит свежую ссылку.
        val audioDtos = audioDao.getAll().map { entity ->
            if (!entity.objectKey.isNullOrBlank()) {
                entity.copy(remoteUrl = null).toDto()
            } else {
                entity.toDto()
            }
        }
        return SyncSnapshotDto(
            songs = songDao.getAll().map { it.toDto() },
            audio = audioDtos,
            setlists = setlistDao.getAll().map { it.toDto() },
            setlistItems = setlistItemDao.getAll().map { it.toDto() },
            pushedBy = memberName
        )
    }

    suspend fun exportSnapshotJson(memberName: String): String {
        return json.encodeToString(SyncSnapshotDto.serializer(), exportSnapshot(memberName))
    }

    suspend fun importSnapshotJson(raw: String) {
        val snapshot = json.decodeFromString(SyncSnapshotDto.serializer(), raw)
        importSnapshot(snapshot)
    }

    suspend fun importSnapshot(snapshot: SyncSnapshotDto) {
        db.withTransaction {
            val localSongs = songDao.getAll().associateBy { it.id }
            val incomingSongs = snapshot.songs.map { it.toEntity() }
            val mergedSongs = SyncMerge.mergeSongs(localSongs, incomingSongs)

            val localSetlists = setlistDao.getAll().associateBy { it.id }
            val incomingSetlists = snapshot.setlists.map { it.toEntity() }
            val mergedSetlists = SyncMerge.mergeSetlists(localSetlists, incomingSetlists)

            val localAudio = audioDao.getAll().associateBy { it.id }
            val incomingAudio = snapshot.audio.map { it.toEntity() }
            val mergedAudio = SyncMerge.mergeAudio(
                localAudio,
                incomingAudio,
                mergedSongs.map { it.id }.toSet()
            )

            val localItems = setlistItemDao.getAll().associateBy { it.id }
            val incomingItems = snapshot.setlistItems.map { it.toEntity() }
            val mergedItems = SyncMerge.mergeSetlistItems(
                localItems,
                incomingItems,
                mergedSetlists.map { it.id }.toSet(),
                mergedSongs.map { it.id }.toSet()
            )

            songDao.upsertAll(mergedSongs)
            setlistDao.upsertAll(mergedSetlists)
            audioDao.upsertAll(mergedAudio)
            setlistItemDao.upsertAll(mergedItems)
        }
    }

    suspend fun push(baseUrl: String, groupCode: String, authToken: String, memberName: String) {
        backfillMissingRemoteAudio(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            memberName = memberName
        )
        val snapshot = exportSnapshot(memberName)
        api.push(baseUrl, groupCode, authToken, snapshot)
    }

    suspend fun pull(baseUrl: String, groupCode: String, authToken: String, memberName: String = ""): SyncSnapshotDto {
        val snapshot = api.pull(baseUrl, groupCode, authToken)
        importSnapshot(snapshot)
        // Если на устройстве остались локально добавленные аудио без objectKey
        // (не загрузились при добавлении, например, из-за отсутствия сети) — тихо
        // пробуем залить их на сервер прямо здесь, чтобы другие участники увидели
        // файл уже при следующем pull.
        runCatching {
            backfillMissingRemoteAudio(
                baseUrl = baseUrl,
                groupCode = groupCode,
                authToken = authToken,
                memberName = memberName.ifBlank { "Неизвестно" }
            )
        }
        return snapshot
    }

    suspend fun roundTrip(baseUrl: String, groupCode: String, authToken: String, memberName: String): SyncSnapshotDto {
        val snapshot = pull(baseUrl, groupCode, authToken, memberName)
        push(baseUrl, groupCode, authToken, memberName)
        return snapshot
    }

    suspend fun fetchGroupMeta(baseUrl: String, groupCode: String, authToken: String): SyncMetaDto {
        return api.meta(baseUrl, groupCode, authToken)
    }

    suspend fun resolveAudioDownloadUrl(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        objectKey: String
    ): String {
        return api.audioDownloadUrl(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            objectKey = objectKey
        ).downloadUrl
    }

    suspend fun resolveAudioByHash(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        contentHash: String
    ): RemoteAudioRef? {
        val exists = api.audioExists(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            contentHash = contentHash
        )
        val objectKey = exists.objectKey?.takeIf { it.isNotBlank() } ?: return null
        val downloadUrl = api.audioDownloadUrl(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            objectKey = objectKey
        ).downloadUrl
        return RemoteAudioRef(objectKey = objectKey, remoteUrl = downloadUrl)
    }

    suspend fun resolveRemoteAudio(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        memberName: String,
        title: String,
        contentHash: String,
        mimeType: String?,
        sizeBytes: Long?,
        durationMs: Long?,
        fileName: String?,
        fileBytes: ByteArray
    ): RemoteAudioRef {
        val exists = api.audioExists(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            contentHash = contentHash
        )

        if (exists.exists && !exists.objectKey.isNullOrBlank()) {
            val objectKey = exists.objectKey
            val download = api.audioDownloadUrl(
                baseUrl = baseUrl,
                groupCode = groupCode,
                authToken = authToken,
                objectKey = objectKey
            )
            return RemoteAudioRef(objectKey = objectKey, remoteUrl = download.downloadUrl)
        }

        val upload = api.audioUploadUrl(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            contentHash = contentHash,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            fileName = fileName
        )

        if (!upload.exists) {
            val uploadUrl = upload.uploadUrl ?: error("Upload URL is empty")
            api.uploadBinary(
                uploadUrl = uploadUrl,
                bytes = fileBytes,
                mimeType = mimeType,
                headers = upload.headers
            )
        }

        val confirmed = api.audioConfirm(
            baseUrl = baseUrl,
            authToken = authToken,
            request = AudioConfirmRequest(
                groupCode = groupCode,
                objectKey = upload.objectKey,
                contentHash = contentHash,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                durationMs = durationMs,
                title = title,
                uploadedBy = memberName
            )
        )

        val remoteUrl = confirmed.remoteUrl ?: api.audioDownloadUrl(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            objectKey = confirmed.objectKey
        ).downloadUrl

        return RemoteAudioRef(objectKey = confirmed.objectKey, remoteUrl = remoteUrl)
    }

    suspend fun backfillMissingRemoteAudio(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        memberName: String
    ) {
        val resolver = AppContainer.appContext.contentResolver
        val audioRows = audioDao.getAll().filter { entity ->
            entity.deletedAt == null &&
                entity.contentHash?.isNotBlank() == true &&
                entity.objectKey.isNullOrBlank() &&
                entity.uri.isNotBlank()
        }

        audioRows.forEach { entity ->
            val parsed = runCatching { Uri.parse(entity.uri) }.getOrNull() ?: return@forEach
            val fileBytes = runCatching {
                resolver.openInputStream(parsed)?.use { input ->
                    readBytesWithLimit(input, MAX_AUDIO_BACKFILL_BYTES)
                }
            }.getOrNull() ?: return@forEach

            val remoteRef = runCatching {
                resolveRemoteAudio(
                    baseUrl = baseUrl,
                    groupCode = groupCode,
                    authToken = authToken,
                    memberName = entity.uploadedBy?.takeIf { it.isNotBlank() } ?: memberName,
                    title = entity.title,
                    contentHash = entity.contentHash.orEmpty(),
                    mimeType = entity.mimeType,
                    sizeBytes = entity.sizeBytes ?: fileBytes.size.toLong(),
                    durationMs = entity.durationMs,
                    fileName = entity.title,
                    fileBytes = fileBytes
                )
            }.getOrNull() ?: return@forEach

            audioDao.insert(
                entity.copy(
                    remoteUrl = remoteRef.remoteUrl,
                    objectKey = remoteRef.objectKey,
                    sizeBytes = entity.sizeBytes ?: fileBytes.size.toLong(),
                    uploadedBy = entity.uploadedBy ?: memberName,
                    deletedAt = null
                )
            )
        }
    }

    private fun readBytesWithLimit(input: java.io.InputStream, limitBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limitBytes) {
                throw IllegalStateException("Audio file is too large (>${limitBytes} bytes)")
            }
            out.write(buffer, 0, read)
        }

        return out.toByteArray()
    }
}
