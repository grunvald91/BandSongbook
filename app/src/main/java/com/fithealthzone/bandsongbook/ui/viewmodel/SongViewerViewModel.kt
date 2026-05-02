package com.fithealthzone.bandsongbook.ui.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongEntity
import com.fithealthzone.bandsongbook.media.AudioPlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

class SongViewerViewModel(private val songId: String) : ViewModel() {
    companion object {
        private const val MAX_UPLOAD_BYTES = 64L * 1024L * 1024L
    }

    sealed interface AudioUploadStatus {
        data object Idle : AudioUploadStatus
        data class InProgress(val title: String) : AudioUploadStatus
        data class Success(val title: String) : AudioUploadStatus
        data class Failed(val title: String, val reason: String) : AudioUploadStatus
    }

    private val _uploadStatus = MutableStateFlow<AudioUploadStatus>(AudioUploadStatus.Idle)
    val uploadStatus: StateFlow<AudioUploadStatus> = _uploadStatus.asStateFlow()

    fun acknowledgeUploadStatus() {
        _uploadStatus.value = AudioUploadStatus.Idle
    }

    val song: StateFlow<SongEntity?> = AppContainer.songRepository.observeSong(songId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val audio: StateFlow<List<SongAudioEntity>> = AppContainer.songRepository.observeAudio(songId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autoSpeed: StateFlow<Float> = song
        .map { it?.autoScrollSpeed ?: 1.0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    private val _transpose = MutableStateFlow(0)
    val transpose: StateFlow<Int> = _transpose

    private val _preferFlats = MutableStateFlow(false)
    val preferFlats: StateFlow<Boolean> = _preferFlats

    fun transposeBy(delta: Int) {
        val next = (_transpose.value + delta).coerceIn(-11, 11)
        _transpose.value = next
        viewModelScope.launch {
            AppContainer.songRepository.setSongTranspose(songId, next)
        }
    }

    fun setPreferFlats(preferFlats: Boolean) {
        _preferFlats.value = preferFlats
        viewModelScope.launch {
            AppContainer.songRepository.setSongPreferFlats(songId, preferFlats)
        }
    }

    fun persistSpeed(speed: Float) {
        viewModelScope.launch {
            AppContainer.songRepository.setSongAutoScrollSpeed(songId, speed)
        }
    }

    init {
        viewModelScope.launch {
            song.collect { currentSong ->
                _preferFlats.value = currentSong?.preferFlats ?: false
                _transpose.value = currentSong?.currentTranspose?.coerceIn(-11, 11) ?: 0
            }
        }
    }

    fun addAudioFromUri(
        context: Context,
        songId: String,
        title: String,
        uri: Uri,
        uploadedBy: String?
    ) {
        viewModelScope.launch {
            _uploadStatus.value = AudioUploadStatus.InProgress(title)

            val prepared = withContext(Dispatchers.IO) {
                prepareAudioAttachment(context, uri)
            }

            val syncSettings = runCatching {
                AppContainer.settingsRepository.getSyncSettingsSnapshot()
            }.getOrNull()

            val memberName = uploadedBy?.takeIf { it.isNotBlank() }
                ?: syncSettings?.memberName?.takeIf { it.isNotBlank() }

            val shouldUpload = prepared.contentHash != null &&
                prepared.fileBytes != null &&
                syncSettings != null &&
                syncSettings.baseUrl.isNotBlank() &&
                syncSettings.groupCode.isNotBlank()

            val uploadResult = if (shouldUpload) {
                runCatching {
                    AppContainer.syncRepository.resolveRemoteAudio(
                        baseUrl = syncSettings!!.baseUrl,
                        groupCode = syncSettings.groupCode,
                        authToken = syncSettings.authToken,
                        memberName = memberName ?: "Неизвестно",
                        title = title,
                        contentHash = prepared.contentHash!!,
                        mimeType = prepared.mimeType,
                        sizeBytes = prepared.sizeBytes,
                        durationMs = prepared.durationMs,
                        fileName = prepared.fileName,
                        fileBytes = prepared.fileBytes!!
                    )
                }
            } else null

            val remoteRef = uploadResult?.getOrNull()

            AppContainer.songRepository.addAudio(
                SongAudioEntity(
                    songId = songId,
                    title = title,
                    uri = uri.toString(),
                    remoteUrl = remoteRef?.remoteUrl,
                    objectKey = remoteRef?.objectKey,
                    contentHash = prepared.contentHash,
                    sizeBytes = prepared.sizeBytes,
                    mimeType = prepared.mimeType,
                    durationMs = prepared.durationMs,
                    uploadedBy = memberName
                )
            )

            _uploadStatus.value = when {
                !shouldUpload && syncSettings != null &&
                    (syncSettings.baseUrl.isBlank() || syncSettings.groupCode.isBlank()) ->
                    AudioUploadStatus.Failed(
                        title = title,
                        reason = "Синхронизация не настроена — аудио пока только локально"
                    )
                uploadResult == null -> AudioUploadStatus.Success(title)
                uploadResult.isSuccess -> AudioUploadStatus.Success(title)
                else -> AudioUploadStatus.Failed(
                    title = title,
                    reason = uploadResult.exceptionOrNull()?.localizedMessage
                        ?: "Не удалось загрузить на сервер"
                )
            }
        }
    }

    fun addAudioFromUrl(songId: String, title: String, url: String, uploadedBy: String?) {
        val normalizedUrl = url.trim()
        val parsed = runCatching { Uri.parse(normalizedUrl) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        if (normalizedUrl.isBlank() || (scheme != "http" && scheme != "https")) return

        viewModelScope.launch {
            val memberName = uploadedBy?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    AppContainer.settingsRepository.getSyncSettingsSnapshot().memberName.trim().ifBlank { null }
                }.getOrNull()

            AppContainer.songRepository.addAudio(
                SongAudioEntity(
                    songId = songId,
                    title = title,
                    uri = "",
                    remoteUrl = normalizedUrl,
                    uploadedBy = memberName
                )
            )
        }
    }

    private data class PreparedAudio(
        val fileName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val durationMs: Long?,
        val contentHash: String?,
        val fileBytes: ByteArray?
    )

    private fun prepareAudioAttachment(context: Context, uri: Uri): PreparedAudio {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)

        var fileName: String? = null
        var sizeFromQuery: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idxName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val idxSize = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (idxName >= 0 && !cursor.isNull(idxName)) {
                        fileName = cursor.getString(idxName)
                    }
                    if (idxSize >= 0 && !cursor.isNull(idxSize)) {
                        sizeFromQuery = cursor.getLong(idxSize)
                    }
                }
            }
        }

        val durationMs = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()

        val fileBytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val targetLimit = (sizeFromQuery ?: MAX_UPLOAD_BYTES).coerceAtMost(MAX_UPLOAD_BYTES)
                readBytesWithLimit(input, targetLimit)
            }
        }.getOrNull()

        val sizeBytes = fileBytes?.size?.toLong() ?: sizeFromQuery

        val contentHash = fileBytes?.let { bytes ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(bytes)
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }

        return PreparedAudio(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            contentHash = contentHash,
            fileBytes = fileBytes
        )
    }

    suspend fun resolvePlaybackUri(context: Context, audio: SongAudioEntity): String? =
        AudioPlaybackResolver.resolve(context, audio)

    fun removeAudio(audio: SongAudioEntity) {
        viewModelScope.launch {
            AppContainer.songRepository.removeAudio(audio)
        }
    }

    private fun readBytesWithLimit(input: InputStream, limitBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limitBytes) {
                throw IllegalStateException("Audio file is too large (>${limitBytes} bytes)")
            }
            out.write(buffer, 0, read)
        }

        return out.toByteArray()
    }
}
