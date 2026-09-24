package com.fithealthzone.bandsongbook.ui.screens

import com.fithealthzone.bandsongbook.data.local.SongEntity

internal enum class SongLibrarySort {
    TITLE,
    RECENT
}

internal fun filterAndSortSongs(
    songs: List<SongEntity>,
    query: String,
    sort: SongLibrarySort
): List<SongEntity> {
    val normalizedQuery = query.trim()
    val filtered = if (normalizedQuery.isEmpty()) {
        songs
    } else {
        songs.filter { song ->
            song.title.contains(normalizedQuery, ignoreCase = true) ||
                song.artist?.contains(normalizedQuery, ignoreCase = true) == true
        }
    }

    return when (sort) {
        SongLibrarySort.TITLE -> filtered.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        SongLibrarySort.RECENT -> filtered.sortedByDescending { it.updatedAt }
    }
}
