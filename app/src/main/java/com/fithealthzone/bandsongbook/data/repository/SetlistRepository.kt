package com.fithealthzone.bandsongbook.data.repository

import com.fithealthzone.bandsongbook.data.local.SetlistDao
import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import com.fithealthzone.bandsongbook.data.local.SetlistItemDao
import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongDao
import com.fithealthzone.bandsongbook.data.local.SongEntity
import kotlinx.coroutines.flow.Flow

class SetlistRepository(
    private val setlistDao: SetlistDao,
    private val setlistItemDao: SetlistItemDao,
    private val songDao: SongDao
) {
    fun observeSetlists(): Flow<List<SetlistEntity>> = setlistDao.observeAll()
    fun observeSetlist(id: String): Flow<SetlistEntity?> = setlistDao.observeById(id)
    fun observeItems(setlistId: String): Flow<List<SetlistItemEntity>> = setlistItemDao.observeBySetlist(setlistId)
    fun observeSongs(): Flow<List<SongEntity>> = songDao.observeAll()

    suspend fun saveSetlist(entity: SetlistEntity) = setlistDao.upsert(
        entity.copy(updatedAt = System.currentTimeMillis(), deletedAt = null)
    )

    suspend fun deleteSetlist(entity: SetlistEntity) {
        val deletedAt = System.currentTimeMillis()
        setlistDao.upsert(entity.copy(updatedAt = deletedAt, deletedAt = deletedAt))
        setlistItemDao.getAllBySetlist(entity.id).forEach { item ->
            setlistItemDao.upsert(item.copy(updatedAt = deletedAt, deletedAt = deletedAt))
        }
    }

    suspend fun addSong(setlistId: String, songId: String, nextIndex: Int) {
        setlistItemDao.upsert(
            SetlistItemEntity(
                setlistId = setlistId,
                songId = songId,
                orderIndex = nextIndex,
                updatedAt = System.currentTimeMillis(),
                deletedAt = null
            )
        )
    }

    suspend fun addSongs(setlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        val nextStartIndex = setlistItemDao.getAllBySetlist(setlistId)
            .count { it.deletedAt == null }
        val now = System.currentTimeMillis()
        val newItems = songIds.distinct().mapIndexed { offset, songId ->
            SetlistItemEntity(
                setlistId = setlistId,
                songId = songId,
                orderIndex = nextStartIndex + offset,
                updatedAt = now,
                deletedAt = null
            )
        }
        setlistItemDao.upsertAll(newItems)
    }

    suspend fun removeItem(item: SetlistItemEntity) {
        val now = System.currentTimeMillis()
        setlistItemDao.upsert(item.copy(updatedAt = now, deletedAt = now))
    }

    suspend fun reorder(items: List<SetlistItemEntity>) {
        val now = System.currentTimeMillis()
        items.forEachIndexed { index, item ->
            setlistItemDao.update(item.copy(orderIndex = index, updatedAt = now))
        }
    }
}
