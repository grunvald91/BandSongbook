package com.fithealthzone.bandsongbook.data.sync

import com.fithealthzone.bandsongbook.data.local.SetlistEntity
import com.fithealthzone.bandsongbook.data.local.SetlistItemEntity
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.data.local.SongEntity

/**
 * Чистая логика слияния локальных и удалённых сущностей.
 *
 * Вынесено из [SyncRepository] в отдельный объект, чтобы:
 *  - не тащить в unit-тесты Room / контекст Android и
 *  - явно отделить детерминированную функцию «сводим два списка» от IO.
 *
 * Все методы — pure: на вход — immutable данные, на выход — новый список. Порядок в
 * выходном списке не определён (используется как источник для `upsertAll`).
 *
 * Политика конфликтов:
 *  - Last-Write-Wins по `max(updatedAt, deletedAt ?: MIN)` на уровне сущности.
 *  - НО личные настройки каждого пользователя НЕ разделяются группой:
 *    `SongEntity.currentTranspose`, `preferFlats`, `autoScrollSpeed` и
 *    `SetlistItemEntity.transposeOverride` при любом исходе LWW сохраняются в том
 *    значении, что уже было на этом устройстве. Гитарист с низкой гитарой не
 *    получит неожиданно чужое транспонирование.
 */
internal object SyncMerge {

    fun mergeSongs(
        local: Map<String, SongEntity>,
        incoming: List<SongEntity>
    ): List<SongEntity> {
        val out = local.toMutableMap()
        incoming.forEach { remote ->
            val current = out[remote.id]
            out[remote.id] = when {
                current == null -> remote
                songVersion(remote) >= songVersion(current) ->
                    remote.copy(
                        currentTranspose = current.currentTranspose,
                        preferFlats = current.preferFlats,
                        autoScrollSpeed = current.autoScrollSpeed
                    )
                else -> current
            }
        }
        return out.values.toList()
    }

    fun mergeSetlists(
        local: Map<String, SetlistEntity>,
        incoming: List<SetlistEntity>
    ): List<SetlistEntity> {
        val out = local.toMutableMap()
        incoming.forEach { remote ->
            val current = out[remote.id]
            out[remote.id] = if (current == null || setlistVersion(remote) >= setlistVersion(current)) {
                remote
            } else {
                current
            }
        }
        return out.values.toList()
    }

    fun mergeAudio(
        local: Map<String, SongAudioEntity>,
        incoming: List<SongAudioEntity>,
        allowedSongIds: Set<String>
    ): List<SongAudioEntity> {
        val out = local.toMutableMap()
        incoming.forEach { remote ->
            if (remote.songId !in allowedSongIds) return@forEach
            val current = out[remote.id]
            out[remote.id] = if (current == null || audioVersion(remote) >= audioVersion(current)) {
                remote
            } else {
                current
            }
        }

        // По contentHash выбираем «самую нагруженную метаданными» запись — её objectKey
        // и другие поля подтянем в дубли с таким же хешем, чтобы расшаренная ссылка
        // работала у всех участников.
        val candidateByHash = mutableMapOf<String, SongAudioEntity>()
        out.values.filter { it.songId in allowedSongIds }.forEach { entity ->
            val hash = entity.contentHash?.takeIf { it.isNotBlank() } ?: return@forEach
            val existing = candidateByHash[hash]
            if (existing == null || audioMetadataScore(entity) >= audioMetadataScore(existing)) {
                candidateByHash[hash] = entity
            }
        }

        return out.values
            .filter { it.songId in allowedSongIds }
            .map { entity ->
                val hash = entity.contentHash?.takeIf { it.isNotBlank() }
                val best = hash?.let { candidateByHash[it] }
                if (best == null || best.id == entity.id) {
                    entity
                } else {
                    entity.copy(
                        remoteUrl = entity.remoteUrl ?: best.remoteUrl,
                        objectKey = entity.objectKey ?: best.objectKey,
                        sizeBytes = entity.sizeBytes ?: best.sizeBytes,
                        mimeType = entity.mimeType ?: best.mimeType,
                        durationMs = entity.durationMs ?: best.durationMs,
                        uploadedBy = entity.uploadedBy ?: best.uploadedBy
                    )
                }
            }
    }

