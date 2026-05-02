package com.fithealthzone.bandsongbook.data.sync

import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongEntity
import kotlinx.serialization.Serializable

@Serializable
data class SongDto(
    val id: String,
    val title: String,
    val artist: String? = null,
    val originalKey: String,
    val currentTranspose: Int,
    val autoScrollSpeed: Float = 1.0f,
    val bpm: Int? = null,
    val capo: Int? = null,
    val lyricsWithChords: String,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String? = null,
    val deletedAt: Long? = null
)

@Serializable
data class SongAudioDto(
    val id: String,
    val songId: String,
    val title: String,
    val uri: String,
    val remoteUrl: String? = null,
    val objectKey: String? = null,
    val contentHash: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val uploadedBy: String? = null,
    val addedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class SetlistDto(
    val id: String,
    val name: String,
    val eventDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class SetlistItemDto(
    val id: String,
    val setlistId: String,
    val songId: String,
    val orderIndex: Int,
    val transposeOverride: Int? = null,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

@Serializable
data class GroupMemberDto(
    val name: String,
    val lastSeenAt: Long
)

@Serializable
data class SyncSnapshotDto(
    val songs: List<SongDto> = emptyList(),
    val audio: List<SongAudioDto> = emptyList(),
    val setlists: List<SetlistDto> = emptyList(),
    val setlistItems: List<SetlistItemDto> = emptyList(),
    val pushedBy: String = "",
    val lastPushedBy: String = "",
    val serverUpdatedAt: Long = 0,
    val members: List<GroupMemberDto> = emptyList()
)

@Serializable
data class SyncMetaDto(
    val lastPushedBy: String = "",
    val serverUpdatedAt: Long = 0,
    val members: List<GroupMemberDto> = emptyList()
)

@Serializable
data class SyncPullRequest(
    val groupCode: String
)

@Serializable
data class SyncPushRequest(
    val groupCode: String,
    val snapshot: SyncSnapshotDto
)

@Serializable
data class AudioExistsRequest(
    val groupCode: String,
    val contentHash: String
)

@Serializable
data class AudioExistsResponse(
    val exists: Boolean,
    val objectKey: String? = null,
    val remoteUrl: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null
)

@Serializable
data class AudioUploadUrlRequest(
    val groupCode: String,
    val contentHash: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val fileName: String? = null
)

@Serializable
data class AudioUploadUrlResponse(
    val exists: Boolean,
    val objectKey: String,
    val uploadUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: Long? = null,
    val remoteUrl: String? = null
)

@Serializable
data class AudioConfirmRequest(
    val groupCode: String,
    val objectKey: String,
    val contentHash: String,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val title: String? = null,
    val uploadedBy: String? = null
)

@Serializable
data class AudioConfirmResponse(
    val ok: Boolean,
    val objectKey: String,
    val remoteUrl: String? = null
)

@Serializable
data class AudioDownloadUrlRequest(
    val groupCode: String,
    val objectKey: String
)

@Serializable
data class AudioDownloadUrlResponse(
    val objectKey: String,
    val downloadUrl: String,
    val expiresAt: Long? = null
)

fun SongEntity.toDto() = SongDto(
    id = id,
    title = title,
    artist = artist,
    originalKey = originalKey,
    currentTranspose = currentTranspose,
    autoScrollSpeed = autoScrollSpeed,
    bpm = bpm,
    capo = capo,
    lyricsWithChords = lyricsWithChords,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    deletedAt = deletedAt
)

fun SongAudioEntity.toDto() = SongAudioDto(
    id = id,
    songId = songId,
    title = title,
    uri = uri,
    remoteUrl = remoteUrl,
    objectKey = objectKey,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    durationMs = durationMs,
    uploadedBy = uploadedBy,
    addedAt = addedAt,
    deletedAt = deletedAt
)
fun SetlistEntity.toDto() = SetlistDto(id, name, eventDate, notes, createdAt, updatedAt, deletedAt)
fun SetlistItemEntity.toDto() = SetlistItemDto(id, setlistId, songId, orderIndex, transposeOverride, updatedAt, deletedAt)

fun SongDto.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    originalKey = originalKey,
    currentTranspose = currentTranspose,
    autoScrollSpeed = autoScrollSpeed,
    bpm = bpm,
    capo = capo,
    lyricsWithChords = lyricsWithChords,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    deletedAt = deletedAt
)

fun SongAudioDto.toEntity() = SongAudioEntity(
    id = id,
    songId = songId,
    title = title,
    uri = uri,
    remoteUrl = remoteUrl,
    objectKey = objectKey,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    durationMs = durationMs,
    uploadedBy = uploadedBy,
    addedAt = addedAt,
    deletedAt = deletedAt
)
fun SetlistDto.toEntity() = SetlistEntity(id, name, eventDate, notes, createdAt, updatedAt, deletedAt)
fun SetlistItemDto.toEntity() = SetlistItemEntity(id, setlistId, songId, orderIndex, transposeOverride, updatedAt, deletedAt)
