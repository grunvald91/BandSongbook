package com.fithealthzone.bandsongbook.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String? = null,
    val originalKey: String = "C",
    val currentTranspose: Int = 0,
    val preferFlats: Boolean = false,
    @ColumnInfo(defaultValue = "1.0") val autoScrollSpeed: Float = 1.0f,
    val bpm: Int? = null,
    val capo: Int? = null,
    val lyricsWithChords: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "song_audio",
    foreignKeys = [ForeignKey(
        entity = SongEntity::class,
        parentColumns = ["id"],
        childColumns = ["songId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("songId")]
)
data class SongAudioEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
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
    val addedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val eventDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(
    tableName = "setlist_items",
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("setlistId"), Index("songId")]
)
data class SetlistItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val setlistId: String,
    val songId: String,
    val orderIndex: Int,
    val transposeOverride: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
