package com.fithealthzone.bandsongbook

import android.content.Context
import com.fithealthzone.bandsongbook.data.local.AppDatabase
import com.fithealthzone.bandsongbook.data.repository.SetlistRepository
import com.fithealthzone.bandsongbook.data.repository.SongRepository
import com.fithealthzone.bandsongbook.data.settings.SettingsRepository
import com.fithealthzone.bandsongbook.data.sync.SyncApiClient
import com.fithealthzone.bandsongbook.data.sync.SyncRepository

object AppContainer {
    data class SetlistSongBrowseContext(
        val setlistId: String,
        val orderedSongIds: List<String>
    )

    private var initialized = false

    var setlistSongBrowseContext: SetlistSongBrowseContext? = null
        private set

    var songViewerFullscreen: Boolean = false

    lateinit var appContext: Context
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var songRepository: SongRepository
        private set
    lateinit var setlistRepository: SetlistRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var syncRepository: SyncRepository
        private set

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        db = AppDatabase.getInstance(appContext)
        songRepository = SongRepository(db.songDao(), db.songAudioDao(), db.setlistItemDao())
        setlistRepository = SetlistRepository(db.setlistDao(), db.setlistItemDao(), db.songDao())
        settingsRepository = SettingsRepository(appContext)
        syncRepository = SyncRepository(
            db = db,
            songDao = db.songDao(),
            audioDao = db.songAudioDao(),
            setlistDao = db.setlistDao(),
            setlistItemDao = db.setlistItemDao(),
            api = SyncApiClient()
        )
        initialized = true
    }

    fun setSetlistSongBrowseContext(setlistId: String, orderedSongIds: List<String>) {
        setlistSongBrowseContext = SetlistSongBrowseContext(
            setlistId = setlistId,
            orderedSongIds = orderedSongIds
        )
    }

    fun clearSetlistSongBrowseContext() {
        setlistSongBrowseContext = null
    }

    fun nextSongIdFromSetlist(currentSongId: String): String? {
        val context = setlistSongBrowseContext ?: return null
        val currentIndex = context.orderedSongIds.indexOf(currentSongId)
        if (currentIndex == -1) return null
        return context.orderedSongIds.getOrNull(currentIndex + 1)
    }

    fun previousSongIdFromSetlist(currentSongId: String): String? {
        val context = setlistSongBrowseContext ?: return null
        val currentIndex = context.orderedSongIds.indexOf(currentSongId)
        if (currentIndex == -1) return null
        return context.orderedSongIds.getOrNull(currentIndex - 1)
    }
}
