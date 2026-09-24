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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val snapshotMutex = Mutex()

    data class RemoteAudioRef(
        val objectKey: String,
        val remoteUrl: String?
    )

    suspend fun exportSnapshot(memberName: String): SyncSnapshotDto =
        snapshotMutex.withLock { exportSnapshotUnlocked(memberName) }

    private suspend fun exportSnapshotUnlocked(memberName: String): SyncSnapshotDto {
        // Вырезаем presigned remoteUrl у серверно-загруженных аудио: эти ссылки
        // короткоживущие и у получателей уже протухают. objectKey+contentHash —
        // канонический указатель, каждый клиент пусть сам резолвит свежую ссылку.
        val audioDtos = audioDao.getAllForSync().map { entity ->
            if (!entity.objectKey.isNullOrBlank()) {
                entity.copy(remoteUrl = null).toDto()
            } else {
                entity.toDto()
            }
        }
        // Разделяем песни/сетлисты на «живые» (в основном массиве) и tombstone'ы
        // (в отдельных массивах deletedSongs/deletedSetlists) — формат, который понимают
        // веб-клиент и сервер. Soft-deleted ряды локально лежат с deletedAt != null,
        // но в `songs[]`/`setlists[]` их класть нельзя: остальные клиенты не должны
        // увидеть «воскрешённую» запись.
        val allSongs = songDao.getAllForSync()
        val liveSongs = allSongs.filter { it.deletedAt == null }
        val deletedSongs = allSongs
            .filter { it.deletedAt != null }
            .map { SyncTombstoneDto(id = it.id, deletedAt = it.deletedAt!!, deletedBy = null) }

        val allSetlists = setlistDao.getAllForSync()
        val liveSetlists = allSetlists.filter { it.deletedAt == null }
        val deletedSetlists = allSetlists
            .filter { it.deletedAt != null }
            .map { SyncTombstoneDto(id = it.id, deletedAt = it.deletedAt!!, deletedBy = null) }

        return SyncSnapshotDto(
            songs = liveSongs.map { it.toDto() },
            audio = audioDtos,
            setlists = liveSetlists.map { it.toDto() },
            setlistItems = setlistItemDao.getAllForSync().map { it.toDto() },
            deletedSongs = deletedSongs,
            deletedSetlists = deletedSetlists,
            pushedBy = memberName
        )
    }

    suspend fun exportSnapshotJson(memberName: String): String {
        return snapshotMutex.withLock {
            json.encodeToString(SyncSnapshotDto.serializer(), exportSnapshotUnlocked(memberName))
        }
    }

    suspend fun importSnapshotJson(raw: String) {
        val snapshot = json.decodeFromString(SyncSnapshotDto.serializer(), raw)
        snapshotMutex.withLock { importSnapshotUnlocked(snapshot) }
    }

    suspend fun importSnapshot(snapshot: SyncSnapshotDto) {
        snapshotMutex.withLock { importSnapshotUnlocked(snapshot) }
    }

    private suspend fun importSnapshotUnlocked(snapshot: SyncSnapshotDto) {
        db.withTransaction {
            importSnapshotRowsUnlocked(snapshot)
        }
    }

    private suspend fun importSnapshotRowsUnlocked(snapshot: SyncSnapshotDto) {
        val localSongs = songDao.getAllForSync().associateBy { it.id }
            val incomingSongs = snapshot.songs.map { it.toEntity() }
            val mergedLiveSongs = SyncMerge.mergeSongs(localSongs, incomingSongs)
            // Применяем tombstone'ы поверх результата merge — приходящее «удалить» с
            // более свежим deletedAt должно перебивать локальную живую запись.
            val mergedSongs = SyncMerge.applySongTombstones(mergedLiveSongs, snapshot.deletedSongs)

            val localSetlists = setlistDao.getAllForSync().associateBy { it.id }
            val incomingSetlists = snapshot.setlists.map { it.toEntity() }
            val mergedLiveSetlists = SyncMerge.mergeSetlists(localSetlists, incomingSetlists)
            val mergedSetlists = SyncMerge.applySetlistTombstones(mergedLiveSetlists, snapshot.deletedSetlists)

            val localAudio = audioDao.getAllForSync().associateBy { it.id }
            val incomingAudio = snapshot.audio.map { it.toEntity() }
            val mergedAudio = SyncMerge.mergeAudio(
                localAudio,
                incomingAudio,
                mergedSongs.map { it.id }.toSet()
            )

            val localItems = setlistItemDao.getAllForSync().associateBy { it.id }
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

    suspend fun activateFromRemote(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        replaceLocalLibrary: Boolean
    ): SyncSnapshotDto = snapshotMutex.withLock {
        // Fetch and validate the remote group before deleting any local rows. The
        // same mutex then keeps activation atomic with all other snapshot work.
        val snapshot = api.pull(baseUrl, groupCode, authToken)
        if (replaceLocalLibrary) {
            db.withTransaction {
                setlistItemDao.clearAll()
                audioDao.clearAll()
                setlistDao.clearAll()
                songDao.clearAll()
                importSnapshotRowsUnlocked(snapshot)
            }
        } else {
            importSnapshotUnlocked(snapshot)
        }
        snapshot
    }

    suspend fun push(baseUrl: String, groupCode: String, authToken: String, memberName: String) {
        snapshotMutex.withLock { pushUnlocked(baseUrl, groupCode, authToken, memberName) }
    }

    private suspend fun pushUnlocked(baseUrl: String, groupCode: String, authToken: String, memberName: String) {
        backfillMissingRemoteAudioUnlocked(
            baseUrl = baseUrl,
            groupCode = groupCode,
            authToken = authToken,
            memberName = memberName
        )
        val snapshot = exportSnapshotUnlocked(memberName)
        api.push(baseUrl, groupCode, authToken, snapshot)
    }

    suspend fun pull(baseUrl: String, groupCode: String, authToken: String, memberName: String = ""): SyncSnapshotDto =
        snapshotMutex.withLock { pullUnlocked(baseUrl, groupCode, authToken, memberName) }

    private suspend fun pullUnlocked(
        baseUrl: String,
        groupCode: String,
        authToken: String,
        memberName: String
    ): SyncSnapshotDto {
        val snapshot = api.pull(baseUrl, groupCode, authToken)
        importSnapshotUnlocked(snapshot)
        // Если на устройстве остались локально добавленные аудио без objectKey
        // (не загрузились при добавлении, например, из-за отсутствия сети) — тихо
        // пробуем залить их на сервер прямо здесь, чтобы другие участники увидели
        // файл уже при следующем pull.
        try {
            backfillMissingRemoteAudioUnlocked(
                baseUrl = baseUrl,
                groupCode = groupCode,
                authToken = authToken,
                memberName = memberName.ifBlank { "Неизвестно" }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Backfill is best-effort; the pulled snapshot is already imported.
        }
        return snapshot
    }

    suspend fun roundTrip(baseUrl: String, groupCode: String, authToken: String, memberName: String): SyncSnapshotDto =
        snapshotMutex.withLock {
            val snapshot = pullUnlocked(baseUrl, groupCode, authToken, memberName)
            pushUnlocked(baseUrl, groupCode, authToken, memberName)
            snapshot
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

    private suspend fun backfillMissingRemoteAudioUnlocked(
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

            val remoteRef = try {
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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            } ?: return@forEach

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
