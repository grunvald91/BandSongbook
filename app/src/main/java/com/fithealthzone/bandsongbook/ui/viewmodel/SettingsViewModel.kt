package com.fithealthzone.bandsongbook.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import androidx.room.withTransaction
import com.fithealthzone.bandsongbook.data.settings.DisplaySettings
import com.fithealthzone.bandsongbook.data.settings.LibraryMode
import com.fithealthzone.bandsongbook.data.settings.SyncSettings
import com.fithealthzone.bandsongbook.data.settings.ThemeMode
import com.fithealthzone.bandsongbook.data.sync.SyncMetaDto
import com.fithealthzone.bandsongbook.data.sync.SyncSnapshotDto
import com.fithealthzone.bandsongbook.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GroupMemberUi(
    val name: String,
    val lastSeenAtText: String
)

data class GroupSyncMetaUi(
    val lastPushedBy: String,
    val serverUpdatedAtText: String,
    val members: List<GroupMemberUi>
)

class SettingsViewModel : ViewModel() {
    val syncSettings: StateFlow<SyncSettings> = AppContainer.settingsRepository.syncSettings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SyncSettings("", "", "", "", false, 15, 0L)
        )

    val displaySettings: StateFlow<DisplaySettings> = AppContainer.settingsRepository.displaySettings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DisplaySettings(preferFlats = false, themeMode = ThemeMode.DARK, lyricsFontSp = 16, chordsFontSp = 14)
        )

    val libraryMode: StateFlow<LibraryMode> = AppContainer.settingsRepository.libraryMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryMode.LOCAL
        )

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private val _groupSyncMeta = MutableStateFlow<GroupSyncMetaUi?>(null)
    val groupSyncMeta: StateFlow<GroupSyncMetaUi?> = _groupSyncMeta

    private val _isGroupStateRefreshing = MutableStateFlow(false)
    val isGroupStateRefreshing: StateFlow<Boolean> = _isGroupStateRefreshing

    private val _groupMetaLastRefreshEpochMs = MutableStateFlow<Long?>(null)
    val groupMetaLastRefreshEpochMs: StateFlow<Long?> = _groupMetaLastRefreshEpochMs

    fun saveSync(baseUrl: String, groupCode: String, memberName: String, authToken: String) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (!isValidGroupCode(normalizedGroup)) {
            _syncStatus.value = "Код группы некорректный. Используй латиницу/цифры, '-', '_' или '.'"
            return
        }
        viewModelScope.launch {
            val tokenToSave = authToken.trim()
                .replace(Regex("(?i)^bearer\\s+"), "")
            AppContainer.settingsRepository.saveSyncSettings(
                baseUrl.trim(),
                normalizedGroup,
                memberName.trim(),
                tokenToSave
            )
            _groupSyncMeta.value = null
            _groupMetaLastRefreshEpochMs.value = null
            _syncStatus.value = "Настройки синхронизации сохранены"
        }
    }

    fun saveDisplaySettings(preferFlats: Boolean, themeMode: ThemeMode, lyricsFontSp: Int, chordsFontSp: Int) {
        viewModelScope.launch {
            AppContainer.settingsRepository.setDisplaySettings(
                preferFlats = preferFlats,
                themeMode = themeMode,
                lyricsFontSp = lyricsFontSp,
                chordsFontSp = chordsFontSp
            )
        }
    }

    fun pushNow(baseUrl: String, groupCode: String, authToken: String, memberName: String) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (baseUrl.isBlank() || normalizedGroup.isBlank()) {
            _syncStatus.value = "Укажи URL сервера и код группы"
            return
        }
        if (!isValidGroupCode(normalizedGroup)) {
            _syncStatus.value = "Код группы некорректный. Пример: worship-band"
            return
        }
        viewModelScope.launch {
            runCatching {
                AppContainer.syncRepository.push(baseUrl.trim(), normalizedGroup, authToken.trim(), memberName.trim().ifBlank { "Неизвестно" })
            }.onSuccess {
                AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
                _syncStatus.value = "PUSH выполнен"
            }.onFailure {
                _syncStatus.value = "Ошибка PUSH: ${humanizeSyncError(it.message)}"
            }
        }
    }

    fun pullNow(baseUrl: String, groupCode: String, authToken: String) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (baseUrl.isBlank() || normalizedGroup.isBlank()) {
            _syncStatus.value = "Укажи URL сервера и код группы"
            return
        }
        if (!isValidGroupCode(normalizedGroup)) {
            _syncStatus.value = "Код группы некорректный. Пример: worship-band"
            return
        }
        viewModelScope.launch {
            runCatching {
                AppContainer.syncRepository.pull(baseUrl.trim(), normalizedGroup, authToken.trim())
            }.onSuccess { snapshot ->
                AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
                updateGroupMeta(snapshot)
                _syncStatus.value = "PULL выполнен. ${formatSyncMeta(snapshot)}"
            }.onFailure {
                _syncStatus.value = "Ошибка PULL: ${humanizeSyncError(it.message)}"
            }
        }
    }

    fun refreshGroupState(baseUrl: String, groupCode: String, authToken: String, silent: Boolean = true) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (baseUrl.isBlank() || normalizedGroup.isBlank() || !isValidGroupCode(normalizedGroup)) return
        viewModelScope.launch {
            _isGroupStateRefreshing.value = true
            runCatching {
                AppContainer.syncRepository.fetchGroupMeta(baseUrl.trim(), normalizedGroup, authToken.trim())
            }.onSuccess { meta ->
                updateGroupMeta(meta)
                if (!silent) {
                    _syncStatus.value = "Состояние группы обновлено. ${formatSyncMeta(meta)}"
                }
            }.onFailure {
                if (!silent) {
                    _syncStatus.value = "Ошибка обновления состояния группы: ${it.message}"
                }
            }
            _isGroupStateRefreshing.value = false
        }
    }

    fun syncNow(baseUrl: String, groupCode: String, authToken: String, memberName: String) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (baseUrl.isBlank() || normalizedGroup.isBlank()) {
            _syncStatus.value = "Укажи URL сервера и код группы"
            return
        }
        if (!isValidGroupCode(normalizedGroup)) {
            _syncStatus.value = "Код группы некорректный. Пример: worship-band"
            return
        }
        viewModelScope.launch {
            runCatching {
                AppContainer.syncRepository.roundTrip(
                    baseUrl.trim(),
                    normalizedGroup,
                    authToken.trim(),
                    memberName.trim().ifBlank { "Неизвестно" }
                )
            }.onSuccess { snapshot ->
                AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
                updateGroupMeta(snapshot)
                _syncStatus.value = "Синхронизация завершена. ${formatSyncMeta(snapshot)}"
            }.onFailure {
                _syncStatus.value = "Ошибка синхронизации: ${humanizeSyncError(it.message)}"
            }
        }
    }

    fun activateGroupMode(
        context: Context,
        baseUrl: String,
        groupCode: String,
        memberName: String,
        authToken: String,
        wipeLibrary: Boolean
    ) {
        val normalizedGroup = normalizeGroupCode(groupCode)
        if (baseUrl.isBlank() || normalizedGroup.isBlank()) {
            _syncStatus.value = "Укажи URL сервера и код группы"
            return
        }
        if (!isValidGroupCode(normalizedGroup)) {
            _syncStatus.value = "Код группы некорректный. Пример: worship-band"
            return
        }
        viewModelScope.launch {
            runCatching {
                if (wipeLibrary) {
                    wipeLibraryData()
                }
                AppContainer.settingsRepository.saveSyncSettings(
                    baseUrl = baseUrl.trim(),
                    groupCode = normalizedGroup,
                    memberName = memberName.trim(),
                    authToken = authToken.trim()
                )
                AppContainer.settingsRepository.setLibraryMode(LibraryMode.GROUP)
                val sync = AppContainer.settingsRepository.getSyncSettingsSnapshot()
                if (sync.backgroundEnabled) {
                    SyncScheduler.schedule(context, sync.intervalMinutes)
                }
                val snapshot = AppContainer.syncRepository.roundTrip(
                    baseUrl = baseUrl.trim(),
                    groupCode = normalizedGroup,
                    authToken = authToken.trim(),
                    memberName = memberName.trim().ifBlank { "Неизвестно" }
                )
                AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
                snapshot
            }.onSuccess { snapshot ->
                updateGroupMeta(snapshot)
                _syncStatus.value = if (wipeLibrary) {
                    "Режим группы включён, библиотека очищена и синхронизирована"
                } else {
                    "Режим группы включён"
                }
            }.onFailure {
                _syncStatus.value = "Ошибка переключения режима: ${humanizeSyncError(it.message)}"
            }
        }
    }

    fun activateLocalMode(context: Context, wipeLibrary: Boolean) {
        viewModelScope.launch {
            runCatching {
                SyncScheduler.cancel(context)
                AppContainer.settingsRepository.clearSyncSettings()
                AppContainer.settingsRepository.setLibraryMode(LibraryMode.LOCAL)
                if (wipeLibrary) {
                    wipeLibraryData()
                }
                _groupSyncMeta.value = null
                _groupMetaLastRefreshEpochMs.value = null
            }.onSuccess {
                _syncStatus.value = if (wipeLibrary) {
                    "Локальный режим включён, библиотека очищена"
                } else {
                    "Локальный режим включён"
                }
            }.onFailure {
                _syncStatus.value = "Ошибка переключения режима: ${it.message}"
            }
        }
    }

    fun setStatus(message: String?) {
        _syncStatus.value = message
    }

    private suspend fun wipeLibraryData() {
        AppContainer.db.withTransaction {
            AppContainer.db.setlistItemDao().clearAll()
            AppContainer.db.songAudioDao().clearAll()
            AppContainer.db.setlistDao().clearAll()
            AppContainer.db.songDao().clearAll()
        }
    }

    fun setBackgroundSync(context: Context, enabled: Boolean, everyMinutes: Int) {
        viewModelScope.launch {
            AppContainer.settingsRepository.setBackgroundSync(enabled, everyMinutes)
            if (enabled) {
                SyncScheduler.schedule(context, everyMinutes)
                _syncStatus.value = "Фоновая синхронизация включена (${everyMinutes.coerceAtLeast(15)} мин)"
            } else {
                SyncScheduler.cancel(context)
                _syncStatus.value = "Фоновая синхронизация выключена"
            }
        }
    }

    fun exportBackupJson(memberName: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                AppContainer.syncRepository.exportSnapshotJson(memberName.ifBlank { "Неизвестно" })
            }.onSuccess {
                onReady(it)
                _syncStatus.value = "Резервная копия JSON подготовлена"
            }.onFailure {
                _syncStatus.value = "Ошибка экспорта: ${it.message}"
            }
        }
    }

    fun importBackupJson(raw: String) {
        viewModelScope.launch {
            runCatching {
                AppContainer.syncRepository.importSnapshotJson(raw)
            }.onSuccess {
                _syncStatus.value = "Импорт JSON успешно завершен"
            }.onFailure {
                _syncStatus.value = "Ошибка импорта: ${it.message}"
            }
        }
    }

    private fun updateGroupMeta(snapshot: SyncSnapshotDto) {
        val who = snapshot.lastPushedBy.ifBlank { snapshot.pushedBy }
        updateGroupMeta(
            SyncMetaDto(
                lastPushedBy = who,
                serverUpdatedAt = snapshot.serverUpdatedAt,
                members = snapshot.members
            )
        )
    }

    private fun updateGroupMeta(meta: SyncMetaDto) {
        val who = meta.lastPushedBy.ifBlank { "неизвестно" }
        val serverUpdatedAtText = formatEpochSeconds(meta.serverUpdatedAt)
        val members = meta.members.map {
            GroupMemberUi(
                name = it.name,
                lastSeenAtText = formatEpochSeconds(it.lastSeenAt)
            )
        }
        _groupSyncMeta.value = GroupSyncMetaUi(
            lastPushedBy = who,
            serverUpdatedAtText = serverUpdatedAtText,
            members = members
        )
        _groupMetaLastRefreshEpochMs.value = System.currentTimeMillis()
    }

    private fun formatSyncMeta(snapshot: SyncSnapshotDto): String {
        val who = snapshot.lastPushedBy.ifBlank { snapshot.pushedBy }
        return formatSyncMeta(
            SyncMetaDto(
                lastPushedBy = who,
                serverUpdatedAt = snapshot.serverUpdatedAt,
                members = snapshot.members
            )
        )
    }

    private fun formatSyncMeta(meta: SyncMetaDto): String {
        val who = meta.lastPushedBy.ifBlank { "неизвестно" }
        val membersCount = meta.members.size
        val ts = formatEpochSeconds(meta.serverUpdatedAt)
        return "Последний push: $who, участников: $membersCount, обновлено: $ts"
    }

    private fun formatEpochSeconds(epochSeconds: Long): String {
        if (epochSeconds <= 0) return "—"
        return runCatching {
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }.getOrElse { "$epochSeconds" }
    }

    private fun humanizeSyncError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        return when {
            msg.contains("401", ignoreCase = true) || msg.contains("invalid bearer token", ignoreCase = true) ->
                "Токен в QR недействителен для сервера. Пересоздай QR на телефоне с актуальным токеном и пересканируй."
            msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                "Нет соединения с сервером. Проверь интернет и адрес сервера."
            msg.isBlank() -> "Неизвестная ошибка"
            else -> msg
        }
    }

    private fun normalizeGroupCode(raw: String): String {
        return raw
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9_.-]"), "")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun isValidGroupCode(groupCode: String): Boolean {
        return Regex("^[a-z0-9][a-z0-9_.-]{1,63}$").matches(groupCode)
    }
}
