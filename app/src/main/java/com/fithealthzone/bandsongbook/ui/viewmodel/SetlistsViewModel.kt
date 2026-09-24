package com.fithealthzone.bandsongbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import com.fithealthzone.bandsongbook.data.settings.LibraryMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistsViewModel : ViewModel() {
    val setlists: StateFlow<List<SetlistEntity>> = AppContainer.setlistRepository.observeSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private var refreshJob: Job? = null

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            AppContainer.setlistRepository.saveSetlist(SetlistEntity(name = name.trim()))
        }
    }

    fun delete(setlist: SetlistEntity) {
        viewModelScope.launch {
            AppContainer.setlistRepository.deleteSetlist(setlist)
        }
    }

    fun refreshFromGroup() {
        if (refreshJob?.isActive == true) return
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            try {
                val mode = AppContainer.settingsRepository.getLibraryModeSnapshot()
                if (mode != LibraryMode.GROUP) {
                    _syncStatus.value = "Сейчас включён локальный режим"
                    return@launch
                }
                val sync = AppContainer.settingsRepository.getSyncSettingsSnapshot()
                val baseUrl = sync.baseUrl.trim().trimEnd('/')
                val groupCode = normalizeGroupCode(sync.groupCode)
                val memberName = sync.memberName.trim().ifBlank { "Неизвестно" }
                val authToken = sync.authToken.trim()

                if (baseUrl.isBlank() || groupCode.isBlank()) {
                    _syncStatus.value = "Сначала настрой группу во вкладке Профиль"
                    return@launch
                }

                AppContainer.syncRepository.roundTrip(baseUrl, groupCode, authToken, memberName)
                AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
                _syncStatus.value = "Сетлисты обновлены из группы"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _syncStatus.value = "Ошибка синхронизации. Проверь подключение и настройки группы"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun normalizeGroupCode(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9_.-]"), "")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}
