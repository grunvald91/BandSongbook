package com.fithealthzone.bandsongbook.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode {
    DARK,
    LIGHT
}

enum class LibraryMode {
    LOCAL,
    GROUP
}

data class DisplaySettings(
    val preferFlats: Boolean,
    val themeMode: ThemeMode,
    val lyricsFontSp: Int,
    val chordsFontSp: Int
)

data class SyncSettings(
    val baseUrl: String,
    val groupCode: String,
    val memberName: String,
    val authToken: String,
    val backgroundEnabled: Boolean,
    val intervalMinutes: Int,
    val lastSyncSuccessEpochMs: Long
)

class SettingsRepository(private val context: Context) {
    private val AUTO_SCROLL_SPEED = floatPreferencesKey("auto_scroll_speed")

    private val DISPLAY_PREFER_FLATS = booleanPreferencesKey("display_prefer_flats")
    private val DISPLAY_THEME_MODE = stringPreferencesKey("display_theme_mode")
    private val DISPLAY_LYRICS_FONT_SP = intPreferencesKey("display_lyrics_font_sp")
    private val DISPLAY_CHORDS_FONT_SP = intPreferencesKey("display_chords_font_sp")
    private val LIBRARY_MODE = stringPreferencesKey("library_mode")

    private val SYNC_BASE_URL = stringPreferencesKey("sync_base_url")
    private val SYNC_GROUP_CODE = stringPreferencesKey("sync_group_code")
    private val SYNC_MEMBER_NAME = stringPreferencesKey("sync_member_name")
    private val SYNC_AUTH_TOKEN = stringPreferencesKey("sync_auth_token")
    private val SYNC_BACKGROUND_ENABLED = booleanPreferencesKey("sync_background_enabled")
    private val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
    private val SYNC_LAST_SUCCESS_EPOCH_MS = longPreferencesKey("sync_last_success_epoch_ms")

    val autoScrollSpeed: Flow<Float> = context.dataStore.data.map { it[AUTO_SCROLL_SPEED] ?: 1.0f }

    val displaySettings: Flow<DisplaySettings> = context.dataStore.data.map {
        val theme = when ((it[DISPLAY_THEME_MODE] ?: ThemeMode.DARK.name).uppercase()) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            else -> ThemeMode.DARK
        }
        DisplaySettings(
            preferFlats = it[DISPLAY_PREFER_FLATS] ?: false,
            themeMode = theme,
            lyricsFontSp = (it[DISPLAY_LYRICS_FONT_SP] ?: 16).coerceIn(12, 30),
            chordsFontSp = (it[DISPLAY_CHORDS_FONT_SP] ?: 14).coerceIn(10, 28)
        )
    }

    val libraryMode: Flow<LibraryMode> = context.dataStore.data.map {
        when ((it[LIBRARY_MODE] ?: LibraryMode.LOCAL.name).uppercase()) {
            LibraryMode.GROUP.name -> LibraryMode.GROUP
            else -> LibraryMode.LOCAL
        }
    }

    val syncSettings: Flow<SyncSettings> = context.dataStore.data.map {
        SyncSettings(
            baseUrl = it[SYNC_BASE_URL] ?: "",
            groupCode = it[SYNC_GROUP_CODE] ?: "",
            memberName = it[SYNC_MEMBER_NAME] ?: "",
            authToken = it[SYNC_AUTH_TOKEN] ?: "",
            backgroundEnabled = it[SYNC_BACKGROUND_ENABLED] ?: false,
            intervalMinutes = (it[SYNC_INTERVAL_MINUTES] ?: 15).coerceAtLeast(15),
            lastSyncSuccessEpochMs = it[SYNC_LAST_SUCCESS_EPOCH_MS] ?: 0L
        )
    }

    suspend fun setAutoScrollSpeed(speed: Float) {
        context.dataStore.edit { it[AUTO_SCROLL_SPEED] = speed }
    }

    suspend fun setDisplaySettings(preferFlats: Boolean, themeMode: ThemeMode, lyricsFontSp: Int, chordsFontSp: Int) {
        context.dataStore.edit {
            it[DISPLAY_PREFER_FLATS] = preferFlats
            it[DISPLAY_THEME_MODE] = themeMode.name
            it[DISPLAY_LYRICS_FONT_SP] = lyricsFontSp.coerceIn(12, 30)
            it[DISPLAY_CHORDS_FONT_SP] = chordsFontSp.coerceIn(10, 28)
        }
    }

    suspend fun setLibraryMode(mode: LibraryMode) {
        context.dataStore.edit {
            it[LIBRARY_MODE] = mode.name
        }
    }

    suspend fun saveSyncSettings(baseUrl: String, groupCode: String, memberName: String, authToken: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedGroupCode = groupCode.trim()
        val normalizedMemberName = memberName.trim()
        val normalizedAuthToken = authToken.trim().replace(Regex("(?i)^bearer\\s+"), "")

        context.dataStore.edit {
            it[SYNC_BASE_URL] = normalizedBaseUrl
            it[SYNC_GROUP_CODE] = normalizedGroupCode
            it[SYNC_MEMBER_NAME] = normalizedMemberName
            it[SYNC_AUTH_TOKEN] = normalizedAuthToken
        }
    }

    suspend fun clearSyncSettings() {
        context.dataStore.edit {
            it[SYNC_BASE_URL] = ""
            it[SYNC_GROUP_CODE] = ""
            it[SYNC_MEMBER_NAME] = ""
            it[SYNC_AUTH_TOKEN] = ""
            it[SYNC_BACKGROUND_ENABLED] = false
        }
    }

    suspend fun setLastSyncSuccessEpochMs(epochMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[SYNC_LAST_SUCCESS_EPOCH_MS] = epochMs
        }
    }

    suspend fun setBackgroundSync(enabled: Boolean, intervalMinutes: Int) {
        context.dataStore.edit {
            it[SYNC_BACKGROUND_ENABLED] = enabled
            it[SYNC_INTERVAL_MINUTES] = intervalMinutes.coerceAtLeast(15)
        }
    }

    suspend fun getSyncSettingsSnapshot(): SyncSettings = syncSettings.first()

    suspend fun getLibraryModeSnapshot(): LibraryMode = libraryMode.first()
}
