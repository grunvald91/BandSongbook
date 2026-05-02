package com.fithealthzone.bandsongbook.data.repository

import com.fithealthzone.bandsongbook.data.local.SetlistItemDao
import com.fithealthzone.bandsongbook.data.local.SongAudioDao
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongDao
import com.fithealthzone.bandsongbook.data.local.SongEntity
import kotlinx.coroutines.flow.Flow

class SongRepository(
    private val songDao: SongDao,
    private val audioDao: SongAudioDao,
    private val setlistItemDao: SetlistItemDao
) {
    fun observeSongs(): Flow<List<SongEntity>> = songDao.observeAll()
    fun observeSong(songId: String): Flow<SongEntity?> = songDao.observeById(songId)
    suspend fun getSong(songId: String): SongEntity? = songDao.getById(songId)

    suspend fun saveSong(song: SongEntity) {
        songDao.upsert(song.copy(updatedAt = System.currentTimeMillis(), deletedAt = null))
    }

    suspend fun setSongAutoScrollSpeed(songId: String, speed: Float) {
        val current = songDao.getById(songId) ?: return
        songDao.upsert(current.copy(autoScrollSpeed = speed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSongPreferFlats(songId: String, preferFlats: Boolean) {
        val current = songDao.getById(songId) ?: return
        songDao.upsert(current.copy(preferFlats = preferFlats, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSongTranspose(songId: String, transpose: Int) {
        val current = songDao.getById(songId) ?: return
        songDao.upsert(
            current.copy(
                currentTranspose = transpose.coerceIn(-11, 11),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSong(song: SongEntity) {
        val deletedAt = System.currentTimeMillis()
        songDao.upsert(song.copy(updatedAt = deletedAt, deletedAt = deletedAt))
        audioDao.getAllBySong(song.id).forEach { audio ->
            audioDao.insert(audio.copy(deletedAt = deletedAt))
        }
        setlistItemDao.getAllBySong(song.id).forEach { item ->
            setlistItemDao.upsert(item.copy(updatedAt = deletedAt, deletedAt = deletedAt))
        }
    }

    fun observeAudio(songId: String): Flow<List<SongAudioEntity>> = audioDao.observeBySong(songId)

    suspend fun addAudio(audio: SongAudioEntity) {
        val restoredAudio = audio.copy(deletedAt = null)
        val hash = restoredAudio.contentHash?.takeIf { it.isNotBlank() }
        if (hash != null) {
            val existingForSong = audioDao.getBySongAndContentHash(restoredAudio.songId, hash)
            if (existingForSong != null) {
                audioDao.insert(
                    existingForSong.copy(
                        title = restoredAudio.title.ifBlank { existingForSong.title },
                        uri = restoredAudio.uri.ifBlank { existingForSong.uri },
                        remoteUrl = restoredAudio.remoteUrl ?: existingForSong.remoteUrl,
                        objectKey = restoredAudio.objectKey ?: existingForSong.objectKey,
                        sizeBytes = restoredAudio.sizeBytes ?: existingForSong.sizeBytes,
                        mimeType = restoredAudio.mimeType ?: existingForSong.mimeType,
                        durationMs = restoredAudio.durationMs ?: existingForSong.durationMs,
                        uploadedBy = restoredAudio.uploadedBy ?: existingForSong.uploadedBy,
                        deletedAt = null
                    )
                )
                return
            }

            val existingAny = audioDao.getByContentHash(hash)
            if (existingAny != null) {
                audioDao.insert(
                    restoredAudio.copy(
                        remoteUrl = restoredAudio.remoteUrl ?: existingAny.remoteUrl,
                        objectKey = restoredAudio.objectKey ?: existingAny.objectKey,
                        uploadedBy = restoredAudio.uploadedBy ?: existingAny.uploadedBy
                    )
                )
                return
            }
        }

        audioDao.insert(restoredAudio)
    }

    suspend fun removeAudio(audio: SongAudioEntity) {
        audioDao.insert(audio.copy(deletedAt = System.currentTimeMillis()))
    }
}
