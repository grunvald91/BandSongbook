package com.fithealthzone.bandsongbook.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.local.SongAudioEntity
import com.fithealthzone.bandsongbook.media.AudioPlaybackCache
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.KeyBadge
import com.fithealthzone.bandsongbook.ui.theme.PlayerDragHandle
import com.fithealthzone.bandsongbook.ui.theme.PlayerTransportButton
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.ui.theme.StageTextToggleChip
import com.fithealthzone.bandsongbook.ui.viewmodel.SetlistEditorFactory
import com.fithealthzone.bandsongbook.ui.viewmodel.SetlistEditorUi
import com.fithealthzone.bandsongbook.ui.viewmodel.SetlistEditorViewModel

private data class OrderedTrack(
    val track: SongAudioEntity,
    val songTitle: String,
    val remoteUri: String?,
    val localUri: String?
) {
    fun preferredUri(): String? = remoteUri ?: localUri

    fun sourceBadge(): TrackSourceBadge {
        return when {
            !remoteUri.isNullOrBlank() -> TrackSourceBadge("URL", Icons.Default.Link)
            !localUri.isNullOrBlank() -> TrackSourceBadge("Файл", Icons.Default.AttachFile)
            else -> TrackSourceBadge("Источник", Icons.Default.PlayArrow)
        }
    }
}

private data class TrackSourceBadge(
    val label: String,
    val icon: ImageVector
)

