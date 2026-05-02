package com.fithealthzone.bandsongbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SongEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(SongEditorState())
    val state: StateFlow<SongEditorState> = _state

    fun load(songId: String?) {
        if (songId == null) return
        viewModelScope.launch {
            AppContainer.songRepository.getSong(songId)?.let { s ->
                _state.value = SongEditorState(
                    id = s.id,
                    title = s.title,
                    artist = s.artist.orEmpty(),
                    key = s.originalKey,
                    preferFlats = s.preferFlats,
                    lyrics = s.lyricsWithChords,
                    notes = s.notes.orEmpty(),
                    bpm = s.bpm?.toString().orEmpty(),
                    capo = s.capo?.toString().orEmpty()
                )
            }
        }
    }

    fun update(state: SongEditorState) {
        _state.value = state
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank() || s.lyrics.isBlank()) return
        viewModelScope.launch {
            val existing = s.id?.let { AppContainer.songRepository.getSong(it) }
            val memberName = runCatching {
                AppContainer.settingsRepository.getSyncSettingsSnapshot().memberName.trim().ifBlank { null }
            }.getOrNull()

            AppContainer.songRepository.saveSong(
                SongEntity(
                    id = s.id ?: java.util.UUID.randomUUID().toString(),
                    title = s.title.trim(),
                    artist = s.artist.ifBlank { null },
                    originalKey = s.key.ifBlank { "C" },
                    currentTranspose = existing?.currentTranspose ?: 0,
                    preferFlats = s.preferFlats,
                    lyricsWithChords = s.lyrics,
                    notes = s.notes.ifBlank { null },
                    bpm = s.bpm.toIntOrNull(),
                    capo = s.capo.toIntOrNull(),
                    autoScrollSpeed = existing?.autoScrollSpeed ?: 1.0f,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    createdBy = existing?.createdBy ?: memberName
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val s = _state.value
        val songId = s.id ?: return
        viewModelScope.launch {
            AppContainer.songRepository.getSong(songId)?.let { song ->
                AppContainer.songRepository.deleteSong(song)
            }
            onDone()
        }
    }
}

data class SongEditorState(
    val id: String? = null,
    val title: String = "",
    val artist: String = "",
    val key: String = "C",
    val preferFlats: Boolean = false,
    val lyrics: String = "",
    val notes: String = "",
    val bpm: String = "",
    val capo: String = ""
)
