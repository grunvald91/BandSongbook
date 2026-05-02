package com.fithealthzone.bandsongbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.ui.screens.ProfileScreen
import com.fithealthzone.bandsongbook.ui.screens.SetlistEditorScreen
import com.fithealthzone.bandsongbook.ui.screens.SetlistsScreen
import com.fithealthzone.bandsongbook.ui.screens.SettingsScreen
import com.fithealthzone.bandsongbook.ui.screens.SongEditorScreen
import com.fithealthzone.bandsongbook.ui.screens.SongViewerScreen
import com.fithealthzone.bandsongbook.ui.screens.SongsListScreen
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.FrostedBackground

private data class NavItem(val dest: Dest, val icon: ImageVector)

@Composable
fun BandSongbookApp() {
    val context = LocalContext.current
    AppContainer.init(context)

    val navController = rememberNavController()
    val items = listOf(
        NavItem(Dest.Songs, Icons.Default.LibraryMusic),
        NavItem(Dest.Setlists, Icons.AutoMirrored.Filled.List),
        NavItem(Dest.Settings, Icons.Default.Settings),
        NavItem(Dest.Profile, Icons.Default.Person)
    )
    val (isSongFullscreen, setSongFullscreen) = remember { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route != Dest.SongViewer.route) {
            AppContainer.songViewerFullscreen = false
            setSongFullscreen(false)
        }
    }

    FrostedBackground {
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = AppColors.BgCard,
                        contentColor = AppColors.TextWhite,
                        actionContentColor = AppColors.PrimaryLight,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            bottomBar = {
                if (!isSongFullscreen) {
                    CompactStageBottomBar(
                        items = items,
                        isSelected = { d -> currentDestination?.hierarchy?.any { it.route == d.route } == true },
                        onClick = { d ->
                            navController.navigate(d.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Dest.Songs.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Dest.Songs.route) {
                    SongsListScreen(
                        onOpenSong = {
                            AppContainer.clearSetlistSongBrowseContext()
                            navController.navigate(Dest.SongViewer.create(it))
                        },
                        onCreateSong = { navController.navigate(Dest.SongEditor.create()) }
                    )
                }
                composable(Dest.Setlists.route) {
                    SetlistsScreen(onOpenSetlist = { navController.navigate(Dest.SetlistEditor.create(it)) })
                }
                composable(Dest.Settings.route) { SettingsScreen() }
                composable(Dest.Profile.route) {
                    ProfileScreen(onOpenSettings = { navController.navigate(Dest.Settings.route) })
                }

                composable(
                    route = Dest.SongEditor.route,
                    arguments = listOf(navArgument("songId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    })
                ) { backStack ->
                    SongEditorScreen(songId = backStack.arguments?.getString("songId")?.takeIf { it.isNotBlank() }) {
                        navController.popBackStack()
                    }
                }
                composable(
                    route = Dest.SongViewer.route,
                    arguments = listOf(navArgument("songId") { type = NavType.StringType })
                ) { backStack ->
                    val songId = backStack.arguments?.getString("songId") ?: return@composable
                    SongViewerScreen(
                        songId = songId,
                        initialFullscreen = AppContainer.songViewerFullscreen,
                        onEdit = { navController.navigate(Dest.SongEditor.create(songId)) },
                        onNavigateToSong = { nextSongId ->
                            navController.navigate(Dest.SongViewer.create(nextSongId)) {
                                popUpTo(Dest.SongViewer.create(songId)) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onFullscreenChanged = { fullscreen ->
                            AppContainer.songViewerFullscreen = fullscreen
                            setSongFullscreen(fullscreen)
                        }
                    )
                }
                composable(
                    route = Dest.SetlistEditor.route,
                    arguments = listOf(navArgument("setlistId") { type = NavType.StringType })
                ) { backStack ->
                    val setlistId = backStack.arguments?.getString("setlistId") ?: return@composable
                    SetlistEditorScreen(
                        setlistId = setlistId,
                        onOpenSong = { navController.navigate(Dest.SongViewer.create(it)) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CompactStageBottomBar(
    items: List<NavItem>,
    isSelected: (Dest) -> Boolean,
    onClick: (Dest) -> Unit
) {
    val barShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(barShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        AppColors.BgSurface.copy(alpha = 0.94f),
                        AppColors.BgCard.copy(alpha = 0.94f)
                    )
                )
            )
            .border(1.dp, AppColors.BorderGlass, barShape)
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = isSelected(item.dest)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(CircleShape)
                        .clickable { onClick(item.dest) }
                        .background(if (selected) AppColors.Primary.copy(alpha = 0.2f) else Color.Transparent)
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (selected) AppColors.PrimaryLight else AppColors.TextDim,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