    fun mergeSetlistItems(
        local: Map<String, SetlistItemEntity>,
        incoming: List<SetlistItemEntity>,
        allowedSetlistIds: Set<String>,
        allowedSongIds: Set<String>
    ): List<SetlistItemEntity> {
        val out = local.toMutableMap()
        incoming.forEach { remote ->
            if (remote.setlistId !in allowedSetlistIds || remote.songId !in allowedSongIds) return@forEach
            val current = out[remote.id]
            out[remote.id] = when {
                current == null -> remote
                setlistItemVersion(remote) >= setlistItemVersion(current) ->
                    // transposeOverride в сетлистном айтеме — личное. Если сегодняшний
                    // басист хочет играть на полтона ниже, его настройка не должна
                    // слетать, когда другой участник просто переставит треки в списке.
                    remote.copy(transposeOverride = current.transposeOverride)
                else -> current
            }
        }
        return out.values.filter { it.setlistId in allowedSetlistIds && it.songId in allowedSongIds }
    }

    private fun songVersion(entity: SongEntity): Long =
        maxOf(entity.updatedAt, entity.deletedAt ?: Long.MIN_VALUE)

    private fun setlistVersion(entity: SetlistEntity): Long =
        maxOf(entity.updatedAt, entity.deletedAt ?: Long.MIN_VALUE)

    private fun audioVersion(entity: SongAudioEntity): Long =
        maxOf(entity.addedAt, entity.deletedAt ?: Long.MIN_VALUE)

    private fun setlistItemVersion(entity: SetlistItemEntity): Long =
        maxOf(entity.updatedAt, entity.deletedAt ?: Long.MIN_VALUE)

    private fun audioMetadataScore(entity: SongAudioEntity): Int {
        var score = 0
        if (!entity.objectKey.isNullOrBlank()) score += 8
        if (!entity.remoteUrl.isNullOrBlank()) score += 4
        if (!entity.contentHash.isNullOrBlank()) score += 2
        if (entity.sizeBytes != null) score += 1
        if (!entity.mimeType.isNullOrBlank()) score += 1
        if (entity.durationMs != null) score += 1
        return score
    }

    /**
     * Применяет внешние tombstone-записи к локальному списку песен.
     *
     * LWW: если `tombstone.deletedAt` строго больше текущей версии локальной записи
     * (`max(updatedAt, deletedAt ?: MIN)`), запись помечается удалённой с `deletedAt`
     * из tombstone. Если локально записи нет совсем — создаётся stub с пустыми полями
     * и проставленным `deletedAt`, чтобы tombstone дальше уезжал в следующий push и
     * другие клиенты тоже узнали про удаление.
     *
     * Личные настройки (`currentTranspose`, `preferFlats`, `autoScrollSpeed`)
     * остаются такими же, как были на этом устройстве: визуально это не важно
     * (строка скрыта `WHERE deletedAt IS NULL` в UI), но так чище.
     */
    fun applySongTombstones(
        songs: List<SongEntity>,
        tombstones: List<SyncTombstoneDto>
    ): List<SongEntity> {
        if (tombstones.isEmpty()) return songs
        // Если на одну запись пришло несколько tombstone'ов — берём с самым свежим deletedAt.
        val latest = tombstones
            .groupBy { it.id }
            .mapValues { (_, ts) -> ts.maxByOrNull { it.deletedAt }!! }
        val byId = songs.associateBy { it.id }.toMutableMap()
        latest.values.forEach { t ->
            val current = byId[t.id]
            if (current == null) {
                byId[t.id] = SongEntity(
                    id = t.id,
                    title = "",
                    artist = null,
                    originalKey = "C",
                    currentTranspose = 0,
                    preferFlats = false,
                    autoScrollSpeed = 1.0f,
                    bpm = null,
                    capo = null,
                    lyricsWithChords = "",
                    notes = null,
                    createdAt = t.deletedAt,
                    updatedAt = t.deletedAt,
                    createdBy = t.deletedBy,
                    deletedAt = t.deletedAt
                )
            } else if (t.deletedAt > songVersion(current)) {
                byId[t.id] = current.copy(deletedAt = t.deletedAt)
            }
        }
        return byId.values.toList()
    }

    fun applySetlistTombstones(
        setlists: List<SetlistEntity>,
        tombstones: List<SyncTombstoneDto>
    ): List<SetlistEntity> {
        if (tombstones.isEmpty()) return setlists
        val latest = tombstones
            .groupBy { it.id }
            .mapValues { (_, ts) -> ts.maxByOrNull { it.deletedAt }!! }
        val byId = setlists.associateBy { it.id }.toMutableMap()
        latest.values.forEach { t ->
            val current = byId[t.id]
            if (current == null) {
                byId[t.id] = SetlistEntity(
                    id = t.id,
                    name = "",
                    eventDate = null,
                    notes = null,
                    createdAt = t.deletedAt,
                    updatedAt = t.deletedAt,
                    deletedAt = t.deletedAt
                )
            } else if (t.deletedAt > setlistVersion(current)) {
                byId[t.id] = current.copy(deletedAt = t.deletedAt)
            }
        }
        return byId.values.toList()
    }
}
