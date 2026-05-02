package com.fithealthzone.bandsongbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistsViewModel : ViewModel() {
    val setlists: StateFlow<List<SetlistEntity>> = AppContainer.setlistRepository.observeSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}
