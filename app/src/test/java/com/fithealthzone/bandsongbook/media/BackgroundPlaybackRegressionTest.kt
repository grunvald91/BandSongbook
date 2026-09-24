package com.fithealthzone.bandsongbook.media

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPlaybackRegressionTest {

    @Test
    fun `manifest declares media playback foreground service`() {
        val manifest = Path.of("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertTrue(manifest.contains("androidx.media3.session.MediaSessionService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertTrue(
            Regex("""<service\s+android:name="\.media\.PlaybackService"[\s\S]*?android:exported="false"""")
                .containsMatchIn(manifest)
        )
    }

    @Test
    fun `playback service owns the player and media session`() {
        val servicePath = Path.of("src/main/java/com/fithealthzone/bandsongbook/media/PlaybackService.kt")
        assertTrue(servicePath.toFile().exists())
        val service = servicePath.readText()
        assertTrue(service.contains("class PlaybackService : MediaSessionService()"))
        assertTrue(service.contains("MediaSession.Builder"))
        assertTrue(service.contains("AudioPlaybackCache.buildPlayer(this)"))
    }

    @Test
    fun `song and setlist screens connect to service instead of releasing players`() {
        val song = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongViewerScreen.kt").readText()
        val setlist = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SetlistEditorScreen.kt").readText()
        assertTrue(song.contains("rememberPlaybackController()"))
        assertTrue(setlist.contains("rememberPlaybackController()"))
        assertFalse(song.contains("AudioPlaybackCache.buildPlayer(context)"))
        assertFalse(setlist.contains("AudioPlaybackCache.buildPlayer(context)"))
        assertFalse(song.contains("player.release()"))
        assertFalse(setlist.contains("player.release()"))
        assertTrue(setlist.contains("Player.REPEAT_MODE_ONE"))
        assertTrue(setlist.contains("Player.REPEAT_MODE_ALL"))
        assertTrue(setlist.contains("onRepeatModeChanged"))
        assertTrue(setlist.contains("PlaybackMediaId.forSetlistItem(setlistItemId, track.id)"))
    }

    @Test
    fun `structured setlist ids expose the same audio id to the viewer`() {
        val mediaId = PlaybackMediaId.forSetlistItem("item-1", "audio-1")
        assertTrue(mediaId != "audio-1")
        assertTrue(PlaybackMediaId.audioId(mediaId) == "audio-1")
        assertTrue(PlaybackMediaId.audioId("audio-2") == "audio-2")
    }
}
