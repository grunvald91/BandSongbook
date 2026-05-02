package com.fithealthzone.bandsongbook.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongEntity
import com.fithealthzone.bandsongbook.media.AudioPlaybackResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SetlistEditorUi(
    val title: String = "Сет-лист",
    val items: List<SetlistItemEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val audioBySongId: Map<String, List<SongAudioEntity>> = emptyMap()
)

class SetlistEditorViewModel(private val setlistId: String) : ViewModel() {
    private val _ui = MutableStateFlow(SetlistEditorUi())
    val ui: StateFlow<SetlistEditorUi> = _ui

    init {
        viewModelScope.launch {
            combine(
                AppContainer.setlistRepository.observeSetlist(setlistId),
                AppContainer.setlistRepository.observeItems(setlistId),
                AppContainer.setlistRepository.observeSongs(),
                AppContainer.db.songAudioDao().observeAll()
            ) { setlist, items, songs, allAudio ->
                val setSongIds = items.map { it.songId }.toSet()
                val audioBySong = allAudio
                    .asSequence()
                    .filter { it.songId in setSongIds }
                    .sortedBy { it.addedAt }
                    .groupBy { it.songId }

                SetlistEditorUi(
                    title = setlist?.name ?: "Сет-лист",
                    items = items,
                    songs = songs,
                    audioBySongId = audioBySong
                )
            }.collect {
                _ui.value = it
            }
        }
    }

    fun addSong(songId: String) {
        viewModelScope.launch {
            AppContainer.setlistRepository.addSong(setlistId, songId, ui.value.items.size)
        }
    }

    fun addSongs(songIds: List<String>) {
        viewModelScope.launch {
            AppContainer.setlistRepository.addSongs(setlistId, songIds)
        }
    }

    fun removeItem(item: SetlistItemEntity) {
        viewModelScope.launch { AppContainer.setlistRepository.removeItem(item) }
    }

    fun moveItem(itemId: String, direction: Int) {
        val current = ui.value.items
        val index = current.indexOfFirst { it.id == itemId }
        if (index == -1) return
        val newIndex = (index + direction).coerceIn(0, current.lastIndex)
        if (newIndex == index) return

        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(newIndex, item)

        viewModelScope.launch {
            AppContainer.setlistRepository.reorder(mutable)
        }
    }

    suspend fun resolvePlaybackUri(context: Context, audio: SongAudioEntity): String? =
        AudioPlaybackResolver.resolve(context, audio)
}

class SetlistEditorFactory(private val setlistId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SetlistEditorViewModel(setlistId) as T
    }
}
