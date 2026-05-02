package com.fithealthzone.bandsongbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.ui.viewmodel.SongsViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongsListScreen(onOpenSong: (String) -> Unit, onCreateSong: () -> Unit) {
    val vm: SongsViewModel = viewModel()
    val songs by vm.songs.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()

    var byName by remember { mutableStateOf(true) }
    var pendingDeleteSongId by remember { mutableStateOf<String?>(null) }

    val sortedSongs = remember(songs, byName) {
        if (byName) songs.sortedBy { it.title.lowercase() } else songs.sortedByDescending { it.updatedAt }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { vm.refreshFromGroup() }
    )

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateSong,
                containerColor = AppColors.Primary,
                contentColor = if (AppColors.isDark) AppColors.BgDeep else Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить песню")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                item {
                    StitchSongsHeader(
                        songsCount = sortedSongs.size,
                        syncStatus = syncStatus
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryIconToggle(
                                icon = Icons.Default.SortByAlpha,
                                contentDescription = "Сортировать песни по названию",
                                selected = byName,
                                onClick = { byName = true }
                            )
                            LibraryIconToggle(
                                icon = Icons.Default.AccessTime,
                                contentDescription = "Показывать новые песни сверху",
                                selected = !byName,
                                onClick = { byName = false }
                            )
                        }
                    }
                }

                if (sortedSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.BgCard, RoundedCornerShape(20.dp))
                                .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = AppColors.TextDim,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Пока нет песен",
                                    color = AppColors.TextMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нажми +, чтобы добавить первую",
                                    color = AppColors.TextDim,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                        val featured = index == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (featured) AppColors.BgCardHover else AppColors.BgCard,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(20.dp))
                                .clickable { onOpenSong(song.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (featured) AppColors.Primary.copy(alpha = 0.2f) else AppColors.BgSurface,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "%02d".format(index + 1),
                                    color = if (featured) AppColors.PrimaryLight else AppColors.TextDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = AppColors.TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist ?: "Без исполнителя",
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = song.originalKey,
                                    color = AppColors.PrimaryLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = song.bpm?.let { "$it BPM" } ?: "— BPM",
                                    color = AppColors.TextDim,
                                    fontSize = 10.sp
                                )
                            }

                            LibraryActionButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Удалить песню ${song.title}",
                                onClick = { pendingDeleteSongId = song.id }
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = AppColors.BgCard,
                contentColor = AppColors.PrimaryLight
            )

            val songToDelete = songs.firstOrNull { it.id == pendingDeleteSongId }
            if (songToDelete != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteSongId = null },
                    title = { Text("Удалить песню") },
                    text = { Text("Удалить «${songToDelete.title}»? Аудио и ссылки в сетлистах тоже будут скрыты после синхронизации.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.delete(songToDelete)
                            pendingDeleteSongId = null
                        }) {
                            Text("Удалить", color = AppColors.Error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteSongId = null }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StitchSongsHeader(songsCount: Int, syncStatus: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BgCard, RoundedCornerShape(24.dp))
                .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("БИБЛИОТЕКА", color = AppColors.TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("$songsCount треков", color = AppColors.TextMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AppColors.BgSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = "Индикатор библиотеки", tint = AppColors.PrimaryLight)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BgCard, RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = "Статус синхронизации", tint = AppColors.TextDim, modifier = Modifier.size(18.dp))
            Text(
                text = syncStatus ?: "Потяни вниз, чтобы синхронизировать библиотеку",
                color = if (syncStatus?.startsWith("Ошибка") == true) AppColors.Error else AppColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        active = selected,
        tint = if (selected) AppColors.PrimaryLight else AppColors.TextMuted,
        backgroundColor = AppColors.BgSurface,
        borderColor = AppColors.BorderGlass,
        onClick = onClick
    )
}

@Composable
private fun LibraryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        tint = AppColors.Error,
        backgroundColor = AppColors.BgSurface,
        borderColor = AppColors.BorderGlass,
        onClick = onClick
    )
}