@Composable
fun SetlistEditorScreen(setlistId: String, onOpenSong: (String) -> Unit = {}) {
    val vm: SetlistEditorViewModel = viewModel(factory = SetlistEditorFactory(setlistId))
    val ui by vm.ui.collectAsState()

    var editMode by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(true) }
    var playerExpanded by remember { mutableStateOf(false) }
    var playerSheetHeight by remember { mutableStateOf(18.dp) }

    val selectedSongIds = remember(ui.items) { ui.items.map { it.songId }.toSet() }
    var pendingSelection by remember { mutableStateOf(selectedSongIds) }

    val orderedTracks = remember(ui.items, ui.songs, ui.audioBySongId) { buildOrderedTracks(ui) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { AudioPlaybackCache.buildPlayer(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrackId by remember { mutableStateOf<String?>(null) }
    var playbackPositionMs by remember { mutableStateOf(0L) }
    var playbackDurationMs by remember { mutableStateOf(0L) }
    var loopOne by remember { mutableStateOf(false) }
    var loopAll by remember { mutableStateOf(false) }
    var canSkipPrevious by remember { mutableStateOf(false) }
    var canSkipNext by remember { mutableStateOf(false) }
    val playerReservedSpace = if (showPlayer) playerSheetHeight else 0.dp

    fun applyRepeat() {
        player.repeatMode = when {
            loopOne -> Player.REPEAT_MODE_ONE
            loopAll -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun startPlaylist(fromTrackId: String? = null) {
        scope.launch {
            val queue = orderedTracks.mapNotNull { track ->
                val uri = vm.resolvePlaybackUri(context, track.track) ?: return@mapNotNull null
                MediaItem.Builder().setMediaId(track.track.id).setUri(uri).build()
            }
            if (queue.isEmpty()) return@launch
            val startIndex = if (fromTrackId == null) 0 else queue.indexOfFirst { it.mediaId == fromTrackId }.coerceAtLeast(0)
            player.setMediaItems(queue, startIndex, 0L)
            player.prepare()
            player.playWhenReady = true
        }
    }

    DisposableEffect(player) {
        fun syncPlayerNavigationState() {
            canSkipPrevious = player.hasPreviousMediaItem()
            canSkipNext = player.hasNextMediaItem()
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                syncPlayerNavigationState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackId = mediaItem?.mediaId
                playbackPositionMs = 0L
                playbackDurationMs = player.duration.coerceAtLeast(0L)
                syncPlayerNavigationState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackDurationMs = player.duration.coerceAtLeast(0L)
                syncPlayerNavigationState()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(loopOne, loopAll) { applyRepeat() }
    LaunchedEffect(selectedSongIds, editMode) { if (!editMode) pendingSelection = selectedSongIds }
    LaunchedEffect(currentTrackId, isPlaying) {
        while (currentTrackId != null) {
            playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
            playbackDurationMs = player.duration.takeIf { it > 0 } ?: 0L
            kotlinx.coroutines.delay(if (isPlaying) 250 else 500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = playerReservedSpace + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SetlistHeader(
                title = ui.title,
                songsCount = ui.items.size,
                tracksCount = orderedTracks.size,
                editMode = editMode,
                onEditClick = {
                    if (editMode) {
                        val toAdd = (pendingSelection - selectedSongIds).toList()
                        val toRemove = selectedSongIds - pendingSelection
                        if (toAdd.isNotEmpty()) vm.addSongs(toAdd)
                        if (toRemove.isNotEmpty()) {
                            ui.items.filter { it.songId in toRemove }.forEach { vm.removeItem(it) }
                        }
                    }
                    editMode = !editMode
                }
            )

            if (editMode) {
                Text("Выбор песен", color = AppColors.TextMuted, fontSize = 12.sp)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ui.songs, key = { it.id }) { song ->
                        val checked = song.id in pendingSelection
                        val selectionInteraction = remember(song.id) { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.BgCard, RoundedCornerShape(16.dp))
                                .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = selectionInteraction,
                                    indication = rememberRipple(
                                        bounded = true,
                                        color = AppColors.Primary.copy(alpha = 0.18f),
                                        radius = 160.dp
                                    )
                                ) {
                                    pendingSelection = if (checked) pendingSelection - song.id else pendingSelection + song.id
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CheckPill(checked = checked)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = AppColors.TextWhite, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist ?: "", color = AppColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

            } else {
                if (ui.items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.BgCard, RoundedCornerShape(16.dp))
                            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Сетлист пуст. Нажми “Редактировать”, чтобы добавить песни.",
                            color = AppColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(ui.items, key = { _, item -> item.id }) { index, item ->
                            val song = ui.songs.firstOrNull { it.id == item.songId }
                            val songRowInteraction = remember(item.id) { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index == 0) AppColors.BgCardHover else AppColors.BgCard, RoundedCornerShape(16.dp))
                                    .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(16.dp))
                                    .clickable(
                                        interactionSource = songRowInteraction,
                                        indication = rememberRipple(
                                            bounded = true,
                                            color = AppColors.Primary.copy(alpha = 0.18f),
                                            radius = 180.dp
                                        )
                                    ) {
                                        AppContainer.setSetlistSongBrowseContext(
                                            setlistId = setlistId,
                                            orderedSongIds = ui.items.map { it.songId }
                                        )
                                        onOpenSong(item.songId)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(AppColors.BgSurface, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "%02d".format(index + 1),
                                                color = if (index == 0) AppColors.PrimaryLight else AppColors.TextDim,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song?.title ?: item.songId,
                                                color = AppColors.TextWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song?.artist ?: "",
                                                color = AppColors.TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        KeyBadge(song?.originalKey ?: "C")
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            IconControl(Icons.Default.ArrowDropUp, "Поднять песню выше", enabled = index > 0) { vm.moveItem(item.id, -1) }
                                            IconControl(Icons.Default.ArrowDropDown, "Опустить песню ниже", enabled = index < ui.items.lastIndex) { vm.moveItem(item.id, 1) }
                                            IconControl(Icons.Default.Delete, "Убрать песню из сетлиста") { vm.removeItem(item) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPlayer) {
            BottomPlayerSheet(
                modifier = Modifier.align(Alignment.BottomCenter),
                orderedTracks = orderedTracks,
                currentTrackId = currentTrackId,
                playbackPositionMs = playbackPositionMs,
                playbackDurationMs = playbackDurationMs,
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                loopAll = loopAll,
                loopOne = loopOne,
                expanded = playerExpanded,
                onExpandedChange = { playerExpanded = it },
                onPlayPause = {
                    if (isPlaying) {
                        player.pause()
                    } else if (player.mediaItemCount > 0) {
                        player.play()
                    } else {
                        startPlaylist()
                    }
                },
                onStop = {
                    player.stop()
                    player.clearMediaItems()
                    currentTrackId = null
                    playbackPositionMs = 0L
                    playbackDurationMs = 0L
                },
                onToggleLoopAll = {
                    loopAll = !loopAll
                    if (loopAll) loopOne = false
                },
                onToggleLoopOne = {
                    loopOne = !loopOne
                    if (loopOne) loopAll = false
                },
                onPrevious = {
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                        player.playWhenReady = true
                    }
                },
                onNext = {
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.playWhenReady = true
                    }
                },
                onPlayTrack = { trackId -> startPlaylist(fromTrackId = trackId) },
                onSeek = { fraction ->
                    val duration = playbackDurationMs.takeIf { it > 0 } ?: return@BottomPlayerSheet
                    player.seekTo((duration * fraction).toLong().coerceIn(0L, duration))
                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                },
                onHeightChange = { playerSheetHeight = it }
            )
        }
    }
}

@Composable
private fun SetlistHeader(
    title: String,
    songsCount: Int,
    tracksCount: Int,
    editMode: Boolean,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BgCard, RoundedCornerShape(24.dp))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title.uppercase(), color = AppColors.TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaBadge("Песни: $songsCount")
                    MetaBadge("Треки: $tracksCount")
                }
            }
            BottomBarIconButton(
                icon = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = if (editMode) "Применить изменения сетлиста" else "Редактировать сетлист",
                active = editMode,
                onClick = onEditClick
            )
        }
    }
}

@Composable
private fun SetlistBottomBar(
    modifier: Modifier = Modifier,
    showPlayer: Boolean,
    onTogglePlayer: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.BgCard, RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarIconButton(
            icon = if (showPlayer) Icons.Default.KeyboardArrowDown else Icons.Default.PlayArrow,
            contentDescription = if (showPlayer) "Скрыть плеер" else "Показать плеер",
            active = showPlayer,
            onClick = onTogglePlayer
        )
    }
}

@Composable
private fun BottomPlayerSheet(
    modifier: Modifier = Modifier,
    orderedTracks: List<OrderedTrack>,
    currentTrackId: String?,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    loopAll: Boolean,
    loopOne: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onToggleLoopAll: () -> Unit,
    onToggleLoopOne: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayTrack: (String) -> Unit,
    onSeek: (Float) -> Unit,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val collapsedHeight = 18.dp
    val expandedHeight = 372.dp
    val collapsedPx = with(density) { collapsedHeight.toPx() }
    val expandedPx = with(density) { expandedHeight.toPx() }
    val panelHeightPx = remember { Animatable(if (expanded) expandedPx else collapsedPx) }
    LaunchedEffect(collapsedPx, expandedPx) {
        val target = if (expanded) expandedPx else collapsedPx
        if (panelHeightPx.value !in (collapsedPx - 1f)..(expandedPx + 1f)) {
            panelHeightPx.snapTo(target)
        } else if (kotlin.math.abs(panelHeightPx.targetValue - target) > 1f && kotlin.math.abs(panelHeightPx.value - target) > 1f) {
            panelHeightPx.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }
    val panelHeight = with(density) { panelHeightPx.value.toDp() }
    val contentRevealThreshold = collapsedHeight + 36.dp
    val showExpandedContent = panelHeight > contentRevealThreshold
    val currentTrack = remember(currentTrackId, orderedTracks) { orderedTracks.firstOrNull { it.track.id == currentTrackId } }
    val currentIndex = orderedTracks.indexOfFirst { it.track.id == currentTrackId }.takeIf { it >= 0 }
    val repeatIcon = when {
        loopOne -> Icons.Default.RepeatOne
        else -> Icons.Default.Repeat
    }
    val repeatActive = loopAll || loopOne
    val repeatDescription = when {
        loopOne -> "Повтор одной песни"
        loopAll -> "Повтор всего сетлиста"
        else -> "Повтор выключен"
    }
    val rowInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(panelHeight) {
        onHeightChange(panelHeight)
    }
    val handleDragState = rememberDraggableState { delta ->
        scope.launch {
            panelHeightPx.snapTo((panelHeightPx.value - delta).coerceIn(collapsedPx, expandedPx))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .height(panelHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = handleDragState,
                    onDragStopped = { velocity ->
                        val midpointPx = (collapsedPx + expandedPx) / 2f
                        val targetExpanded = when {
                            velocity < -900f -> true
                            velocity > 900f -> false
                            panelHeightPx.value >= midpointPx -> true
                            else -> false
                        }
                        onExpandedChange(targetExpanded)
                        scope.launch {
                            panelHeightPx.animateTo(
                                targetValue = if (targetExpanded) expandedPx else collapsedPx,
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            PlayerDragHandle(
                expanded = expanded,
                onClick = {
                    val targetExpanded = !expanded
                    onExpandedChange(targetExpanded)
                    scope.launch {
                        panelHeightPx.animateTo(
                            targetValue = if (targetExpanded) expandedPx else collapsedPx,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        )
                    }
                }
            )
        }

        if (showExpandedContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AppColors.Primary.copy(alpha = if (currentTrack != null) 0.14f else 0.06f),
                                AppColors.BgSurface.copy(alpha = 0.98f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, if (currentTrack != null) AppColors.Primary.copy(alpha = 0.28f) else AppColors.BorderGlassStrong, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Сетлист-плеер", color = AppColors.TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (currentTrack != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetaBadge("Сейчас играет")
                                currentIndex?.let { index -> MetaBadge("${index + 1}/${orderedTracks.size}") }
                                TrackSourcePill(
                                    label = currentTrack.sourceBadge().label,
                                    icon = currentTrack.sourceBadge().icon,
                                    active = true
                                )
                            }
                            Text(
                                text = currentTrack.track.title,
                                color = AppColors.PrimaryLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    StageIconButton(
                        icon = repeatIcon,
                        contentDescription = repeatDescription,
                        active = repeatActive,
                        buttonSize = 38.dp,
                        iconSize = 18.dp,
                        backgroundColor = AppColors.BgSurface.copy(alpha = 0.9f),
                        borderColor = AppColors.BorderGlassStrong,
                        activeBackgroundColor = AppColors.Primary.copy(alpha = 0.16f),
                        onClick = {
                            when {
                                loopOne -> onToggleLoopOne()
                                loopAll -> {
                                    onToggleLoopAll()
                                    onToggleLoopOne()
                                }
                                else -> onToggleLoopAll()
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerTransportButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Предыдущий трек",
                            enabled = canSkipPrevious,
                            onClick = onPrevious
                        )
                        PlayerTransportButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                            prominent = true,
                            onClick = onPlayPause
                        )
                        PlayerTransportButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Следующий трек",
                            enabled = canSkipNext,
                            onClick = onNext
                        )
                        PlayerTransportButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Остановить",
                            onClick = onStop
                        )
                    }
                }

                if (currentTrack != null) {
                    ThinTimelineBar(
                        positionMs = playbackPositionMs,
                        durationMs = playbackDurationMs,
                        onSeek = onSeek
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatPlaybackTime(playbackPositionMs), color = AppColors.TextDim, fontSize = 10.sp)
                        Text(formatPlaybackTime(playbackDurationMs), color = AppColors.TextDim, fontSize = 10.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Очередь", color = AppColors.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    PlayerMiniStatPill(text = "${orderedTracks.size} треков")
                }

                if (orderedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(AppColors.BgSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет прикреплённых аудиотреков", color = AppColors.TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(orderedTracks, key = { it.track.id }) { track ->
                            val sourceBadge = track.sourceBadge()
                            val isCurrent = currentTrackId == track.track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrent) AppColors.Primary.copy(alpha = 0.12f) else AppColors.BgSurface,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) AppColors.Primary.copy(alpha = 0.38f) else AppColors.BorderGlass,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable(
                                        interactionSource = rowInteraction,
                                        indication = rememberRipple(
                                            bounded = true,
                                            color = AppColors.Primary.copy(alpha = 0.20f),
                                            radius = 140.dp
                                        ),
                                        onClick = { onPlayTrack(track.track.id) }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (isCurrent) AppColors.Primary.copy(alpha = 0.22f) else AppColors.BgCard, CircleShape)
                                        .border(1.dp, if (isCurrent) AppColors.PrimaryLight else AppColors.BorderGlass, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isCurrent) AppColors.PrimaryLight else AppColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = track.track.title,
                                        color = if (isCurrent) AppColors.TextWhite else AppColors.TextLight,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.songTitle,
                                        color = if (isCurrent) AppColors.PrimaryLight else AppColors.TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TrackSourcePill(
                                    label = sourceBadge.label,
                                    icon = sourceBadge.icon,
                                    active = isCurrent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckPill(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (checked) AppColors.Primary.copy(alpha = 0.22f) else AppColors.BgSurface, CircleShape)
            .border(1.dp, if (checked) AppColors.PrimaryLight else AppColors.BorderGlassStrong, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.PrimaryLight, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Box(
        modifier = Modifier
            .background(AppColors.BgSurface, RoundedCornerShape(999.dp))
            .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = AppColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlayerMiniStatPill(text: String) {
    Box(
        modifier = Modifier
            .background(AppColors.BgDeep.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = AppColors.TextLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackSourcePill(
    label: String,
    icon: ImageVector,
    active: Boolean
) {
    Row(
        modifier = Modifier
            .background(
                if (active) AppColors.Primary.copy(alpha = 0.18f) else AppColors.BgCard,
                RoundedCornerShape(999.dp)
            )
            .border(
                1.dp,
                if (active) AppColors.PrimaryLight else AppColors.BorderGlass,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) AppColors.PrimaryLight else AppColors.TextMuted,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            color = if (active) AppColors.PrimaryLight else AppColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlayerToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    StageTextToggleChip(
        label = label,
        active = active,
        buttonSize = 44.dp,
        onClick = onClick
    )
}

@Composable
private fun IconControl(icon: ImageVector, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        enabled = enabled,
        backgroundColor = AppColors.BgSurface,
        borderColor = AppColors.BorderGlass,
        onClick = onClick
    )
}

@Composable
private fun BottomBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        active = active,
        backgroundColor = AppColors.BgSurface,
        borderColor = AppColors.BorderGlass,
        onClick = onClick
    )
}

@Composable
private fun ThinTimelineBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val progress = if (safeDuration > 0L) {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    androidx.compose.material3.Slider(
        value = progress,
        onValueChange = onSeek,
        valueRange = 0f..1f,
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = AppColors.PrimaryLight,
            activeTrackColor = AppColors.PrimaryLight,
            inactiveTrackColor = AppColors.BorderGlassStrong,
            activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
            inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun buildOrderedTracks(ui: SetlistEditorUi): List<OrderedTrack> {
    val songById = ui.songs.associateBy { it.id }
    val out = mutableListOf<OrderedTrack>()
    ui.items.forEach { item ->
        val songTitle = songById[item.songId]?.title ?: "Песня"
        val tracks = ui.audioBySongId[item.songId].orEmpty().sortedBy { it.addedAt }
        tracks.forEach { track ->
            out += OrderedTrack(
                track = track,
                songTitle = songTitle,
                remoteUri = track.remoteUrl?.takeIf { it.isNotBlank() },
                localUri = track.uri.takeIf { it.isNotBlank() }
            )
        }
    }
    return out
}
