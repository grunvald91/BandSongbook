package com.fithealthzone.bandsongbook.ui.screens

import com.fithealthzone.bandsongbook.data.local.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SongLibraryFilterTest {

    private val songs = listOf(
        song(id = "1", title = "Звезда", artist = "Кино", updatedAt = 100L),
        song(id = "2", title = "Группа крови", artist = "Виктор Цой", updatedAt = 300L),
        song(id = "3", title = "Весна", artist = null, updatedAt = 200L)
    )

    @Test
    fun `blank query keeps all songs and sorts by title`() {
        val result = filterAndSortSongs(songs, "  ", SongLibrarySort.TITLE)

        assertEquals(listOf("Весна", "Группа крови", "Звезда"), result.map { it.title })
    }

    @Test
    fun `search matches title ignoring case and surrounding spaces`() {
        val result = filterAndSortSongs(songs, "  ГРУППА  ", SongLibrarySort.TITLE)

        assertEquals(listOf("Группа крови"), result.map { it.title })
    }

    @Test
    fun `search matches artist ignoring case`() {
        val result = filterAndSortSongs(songs, "кино", SongLibrarySort.TITLE)

        assertEquals(listOf("Звезда"), result.map { it.title })
    }

    @Test
    fun `recent sort orders matches by updated time descending`() {
        val result = filterAndSortSongs(songs, "", SongLibrarySort.RECENT)

        assertEquals(listOf("Группа крови", "Весна", "Звезда"), result.map { it.title })
    }

    private fun song(
        id: String,
        title: String,
        artist: String?,
        updatedAt: Long
    ) = SongEntity(
        id = id,
        title = title,
        artist = artist,
        lyricsWithChords = "Текст",
        updatedAt = updatedAt
    )
}
