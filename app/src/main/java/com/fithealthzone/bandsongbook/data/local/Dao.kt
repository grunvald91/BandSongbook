package com.fithealthzone.bandsongbook.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeById(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: String): SongEntity?

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs")
    suspend fun clearAll()
}

@Dao
interface SongAudioDao {
    @Query("SELECT * FROM song_audio WHERE songId = :songId AND deletedAt IS NULL ORDER BY addedAt ASC")
    fun observeBySong(songId: String): Flow<List<SongAudioEntity>>

    @Query("SELECT * FROM song_audio WHERE deletedAt IS NULL ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<SongAudioEntity>>

    @Query("SELECT * FROM song_audio")
    suspend fun getAll(): List<SongAudioEntity>

    @Query("SELECT * FROM song_audio WHERE contentHash = :contentHash AND deletedAt IS NULL LIMIT 1")
    suspend fun getByContentHash(contentHash: String): SongAudioEntity?

    @Query("SELECT * FROM song_audio WHERE songId = :songId AND contentHash = :contentHash AND deletedAt IS NULL LIMIT 1")
    suspend fun getBySongAndContentHash(songId: String, contentHash: String): SongAudioEntity?

    @Query("SELECT * FROM song_audio WHERE songId = :songId")
    suspend fun getAllBySong(songId: String): List<SongAudioEntity>

    @Upsert
    suspend fun insert(audio: SongAudioEntity)

    @Upsert
    suspend fun upsertAll(audio: List<SongAudioEntity>)

    @Delete
    suspend fun delete(audio: SongAudioEntity)

    @Query("DELETE FROM song_audio")
    suspend fun clearAll()
}

@Dao
interface SetlistDao {
    @Query("SELECT * FROM setlists WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SetlistEntity>>

    @Query("SELECT * FROM setlists")
    suspend fun getAll(): List<SetlistEntity>

    @Query("SELECT * FROM setlists WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeById(id: String): Flow<SetlistEntity?>

    @Upsert
    suspend fun upsert(entity: SetlistEntity)

    @Upsert
    suspend fun upsertAll(items: List<SetlistEntity>)

    @Delete
    suspend fun delete(entity: SetlistEntity)

    @Query("DELETE FROM setlists")
    suspend fun clearAll()
}

@Dao
interface SetlistItemDao {
    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId AND deletedAt IS NULL ORDER BY orderIndex ASC")
    fun observeBySetlist(setlistId: String): Flow<List<SetlistItemEntity>>

    @Query("SELECT * FROM setlist_items")
    suspend fun getAll(): List<SetlistItemEntity>

    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun getAllBySetlist(setlistId: String): List<SetlistItemEntity>

    @Query("SELECT * FROM setlist_items WHERE songId = :songId")
    suspend fun getAllBySong(songId: String): List<SetlistItemEntity>

    @Upsert
    suspend fun upsert(item: SetlistItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<SetlistItemEntity>)

    @Update
    suspend fun update(item: SetlistItemEntity)

    @Delete
    suspend fun delete(item: SetlistItemEntity)

    @Query("DELETE FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun clearSetlist(setlistId: String)

    @Query("DELETE FROM setlist_items")
    suspend fun clearAll()
}
