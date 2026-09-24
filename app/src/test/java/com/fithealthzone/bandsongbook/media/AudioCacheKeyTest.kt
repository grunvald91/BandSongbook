package com.fithealthzone.bandsongbook.media

import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCacheKeyTest {
    @Test
    fun `content hash keeps cache stable when signed url changes`() {
        val first = audio(contentHash = "abc", remoteUrl = "https://host/file?sig=old")
        val refreshed = first.copy(remoteUrl = "https://host/file?sig=new")

        assertEquals(AudioCacheKey.forAttachment(first), AudioCacheKey.forAttachment(refreshed))
        assertEquals("song-audio:sha256:abc", AudioCacheKey.forAttachment(first))
    }

    @Test
    fun `object key and attachment id provide stable fallbacks`() {
        assertEquals("song-audio:object:groups/g/audio/file", AudioCacheKey.forAttachment(audio(objectKey = "groups/g/audio/file")))
        assertEquals("song-audio:id:audio-id", AudioCacheKey.forAttachment(audio()))
    }

    private fun audio(
        contentHash: String? = null,
        objectKey: String? = null,
        remoteUrl: String? = null
    ) = SongAudioEntity(
        id = "audio-id",
        songId = "song-id",
        title = "Минус",
        uri = "",
        remoteUrl = remoteUrl,
        objectKey = objectKey,
        contentHash = contentHash
    )
}