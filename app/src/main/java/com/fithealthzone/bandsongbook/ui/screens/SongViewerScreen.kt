package com.fithealthzone.bandsongbook.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.settings.DisplaySettings
import com.fithealthzone.bandsongbook.data.settings.ThemeMode
import com.fithealthzone.bandsongbook.media.AudioPlaybackCache
import com.fithealthzone.bandsongbook.transpose.ChordTransposer
import com.fithealthzone.bandsongbook.ui.LocalSnackbarHostState
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.KeyBadge
import com.fithealthzone.bandsongbook.ui.theme.PlayerDragHandle
import com.fithealthzone.bandsongbook.ui.theme.PlayerTransportButton
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.ui.theme.StageTextToggleChip
import com.fithealthzone.bandsongbook.ui.viewmodel.SongViewerFactory
import com.fithealthzone.bandsongbook.ui.viewmodel.SongViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val chordRegex = Regex("""\[(.+?)]""")
private val effectTagRegex = Regex("(?i)</?(b|u|i|color(?:=[^>]+)?|mark)>")
private const val AUTO_SCROLL_BASE_PX_PER_SECOND = 22f

@Composable
fun SongViewerScreen(
    songId: String,
    initialFullscreen: Boolean = false,
    onEdit: () -> Unit,
    onNavigateToSong: (String) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit
) {
    val vm: SongViewerViewModel = viewModel(factory = SongViewerFactory(songId))

    val song by vm.song.collectAsState()
    val audio by vm.audio.collectAsState()
    val transpose by vm.transpose.collectAsState()
    val speed by vm.autoSpeed.collectAsState()
    val uploadStatus by vm.uploadStatus.collectAsState()

    val display by AppContainer.settingsRepository.displaySettings.collectAsState(
        initial = DisplaySettings(
            preferFlats = false,
            themeMode = ThemeMode.DARK,
            lyricsFontSp = 16,
            chordsFontSp = 14
        )
    )
    val preferFlats by vm.preferFlats.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(uploadStatus) {
        when (val status = uploadStatus) {
            is SongViewerViewModel.AudioUploadStatus.InProgress -> {
                snackbarHostState.showSnackbar("Загружаем «${status.title}» на сервер…")
            }
            is SongViewerViewModel.AudioUploadStatus.Success -> {
                snackbarHostState.showSnackbar("«${status.title}» загружено в группу")
                vm.acknowledgeUploadStatus()
            }
            is SongViewerViewModel.AudioUploadStatus.Failed -> {
                snackbarHostState.showSnackbar(
                    message = "Не удалось загрузить «${status.title}»: ${status.reason}",
                    duration = androidx.compose.material3.SnackbarDuration.Long
                )
                vm.acknowledgeUploadStatus()
            }
            SongViewerViewModel.AudioUploadStatus.Idle -> Unit
        }
    }

    var autoScroll by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(initialFullscreen) }
    var controlsExpanded by remember { mutableStateOf(true) }

    var partLabel by remember { mutableStateOf("") }
    var pendingAudioUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAudioName by remember { mutableStateOf<String?>(null) }
    var pendingAudioUrl by remember { mutableStateOf("") }
    var isUrlInputVisible by remember { mutableStateOf(false) }

    var currentPlayingAudioId by remember { mutableStateOf<String?>(null) }
    var isPlayerPlaying by remember { mutableStateOf(false) }
    var isAudioExpanded by remember { mutableStateOf(true) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var playbackDurationMs by remember { mutableLongStateOf(0L) }
    var swipeAccumulatedX by remember(songId) { mutableFloatStateOf(0f) }
    val nextSongId = remember(songId) { AppContainer.nextSongIdFromSetlist(songId) }
    val previousSongId = remember(songId) { AppContainer.previousSongIdFromSetlist(songId) }
    val canSwipeBetweenSetlistSongs = nextSongId != null || previousSongId != null
    val swipeToSongState = rememberDraggableState { delta ->
        swipeAccumulatedX += delta
    }
    fun navigateToAdjacentSong(targetSongId: String?) {
        targetSongId ?: return
        swipeAccumulatedX = 0f
        onNavigateToSong(targetSongId)
    }
    val swipeToSongModifier = Modifier.draggable(
        orientation = Orientation.Horizontal,
        enabled = canSwipeBetweenSetlistSongs,
        state = swipeToSongState,
        onDragStopped = { velocity ->
            val shouldOpenNext = nextSongId != null && (swipeAccumulatedX < -72f || velocity < -1200f)
            val shouldOpenPrevious = previousSongId != null && (swipeAccumulatedX > 72f || velocity > 1200f)
            val targetSongId = when {
                shouldOpenNext -> nextSongId
                shouldOpenPrevious -> previousSongId
                else -> null
            }
            swipeAccumulatedX = 0f
            navigateToAdjacentSong(targetSongId)
        }
    )

    val trimmedPendingAudioUrl = pendingAudioUrl.trim()
    val hasValidPendingAudioUrl = remember(trimmedPendingAudioUrl) {
        val parsed = runCatching { Uri.parse(trimmedPendingAudioUrl) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        trimmedPendingAudioUrl.isNotBlank() && (scheme == "http" || scheme == "https")
    }
    val canSubmitPendingAudio = pendingAudioUri != null || (isUrlInputVisible && hasValidPendingAudioUrl)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pendingAudioUri = it
            pendingAudioName = resolveDisplayName(context, it)
            isUrlInputVisible = false
            pendingAudioUrl = ""
        }
    }

    val player = remember { AudioPlaybackCache.buildPlayer(context) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlayerPlaying = false
                }
                playbackDurationMs = player.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playbackPositionMs = 0L
                playbackDurationMs = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(song?.id) { autoScroll = false }
    LaunchedEffect(isFullscreen) { onFullscreenChanged(isFullscreen) }

    DisposableEffect(autoScroll) {
        val prev = view.keepScreenOn
        view.keepScreenOn = autoScroll
        onDispose { view.keepScreenOn = prev }
    }

    val transformedLyrics = remember(song?.lyricsWithChords, transpose, preferFlats) {
        song?.lyricsWithChords?.let {
            ChordTransposer.transposeLyrics(it, transpose, preferFlats = preferFlats)
        } ?: ""
    }

    val viewerLyricsFontSp = display.lyricsFontSp.toFloat()
    val viewerChordsFontSp = display.chordsFontSp.toFloat()

    val renderedLines = remember(transformedLyrics, viewerChordsFontSp) {
        renderLyricsParagraphsWithFormatting(
            lyrics = transformedLyrics,
            chordColor = AppColors.PrimaryLight,
            chordFontSize = viewerChordsFontSp.sp
        )
    }

    var speedDraft by remember(song?.id) { mutableStateOf(speed) }
    LaunchedEffect(speed) {
        if (!autoScroll) speedDraft = speed
    }

    val currentAutoScrollSpeed by rememberUpdatedState(speedDraft)
    LaunchedEffect(autoScroll, renderedLines.size) {
        if (!autoScroll) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (autoScroll) {
            val dtSeconds = withFrameNanos { frameTimeNanos ->
                val previous = lastFrameNanos
                lastFrameNanos = frameTimeNanos
                if (previous == 0L) 0f
                else ((frameTimeNanos - previous) / 1_000_000_000f).coerceAtMost(0.1f)
            }
            if (dtSeconds <= 0f) continue
            val pxPerSecond = AUTO_SCROLL_BASE_PX_PER_SECOND * currentAutoScrollSpeed
            val delta = pxPerSecond * dtSeconds
            val consumed = listState.scrollBy(delta)
            if (consumed < delta - 0.5f) {
                autoScroll = false
                break
            }
        }
    }

    LaunchedEffect(currentPlayingAudioId, isPlayerPlaying) {
        while (currentPlayingAudioId != null) {
            playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
            playbackDurationMs = player.duration.takeIf { it > 0 } ?: 0L
            delay(if (isPlayerPlaying) 250 else 500)
        }
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.BgDeep)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(swipeToSongModifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                state = listState
            ) {
                item {
                    Text(
                        text = song?.title ?: "Загрузка...",
                        color = AppColors.TextWhite,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        val currentKeyLabel = remember(song?.originalKey, transpose, preferFlats) {
                            song?.originalKey
                                ?.takeIf { it.isNotBlank() }
                                ?.let { ChordTransposer.transposeChord(it, transpose, preferFlats) }
                                ?: "C"
                        }
                        KeyBadge(currentKeyLabel)
                        val capoValue = song?.capo
                        if (capoValue != null && capoValue > 0) {
                            TinyMetaChip("Capo $capoValue")
                        }
                    }
                }
                items(renderedLines) { line ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (line.hasHighlight) highlightBlockColor() else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = if (line.hasHighlight) 10.dp else 0.dp, vertical = if (line.hasHighlight) 7.dp else 0.dp)
                    ) {
                        Text(
                            text = line.text,
                            color = AppColors.TextLight,
                            fontSize = viewerLyricsFontSp.sp,
                            lineHeight = (viewerLyricsFontSp + 6f).sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            OverlayIconButton(
                icon = Icons.Default.FullscreenExit,
                contentDescription = "Выйти из полноэкранного",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                onClick = { isFullscreen = false }
            )

            OverlayIconButton(
                icon = if (autoScroll) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (autoScroll) "Пауза автопрокрутки" else "Запустить автопрокрутку",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(14.dp),
                active = autoScroll,
                onClick = { autoScroll = !autoScroll }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BgCard, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song?.title ?: "Загрузка...",
                            color = AppColors.TextWhite,
                            fontSize = 22.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(song?.artist ?: "", color = AppColors.TextMuted, fontSize = 11.sp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        CompactIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Редактировать песню",
                            onClick = onEdit
                        )
                        CompactIconButton(
                            icon = Icons.Default.Fullscreen,
                            contentDescription = "Открыть полный экран",
                            onClick = { isFullscreen = true }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TinyMetaChip(song?.bpm?.let { "$it BPM" } ?: "— BPM")
                    CompactIconButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Понизить тональность",
                        onClick = { vm.transposeBy(-1) }
                    )
                    TinyMetaChip(if (transpose >= 0) "T+$transpose" else "T$transpose")
                    CompactIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Повысить тональность",
                        onClick = { vm.transposeBy(1) }
                    )
                    StageTextToggleChip(
                        label = if (preferFlats) "♭" else "♯",
                        active = preferFlats,
                        buttonSize = 36.dp,
                        onClick = { vm.setPreferFlats(!preferFlats) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val currentKeyLabel = remember(song?.originalKey, transpose, preferFlats) {
                        song?.originalKey
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ChordTransposer.transposeChord(it, transpose, preferFlats) }
                            ?: "C"
                    }
                    KeyBadge(currentKeyLabel)
                    val capoValue = song?.capo
                    if (capoValue != null && capoValue > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TinyMetaChip("Capo $capoValue")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(swipeToSongModifier)
                .background(AppColors.BgCard.copy(alpha = 0.74f), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(renderedLines) { paragraph ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (paragraph.hasHighlight) highlightBlockColor() else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = if (paragraph.hasHighlight) 10.dp else 0.dp, vertical = if (paragraph.hasHighlight) 7.dp else 0.dp)
                ) {
                    Text(
                        text = paragraph.text,
                        color = AppColors.TextLight,
                        fontSize = viewerLyricsFontSp.sp,
                        lineHeight = (viewerLyricsFontSp + 5f).sp
                    )
                }
            }
        }

        val density = LocalDensity.current
        val collapsedControlsHeight = 18.dp
        val expandedControlsHeight = when {
            pendingAudioUri != null || isUrlInputVisible -> 320.dp
            audio.isNotEmpty() -> 272.dp
            else -> 228.dp
        }
        val collapsedControlsPx = with(density) { collapsedControlsHeight.toPx() }
        val expandedControlsPx = with(density) { expandedControlsHeight.toPx() }
        val controlsHeightPx = remember { Animatable(if (controlsExpanded) expandedControlsPx else collapsedControlsPx) }
        LaunchedEffect(collapsedControlsPx, expandedControlsPx) {
            val target = if (controlsExpanded) expandedControlsPx else collapsedControlsPx
            if (controlsHeightPx.value !in (collapsedControlsPx - 1f)..(expandedControlsPx + 1f)) {
                controlsHeightPx.snapTo(target)
            } else if (kotlin.math.abs(controlsHeightPx.targetValue - target) > 1f && kotlin.math.abs(controlsHeightPx.value - target) > 1f) {
                controlsHeightPx.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
        val controlsPanelHeight = with(density) { controlsHeightPx.value.toDp() }
        val controlsRevealThreshold = collapsedControlsHeight + 28.dp
        val showExpandedControls = controlsPanelHeight > controlsRevealThreshold
        val controlsDragState = rememberDraggableState { delta ->
            scope.launch {
                controlsHeightPx.snapTo((controlsHeightPx.value - delta).coerceIn(collapsedControlsPx, expandedControlsPx))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(controlsPanelHeight),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = controlsDragState,
                        onDragStopped = { velocity ->
                            val midpointPx = (collapsedControlsPx + expandedControlsPx) / 2f
                            val targetExpanded = when {
                                velocity < -900f -> true
                                velocity > 900f -> false
                                controlsHeightPx.value >= midpointPx -> true
                                else -> false
                            }
                            controlsExpanded = targetExpanded
                            scope.launch {
                                controlsHeightPx.animateTo(
                                    targetValue = if (targetExpanded) expandedControlsPx else collapsedControlsPx,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                PlayerDragHandle(
                    expanded = controlsExpanded,
                    onClick = {
                        val targetExpanded = !controlsExpanded
                        controlsExpanded = targetExpanded
                        scope.launch {
                            controlsHeightPx.animateTo(
                                targetValue = if (targetExpanded) expandedControlsPx else collapsedControlsPx,
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                )
            }

            if (showExpandedControls) {
                val panelScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(AppColors.BgCard, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(panelScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (autoScroll) "Автопрокрутка: ВКЛ" else "Автопрокрутка", color = AppColors.TextLight, fontSize = 11.sp)
                            Text("${"%.1f".format(speedDraft)}x", color = AppColors.PrimaryLight, fontSize = 11.sp)
                        }
                        Slider(
                            value = speedDraft,
                            onValueChange = { speedDraft = it },
                            onValueChangeFinished = { vm.persistSpeed(speedDraft) },
                            valueRange = 0.5f..3f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactIconButton(
                                    icon = if (autoScroll) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (autoScroll) "Пауза автопрокрутки" else "Запустить автопрокрутку",
                                    active = autoScroll,
                                    onClick = { autoScroll = !autoScroll }
                                )
                                CompactIconButton(
                                    icon = Icons.Default.AttachFile,
                                    contentDescription = "Прикрепить аудиофайл",
                                    active = pendingAudioUri != null,
                                    onClick = { launcher.launch(arrayOf("audio/*")) }
                                )
                                CompactIconButton(
                                    icon = Icons.Default.Link,
                                    contentDescription = if (isUrlInputVisible) "Скрыть ввод URL" else "Добавить аудио по URL",
                                    active = isUrlInputVisible,
                                    onClick = {
                                        isUrlInputVisible = !isUrlInputVisible
                                        if (isUrlInputVisible) {
                                            pendingAudioUri = null
                                            pendingAudioName = null
                                        }
                                    }
                                )
                                CompactIconButton(
                                    icon = Icons.Default.LibraryMusic,
                                    contentDescription = if (isAudioExpanded) "Скрыть аудио" else "Показать аудио",
                                    active = isAudioExpanded,
                                    onClick = { isAudioExpanded = !isAudioExpanded }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactIconButton(
                                    icon = Icons.Default.ContentCopy,
                                    contentDescription = "Скопировать текст песни",
                                    onClick = {
                                        val plainText = extractLyricsOnly(song?.lyricsWithChords.orEmpty())
                                        clipboardManager.setText(AnnotatedString(plainText))
                                    }
                                )
                            }
                        }

                        if (pendingAudioUri != null || isUrlInputVisible) {
                            if (pendingAudioUri != null) {
                                Text(
                                    text = "Выбран файл: ${pendingAudioName ?: pendingAudioUri.toString()}",
                                    color = AppColors.TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            if (isUrlInputVisible) {
                                OutlinedTextField(
                                    value = pendingAudioUrl,
                                    onValueChange = { pendingAudioUrl = it },
                                    label = { Text("URL аудио", color = AppColors.TextMuted) },
                                    placeholder = { Text("https://...", color = AppColors.TextDim, fontSize = 12.sp) },
                                    singleLine = true,
                                    isError = pendingAudioUrl.isNotBlank() && !hasValidPendingAudioUrl,
                                    textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextLight),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.TextLight,
                                        unfocusedTextColor = AppColors.TextLight,
                                        focusedLabelColor = AppColors.PrimaryLight,
                                        unfocusedLabelColor = AppColors.TextMuted,
                                        focusedBorderColor = AppColors.PrimaryLight,
                                        unfocusedBorderColor = AppColors.BorderGlass.copy(alpha = 0.45f),
                                        errorBorderColor = AppColors.Error,
                                        errorLabelColor = AppColors.Error,
                                        focusedContainerColor = AppColors.BgSurface.copy(alpha = 0.96f),
                                        unfocusedContainerColor = AppColors.BgSurface.copy(alpha = 0.92f),
                                        errorContainerColor = AppColors.BgSurface.copy(alpha = 0.92f),
                                        cursorColor = AppColors.PrimaryLight
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (pendingAudioUrl.isNotBlank() && !hasValidPendingAudioUrl) {
                                    Text(
                                        text = "Нужна прямая ссылка http/https",
                                        color = AppColors.Error,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = partLabel,
                                    onValueChange = { partLabel = it },
                                    label = { Text("Подпись", color = AppColors.TextMuted) },
                                    placeholder = { Text("Например: Соло", color = AppColors.TextDim, fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextLight),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.TextLight,
                                        unfocusedTextColor = AppColors.TextLight,
                                        focusedLabelColor = AppColors.PrimaryLight,
                                        unfocusedLabelColor = AppColors.TextMuted,
                                        focusedBorderColor = AppColors.PrimaryLight,
                                        unfocusedBorderColor = AppColors.BorderGlass.copy(alpha = 0.45f),
                                        focusedContainerColor = AppColors.BgSurface.copy(alpha = 0.96f),
                                        unfocusedContainerColor = AppColors.BgSurface.copy(alpha = 0.92f),
                                        cursorColor = AppColors.PrimaryLight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                )

                                CompactIconButton(
                                    icon = Icons.Default.Check,
                                    contentDescription = if (canSubmitPendingAudio) "Подтвердить аудио" else "Выбери файл или введи корректный URL",
                                    enabled = canSubmitPendingAudio,
                                    onClick = {
                                        val fallbackTitle = partLabel.trim().ifBlank {
                                            pendingAudioName
                                                ?.substringBeforeLast('.')
                                                ?.takeIf { it.isNotBlank() }
                                                ?: trimmedPendingAudioUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
                                                ?: "Трек ${audio.size + 1}"
                                        }
                                        val selectedUri = pendingAudioUri
                                        if (selectedUri != null) {
                                            vm.addAudioFromUri(
                                                context = context,
                                                songId = songId,
                                                title = fallbackTitle,
                                                uri = selectedUri,
                                                uploadedBy = null
                                            )
                                            pendingAudioUri = null
                                            pendingAudioName = null
                                        } else if (isUrlInputVisible && hasValidPendingAudioUrl) {
                                            vm.addAudioFromUrl(
                                                songId = songId,
                                                title = fallbackTitle,
                                                url = trimmedPendingAudioUrl,
                                                uploadedBy = null
                                            )
                                            pendingAudioUrl = ""
                                            isUrlInputVisible = false
                                        }
                                        partLabel = ""
                                    }
                                )
                            }
                        }

                        if (audio.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Аудио",
                                    color = AppColors.TextMuted,
                                    fontSize = 10.sp
                                )
                                TinyMetaChip(audio.size.toString())
                            }
                        }

                        if (isAudioExpanded) {
                            audio.forEach { a ->
                                key(a.id) {
                                    val sourceBadge = audioSourceBadge(remoteUrl = a.remoteUrl, localUri = a.uri)
                                    val isCurrent = currentPlayingAudioId == a.id
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = if (isCurrent) 140.dp else 64.dp)
                                            .background(
                                                brush = if (isCurrent) {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            AppColors.Primary.copy(alpha = 0.22f),
                                                            AppColors.BgSurface.copy(alpha = 0.98f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            AppColors.BgSurface.copy(alpha = 0.88f),
                                                            AppColors.BgSurface.copy(alpha = 0.88f)
                                                        )
                                                    )
                                                },
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .border(
                                                width = if (isCurrent) 1.dp else 1.dp,
                                                color = if (isCurrent) AppColors.Primary.copy(alpha = 0.35f) else AppColors.BorderGlass.copy(alpha = 0.24f),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .padding(
                                                horizontal = if (isCurrent) 14.dp else 12.dp,
                                                vertical = if (isCurrent) 12.dp else 10.dp
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(if (isCurrent) 10.dp else 8.dp)
                                    ) {
                                        if (isCurrent) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TinyMetaChip("Сейчас играет")
                                                    AudioSourcePill(
                                                        label = sourceBadge.label,
                                                        icon = sourceBadge.icon,
                                                        active = true
                                                    )
                                                }
                                                Text(
                                                    text = a.title,
                                                    color = AppColors.TextWhite,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    PlayerTransportButton(
                                                        icon = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isPlayerPlaying) "Пауза ${a.title}" else "Играть ${a.title}",
                                                        prominent = true,
                                                        active = true,
                                                        onClick = {
                                                            if (isPlayerPlaying) {
                                                                player.pause()
                                                                return@PlayerTransportButton
                                                            }
                                                            if (player.mediaItemCount > 0) {
                                                                player.play()
                                                                return@PlayerTransportButton
                                                            }
                                                            scope.launch {
                                                                val playbackUri = vm.resolvePlaybackUri(context, a)
                                                                if (playbackUri.isNullOrBlank()) {
                                                                    snackbarHostState.showSnackbar(
                                                                        message = "Не удалось получить аудио с сервера. Проверь настройки синхронизации.",
                                                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                                                    )
                                                                    return@launch
                                                                }
                                                                player.setMediaItem(MediaItem.fromUri(playbackUri))
                                                                player.prepare()
                                                                player.playWhenReady = true
                                                                currentPlayingAudioId = a.id
                                                            }
                                                        }
                                                    )
                                                    PlayerTransportButton(
                                                        icon = Icons.Default.Stop,
                                                        contentDescription = "Остановить ${a.title}",
                                                        onClick = {
                                                            player.stop()
                                                            currentPlayingAudioId = null
                                                            isPlayerPlaying = false
                                                            playbackPositionMs = 0L
                                                            playbackDurationMs = 0L
                                                        }
                                                    )
                                                    PlayerTransportButton(
                                                        icon = Icons.Default.Delete,
                                                        contentDescription = "Открепить ${a.title}",
                                                        onClick = {
                                                            player.stop()
                                                            currentPlayingAudioId = null
                                                            isPlayerPlaying = false
                                                            playbackPositionMs = 0L
                                                            playbackDurationMs = 0L
                                                            vm.removeAudio(a)
                                                        }
                                                    )
                                                }
                                            }
                                            ThinSeekBar(
                                                positionMs = playbackPositionMs,
                                                durationMs = playbackDurationMs,
                                                onSeek = { fraction ->
                                                    val duration = playbackDurationMs.takeIf { it > 0 } ?: return@ThinSeekBar
                                                    player.seekTo((duration * fraction).toLong().coerceIn(0L, duration))
                                                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                                                }
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(formatPlaybackTime(playbackPositionMs), color = AppColors.TextDim, fontSize = 10.sp)
                                                Text(formatPlaybackTime(playbackDurationMs), color = AppColors.TextDim, fontSize = 10.sp)
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    AudioSourcePill(
                                                        label = sourceBadge.label,
                                                        icon = sourceBadge.icon,
                                                        active = false
                                                    )
                                                    Text(
                                                        text = a.title,
                                                        color = AppColors.TextMuted,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                PlayerTransportButton(
                                                    icon = Icons.Default.PlayArrow,
                                                    contentDescription = "Играть ${a.title}",
                                                    onClick = {
                                                        scope.launch {
                                                            val playbackUri = vm.resolvePlaybackUri(context, a)
                                                            if (playbackUri.isNullOrBlank()) {
                                                                snackbarHostState.showSnackbar(
                                                                    message = "Не удалось получить аудио с сервера. Проверь настройки синхронизации.",
                                                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                                                )
                                                                return@launch
                                                            }
                                                            player.setMediaItem(MediaItem.fromUri(playbackUri))
                                                            player.prepare()
                                                            player.playWhenReady = true
                                                            currentPlayingAudioId = a.id
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    val resolver = context.contentResolver
    return runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getString(idx) else null
        }
    }.getOrNull()
}

private fun renderLineWithChordHighlights(
    line: String,
    chordColor: Color,
    chordFontSize: TextUnit
): AnnotatedString {
    if (line.isEmpty()) return AnnotatedString("")
    val segments = parseInlineFormatting(line)
    return buildAnnotatedString {
        segments.forEach { segment ->
            var cursor = 0
            chordRegex.findAll(segment.text).forEach { match ->
                val start = match.range.first
                if (start > cursor) {
                    withStyle(segment.style) {
                        append(segment.text.substring(cursor, start))
                    }
                }
                val rawToken = match.value
                val chord = match.groupValues[1]
                val paddedChord = " " + chord + " "
                withStyle(
                    segment.style.merge(
                        SpanStyle(
                            color = chordColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = chordFontSize
                        )
                    )
                ) {
                    append(paddedChord)
                }
                cursor = match.range.last + 1
            }
            if (cursor < segment.text.length) {
                withStyle(segment.style) {
                    append(segment.text.substring(cursor))
                }
            }
        }
    }
}

private data class LyricsParagraph(
    val text: AnnotatedString,
    val hasHighlight: Boolean
)

private fun renderLyricsParagraphsWithFormatting(
    lyrics: String,
    chordColor: Color,
    chordFontSize: TextUnit
): List<LyricsParagraph> {
    if (lyrics.isEmpty()) return listOf(LyricsParagraph(AnnotatedString(""), hasHighlight = false))

    val segments = parseInlineFormatting(lyrics)
    val out = mutableListOf<LyricsParagraph>()
    var currentParagraph = AnnotatedString.Builder()
    var paragraphHasHighlight = false
    var newlineRun = 0

    fun flushParagraph(forceEmpty: Boolean = false) {
        if (currentParagraph.length > 0 || forceEmpty) {
            out += LyricsParagraph(
                text = currentParagraph.toAnnotatedString(),
                hasHighlight = paragraphHasHighlight
            )
            currentParagraph = AnnotatedString.Builder()
            paragraphHasHighlight = false
        }
    }

    segments.forEach { segment ->
        if (segment.highlighted) paragraphHasHighlight = true
        var cursor = 0
        chordRegex.findAll(segment.text).forEach { match ->
            val start = match.range.first
            if (start > cursor) {
                currentParagraph.withStyle(segment.style) {
                    append(segment.text.substring(cursor, start))
                }
            }
            val rawToken = match.value
            val chord = match.groupValues[1]
            val paddedChord = " " + chord + " "
            currentParagraph.withStyle(
                segment.style.merge(
                    SpanStyle(
                        color = chordColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = chordFontSize
                    )
                )
            ) {
                append(paddedChord)
            }
            cursor = match.range.last + 1
        }

        val tail = segment.text.substring(cursor)
        tail.forEach { ch ->
            if (ch == '\n') {
                newlineRun += 1
                if (newlineRun >= 2) {
                    flushParagraph()
                    newlineRun = 0
                } else {
                    currentParagraph.append('\n')
                }
            } else {
                newlineRun = 0
                currentParagraph.withStyle(segment.style) {
                    append(ch)
                }
            }
        }
    }

    flushParagraph(forceEmpty = true)
    return out
}

private data class InlineSegment(
    val text: String,
    val style: SpanStyle,
    val highlighted: Boolean
)

private fun parseInlineFormatting(text: String): List<InlineSegment> {
    val matches = effectTagRegex.findAll(text).toList()
    if (matches.isEmpty()) return listOf(InlineSegment(text, SpanStyle(), highlighted = false))

    val segments = mutableListOf<InlineSegment>()
    var rawPos = 0
    var boldDepth = 0
    var italicDepth = 0
    var underlineDepth = 0
    var markDepth = 0
    val colorStack = ArrayDeque<Color>()

    fun currentStyle(): SpanStyle {
        return SpanStyle(
            color = colorStack.lastOrNull() ?: Color.Unspecified,
            background = Color.Unspecified,
            fontWeight = if (boldDepth > 0 || markDepth > 0) FontWeight.Bold else null,
            fontStyle = if (italicDepth > 0) FontStyle.Italic else null,
            textDecoration = if (underlineDepth > 0) TextDecoration.Underline else null
        )
    }

    fun isHighlighted(): Boolean = markDepth > 0

    matches.forEach { match ->
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (rawPos < start) {
            val segmentText = text.substring(rawPos, start)
            if (segmentText.isNotEmpty()) {
                segments += InlineSegment(segmentText, currentStyle(), highlighted = isHighlighted())
            }
        }

        val token = text.substring(start, endExclusive)
        when {
            token.equals("<b>", ignoreCase = true) -> boldDepth++
            token.equals("</b>", ignoreCase = true) -> boldDepth = (boldDepth - 1).coerceAtLeast(0)
            token.equals("<i>", ignoreCase = true) -> italicDepth++
            token.equals("</i>", ignoreCase = true) -> italicDepth = (italicDepth - 1).coerceAtLeast(0)
            token.equals("<u>", ignoreCase = true) -> underlineDepth++
            token.equals("</u>", ignoreCase = true) -> underlineDepth = (underlineDepth - 1).coerceAtLeast(0)
            token.startsWith("<color=", ignoreCase = true) -> parseColorTag(token)?.let { colorStack.addLast(it) }
            token.equals("</color>", ignoreCase = true) -> if (colorStack.isNotEmpty()) colorStack.removeLast()
            token.equals("<mark>", ignoreCase = true) -> markDepth++
            token.equals("</mark>", ignoreCase = true) -> markDepth = (markDepth - 1).coerceAtLeast(0)
        }
        rawPos = endExclusive
    }

    if (rawPos < text.length) {
        val tail = text.substring(rawPos)
        if (tail.isNotEmpty()) segments += InlineSegment(tail, currentStyle(), highlighted = isHighlighted())
    }

    return if (segments.isEmpty()) listOf(InlineSegment("", SpanStyle(), highlighted = false)) else segments
}

private fun parseColorTag(tag: String): Color? {
    val value = tag
        .removePrefix("<color=")
        .removePrefix("<COLOR=")
        .removeSuffix(">")
        .trim()
        .trim('"', '\'')
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
}

private fun highlightBlockColor(): Color {
    return if (AppColors.isDark) {
        AppColors.BgCardHover.copy(alpha = 0.96f)
    } else {
        AppColors.BgDeep.copy(alpha = 0.16f)
    }
}

private fun extractLyricsOnly(rawLyricsWithChords: String): String {
    return rawLyricsWithChords
        .replace(chordRegex, "")
        .replace(Regex("(?i)</?(chorus|bridge|b|u|i|mark)>"), "")
        .replace(Regex("(?i)<color=[^>]+>|</color>"), "")
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()
}

private data class AudioSourceBadgeModel(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun audioSourceBadge(remoteUrl: String?, localUri: String): AudioSourceBadgeModel {
    return when {
        !remoteUrl.isNullOrBlank() -> AudioSourceBadgeModel("URL", Icons.Default.Link)
        localUri.isNotBlank() -> AudioSourceBadgeModel("Файл", Icons.Default.AttachFile)
        else -> AudioSourceBadgeModel("Источник", Icons.Default.LibraryMusic)
    }
}

@Composable
private fun AudioSourcePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean
) {
    Row(
        modifier = Modifier
            .background(
                if (active) AppColors.Primary.copy(alpha = 0.18f) else AppColors.BgSurface.copy(alpha = 0.86f),
                RoundedCornerShape(999.dp)
            )
            .border(
                1.dp,
                if (active) AppColors.PrimaryLight else AppColors.BorderGlass.copy(alpha = 0.32f),
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
private fun TinyMetaChip(text: String) {
    Box(
        modifier = Modifier
            .background(AppColors.BgSurface.copy(alpha = 0.86f), RoundedCornerShape(99.dp))
            .border(1.dp, AppColors.BorderGlass.copy(alpha = 0.28f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        active = active,
        backgroundColor = AppColors.BgSurface.copy(alpha = 0.94f),
        borderColor = AppColors.BorderGlass.copy(alpha = 0.38f),
        onClick = onClick
    )
}

@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        active = active,
        enabled = enabled,
        buttonSize = 42.dp,
        iconSize = 18.dp,
        backgroundColor = AppColors.BgSurface.copy(alpha = 0.94f),
        borderColor = AppColors.BorderGlass.copy(alpha = 0.38f),
        onClick = onClick
    )
}

@Composable
private fun ThinSeekBar(
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

    Slider(
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
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
