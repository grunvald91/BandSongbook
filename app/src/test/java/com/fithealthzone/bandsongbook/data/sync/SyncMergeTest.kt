package com.fithealthzone.bandsongbook.data.sync

import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    // --- Helpers ---

    private fun song(
        id: String = "s1",
        title: String = "Hello",
        artist: String? = null,
        lyrics: String = "[C]text",
        currentTranspose: Int = 0,
        preferFlats: Boolean = false,
        autoScrollSpeed: Float = 1.0f,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null
    ) = SongEntity(
        id = id,
        title = title,
        artist = artist,
        originalKey = "C",
        currentTranspose = currentTranspose,
        preferFlats = preferFlats,
        autoScrollSpeed = autoScrollSpeed,
        bpm = null,
        capo = null,
        lyricsWithChords = lyrics,
        notes = null,
        createdAt = 0L,
        updatedAt = updatedAt,
        createdBy = null,
        deletedAt = deletedAt
    )

    private fun audio(
        id: String,
        songId: String,
        remoteUrl: String? = null,
        objectKey: String? = null,
        contentHash: String? = null,
        sizeBytes: Long? = null,
        mimeType: String? = null,
        durationMs: Long? = null,
        addedAt: Long = 1_000L,
        deletedAt: Long? = null
    ) = SongAudioEntity(
        id = id,
        songId = songId,
        title = "audio $id",
        uri = "",
        remoteUrl = remoteUrl,
        objectKey = objectKey,
        contentHash = contentHash,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        durationMs = durationMs,
        uploadedBy = null,
        addedAt = addedAt,
        deletedAt = deletedAt
    )

    private fun setlistItem(
        id: String,
        setlistId: String,
        songId: String,
        orderIndex: Int = 0,
        transposeOverride: Int? = null,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null
    ) = SetlistItemEntity(
        id = id,
        setlistId = setlistId,
        songId = songId,
        orderIndex = orderIndex,
        transposeOverride = transposeOverride,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    // --- mergeSongs: базовое поведение ---

    @Test
    fun `mergeSongs adds new remote song when local has nothing`() {
        val merged = SyncMerge.mergeSongs(
            local = emptyMap(),
            incoming = listOf(song(id = "s1", title = "Remote"))
        )
        assertEquals(1, merged.size)
        assertEquals("Remote", merged.single().title)
    }

    @Test
    fun `mergeSongs prefers newer remote by updatedAt for content fields`() {
        val local = song(id = "s1", title = "Old local", updatedAt = 100L)
        val remote = song(id = "s1", title = "New remote", updatedAt = 200L)

        val merged = SyncMerge.mergeSongs(mapOf("s1" to local), listOf(remote))
        assertEquals("New remote", merged.single().title)
    }

    @Test
    fun `mergeSongs keeps local when local is newer`() {
        val local = song(id = "s1", title = "Local newer", updatedAt = 500L)
        val remote = song(id = "s1", title = "Remote older", updatedAt = 100L)

        val merged = SyncMerge.mergeSongs(mapOf("s1" to local), listOf(remote))
        assertEquals("Local newer", merged.single().title)
    }

    // --- A7: пользовательские настройки всегда локальные ---

    @Test
    fun `mergeSongs preserves local currentTranspose when remote wins content`() {
        val local = song(id = "s1", title = "old title", currentTranspose = 5, updatedAt = 100L)
        val remote = song(id = "s1", title = "new title", currentTranspose = -3, updatedAt = 200L)

        val merged = SyncMerge.mergeSongs(mapOf("s1" to local), listOf(remote)).single()

        // содержательное поле — с удалённого устройства
        assertEquals("new title", merged.title)
        // личная настройка транспонирования — СВОЯ, не +(-3) с чужого устройства
        assertEquals(5, merged.currentTranspose)
    }

    @Test
    fun `mergeSongs preserves local preferFlats and autoScrollSpeed when remote wins`() {
        val local = song(
            id = "s1",
            preferFlats = true,
            autoScrollSpeed = 2.5f,
            updatedAt = 100L
        )
        val remote = song(
            id = "s1",
            preferFlats = false,
            autoScrollSpeed = 1.0f,
            updatedAt = 300L
        )

        val merged = SyncMerge.mergeSongs(mapOf("s1" to local), listOf(remote)).single()
        assertTrue("preferFlats должен остаться локальным", merged.preferFlats)
        assertEquals(2.5f, merged.autoScrollSpeed, 0.0001f)
    }

    @Test
    fun `mergeSongs uses remote preferences when song is brand new for this device`() {
        // Если у устройства локально записи не было — берём дефолты удалённой сущности как есть.
        val remote = song(id = "s1", currentTranspose = -2, preferFlats = true, autoScrollSpeed = 1.5f)
        val merged = SyncMerge.mergeSongs(emptyMap(), listOf(remote)).single()
        assertEquals(-2, merged.currentTranspose)
        assertTrue(merged.preferFlats)
        assertEquals(1.5f, merged.autoScrollSpeed, 0.0001f)
    }

    // --- Удаления через tombstone ---

    @Test
    fun `mergeSongs honours remote tombstone via deletedAt`() {
        val local = song(id = "s1", updatedAt = 100L)
        val remote = song(id = "s1", updatedAt = 200L, deletedAt = 250L)
        val merged = SyncMerge.mergeSongs(mapOf("s1" to local), listOf(remote)).single()
        assertNotNull(merged.deletedAt)
    }

    // --- mergeSetlistItems: личный transposeOverride не затирается ---

    @Test
    fun `mergeSetlistItems preserves local transposeOverride when remote wins order`() {
        val local = setlistItem(
            id = "i1",
            setlistId = "sl1",
            songId = "s1",
            orderIndex = 0,
            transposeOverride = 4,
            updatedAt = 100L
        )
        val remote = setlistItem(
            id = "i1",
            setlistId = "sl1",
            songId = "s1",
            orderIndex = 3,
            transposeOverride = -2,
            updatedAt = 500L
        )
        val merged = SyncMerge.mergeSetlistItems(
            local = mapOf("i1" to local),
            incoming = listOf(remote),
            allowedSetlistIds = setOf("sl1"),
            allowedSongIds = setOf("s1")
        ).single()

        assertEquals(3, merged.orderIndex)          // порядок из remote
        assertEquals(4, merged.transposeOverride)   // override остался свой
    }

    @Test
    fun `mergeSetlistItems drops items pointing to unknown songs or setlists`() {
        val item = setlistItem(id = "i1", setlistId = "ghost", songId = "s1")
        val merged = SyncMerge.mergeSetlistItems(
            local = emptyMap(),
            incoming = listOf(item),
            allowedSetlistIds = emptySet(),
            allowedSongIds = setOf("s1")
        )
        assertTrue(merged.isEmpty())
    }

    // --- mergeAudio: enrichment по contentHash ---

    @Test
    fun `mergeAudio enriches missing objectKey from sibling with same contentHash`() {
        val rich = audio(
            id = "a1",
            songId = "s1",
            objectKey = "canonical-key",
            remoteUrl = "https://cdn/abc",
            contentHash = "hash-1",
            sizeBytes = 123L,
            mimeType = "audio/mp3",
            durationMs = 5000L
        )
        val poor = audio(
            id = "a2",
            songId = "s1",
            contentHash = "hash-1"
        )

        val merged = SyncMerge.mergeAudio(
            local = mapOf("a1" to rich, "a2" to poor),
            incoming = emptyList(),
            allowedSongIds = setOf("s1")
        )
        val a2 = merged.first { it.id == "a2" }
        assertEquals("canonical-key", a2.objectKey)
        assertEquals("https://cdn/abc", a2.remoteUrl)
        assertEquals(123L, a2.sizeBytes)
    }

    @Test
    fun `mergeAudio drops audio pointing at unknown song`() {
        val orphan = audio(id = "a1", songId = "ghost", contentHash = "h")
        val merged = SyncMerge.mergeAudio(
            local = mapOf("a1" to orphan),
            incoming = emptyList(),
            allowedSongIds = setOf("s1")
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `mergeAudio keeps local when newer than incoming`() {
        val local = audio(id = "a1", songId = "s1", addedAt = 500L, objectKey = "local-key")
        val remote = audio(id = "a1", songId = "s1", addedAt = 100L, objectKey = "remote-key")
        val merged = SyncMerge.mergeAudio(
            local = mapOf("a1" to local),
            incoming = listOf(remote),
            allowedSongIds = setOf("s1")
        )
        assertEquals("local-key", merged.single().objectKey)
    }

    // --- Tombstone audio сохраняется ---

    @Test
    fun `mergeAudio replaces with remote tombstone when deletedAt is newer`() {
        val local = audio(id = "a1", songId = "s1", addedAt = 100L)
        val remote = audio(id = "a1", songId = "s1", addedAt = 100L, deletedAt = 500L)
        val merged = SyncMerge.mergeAudio(
            local = mapOf("a1" to local),
            incoming = listOf(remote),
            allowedSongIds = setOf("s1")
        )
        assertNotNull(merged.single().deletedAt)
    }

    @Test
    fun `mergeAudio without contentHash leaves entity unchanged`() {
        val local = audio(id = "a1", songId = "s1", contentHash = null, objectKey = null)
        val merged = SyncMerge.mergeAudio(
            local = mapOf("a1" to local),
            incoming = emptyList(),
            allowedSongIds = setOf("s1")
        )
        assertEquals(1, merged.size)
        assertNull(merged.single().objectKey)
    }
}
