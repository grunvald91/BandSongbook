package com.fithealthzone.bandsongbook.ui.screens

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRefreshPlayerRegressionTest {

    @Test
    fun `setlists screen exposes pull to refresh`() {
        val screen = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SetlistsScreen.kt").readText()
        val viewModel = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/viewmodel/SetlistsViewModel.kt").readText()

        assertTrue(screen.contains("rememberPullRefreshState"))
        assertTrue(screen.contains(".pullRefresh(pullRefreshState)"))
        assertTrue(screen.contains("PullRefreshIndicator("))
        assertTrue(viewModel.contains("fun refreshFromGroup()"))
    }

    @Test
    fun `global bottom navigation stays above three button system navigation`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/BandSongbookApp.kt").readText()

        assertTrue(
            "The custom bottom bar must consume the navigation-bar inset itself.",
            source.contains(".navigationBarsPadding()")
        )
    }

    @Test
    fun `root lists do not reserve a decorative gap above bottom navigation`() {
        val app = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/BandSongbookApp.kt").readText()
        val songs = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongsListScreen.kt").readText()
        val setlists = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SetlistsScreen.kt").readText()

        assertFalse(app.contains(".padding(horizontal = 14.dp, vertical = 10.dp)"))
        assertTrue(app.contains(".padding(start = 14.dp, end = 14.dp, bottom = 6.dp)"))
        assertFalse(songs.contains("PaddingValues(bottom = 96.dp)"))
        assertFalse(setlists.contains("PaddingValues(bottom = 96.dp)"))
        assertTrue(songs.contains("PaddingValues(bottom = 72.dp)"))
        assertTrue(setlists.contains("PaddingValues(bottom = 72.dp)"))
    }

    @Test
    fun `setlist player builds queue only from user selected tracks`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SetlistEditorScreen.kt").readText()

        assertTrue(source.contains("var excludedTrackIds by remember(setlistId)"))
        assertTrue(source.contains("val selectedTracks = orderedTracks.filterNot"))
        assertTrue(source.contains("val queue = selectedTracks.mapNotNull"))
        assertTrue(source.contains("selectedTrackIds = selectedTracks.map { it.mediaId }.toSet()"))
        assertTrue(source.contains("onTrackSelectionChange"))
        assertTrue(source.contains("Выбрано: $" + "{selectedTrackIds.size}/$" + "{orderedTracks.size}"))
        assertTrue(source.contains("playlistBuildJob?.cancel()"))
        assertTrue(source.contains("coroutineContext.ensureActive()"))
        assertTrue(source.contains("LaunchedEffect(player, selectedQueueIds)"))
        assertTrue(source.contains("loadedQueueIds != selectedQueueIds"))
    }

    @Test
    fun `bottom navigation is hidden only on the fullscreen viewer and always opens a root screen`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/BandSongbookApp.kt").readText()

        assertTrue(source.contains("val hideBottomBar = currentDestination?.route == Dest.SongViewer.route && isSongFullscreen"))
        assertTrue(source.contains("if (!hideBottomBar)"))
        assertFalse("Top-level buttons must not restore a previously nested editor/viewer.", source.contains("restoreState = true"))
        assertTrue(source.contains("inclusive = true"))
    }

    @Test
    fun `release build uses durable external signing credentials and a new version`() {
        val source = Path.of("build.gradle.kts").readText()

        assertTrue(source.contains("BandSongbook-release.properties"))
        assertTrue(source.contains("signingConfigs"))
        assertTrue(source.contains("signingConfig = signingConfigs.getByName(\"release\")"))
        assertTrue(source.contains("versionCode = 15"))
        assertTrue(source.contains("versionName = \"3.3\""))
    }

    @Test
    fun `fullscreen song does not show library or setlists shortcuts`() {
        val viewer = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongViewerScreen.kt").readText()

        assertFalse(viewer.contains("onOpenLibrary: () -> Unit"))
        assertFalse(viewer.contains("onOpenSetlists: () -> Unit"))
        assertFalse(viewer.contains("contentDescription = \"Открыть библиотеку\""))
        assertFalse(viewer.contains("contentDescription = \"Открыть сетлисты\""))
    }

    @Test
    fun `song media panel starts collapsed for every browsed song`() {
        val source = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SongViewerScreen.kt").readText()

        assertTrue(
            "A newly browsed song must not inherit the previous song's expanded media panel.",
            source.contains("var controlsExpanded by remember(songId) { mutableStateOf(false) }")
        )
        assertTrue(
            "The panel animation must also restart from its collapsed anchor for a new song.",
            source.contains("remember(songId) { Animatable(collapsedControlsPx) }")
        )
    }
}
