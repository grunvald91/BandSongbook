package com.fithealthzone.bandsongbook.ui

sealed class Dest(val route: String) {
    data object Songs : Dest("songs")
    data object Setlists : Dest("setlists")
    data object Settings : Dest("settings")
    data object Profile : Dest("profile")
    data object SongEditor : Dest("song_editor?songId={songId}") {
        fun create(songId: String? = null) = "song_editor?songId=${songId ?: ""}"
    }
    data object SongViewer : Dest("song_viewer/{songId}") {
        fun create(songId: String) = "song_viewer/$songId"
    }
    data object SetlistEditor : Dest("setlist_editor/{setlistId}") {
        fun create(setlistId: String) = "setlist_editor/$setlistId"
    }
}
