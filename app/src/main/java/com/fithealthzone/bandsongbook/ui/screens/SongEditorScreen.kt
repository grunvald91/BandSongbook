package com.fithealthzone.bandsongbook.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.GlassCard
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.formatting.SongFormatting
import com.fithealthzone.bandsongbook.ui.viewmodel.SongEditorState
import com.fithealthzone.bandsongbook.ui.viewmodel.SongEditorViewModel
import com.fithealthzone.bandsongbook.ui.viewmodel.SongViewerFactory
import com.fithealthzone.bandsongbook.ui.viewmodel.SongViewerViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongEditorScreen(songId: String?, onSaved: () -> Unit) {
    val vm: SongEditorViewModel = viewModel()
    val state by vm.state.collectAsState()

    var lyricsField by remember { mutableStateOf(TextFieldValue(state.lyrics)) }
    var colorPickerExpanded by remember { mutableStateOf(false) }
    var selectedColorHex by remember { mutableStateOf("#00CEC9") }
    val editorScrollState = rememberScrollState()

    LaunchedEffect(songId) { vm.load(songId) }
    LaunchedEffect(state.lyrics) {
        if (state.lyrics != lyricsField.text) {
            lyricsField = TextFieldValue(state.lyrics, selection = TextRange(state.lyrics.length))
        }
    }

    fun set(update: SongEditorState) = vm.update(update)

    fun wrapSelection() {
        val wrapped = wrapSelectionWithChordTag(lyricsField)
        lyricsField = wrapped
        set(state.copy(lyrics = wrapped.text))
    }

    fun wrapSelectionWithMarker(openTag: String, closeTag: String) {
        val wrapped = wrapSelectionWithCustomTags(lyricsField, openTag, closeTag)
        lyricsField = wrapped
        set(state.copy(lyrics = wrapped.text))
    }

    fun toggleSelectionWithMarker(openTag: String, closeTag: String) {
        val wrapped = toggleSelectionWithCustomTags(lyricsField, openTag, closeTag)
        lyricsField = wrapped
        set(state.copy(lyrics = wrapped.text))
    }

    val colorOptions = listOf(
        "#00CEC9" to Color(0xFF00CEC9),
        "#6C5CE7" to Color(0xFF6C5CE7),
        "#FF6E84" to Color(0xFFFF6E84),
        "#FFD166" to Color(0xFFFFD166),
        "#4CD964" to Color(0xFF4CD964)
    )

    fun applySelectedColor() {
        val wrapped = toggleSelectionColorTag(lyricsField, selectedColorHex)
        lyricsField = wrapped
        set(state.copy(lyrics = wrapped.text))
    }

    fun applyHighlightAccent() {
        val edit = SongFormatting.toggleHighlight(
            lyricsField.text,
            lyricsField.selection.start,
            lyricsField.selection.end
        )
        val wrapped = TextFieldValue(
            text = edit.text,
            selection = TextRange(edit.selectionStart, edit.selectionEnd)
        )
        lyricsField = wrapped
        set(state.copy(lyrics = wrapped.text))
    }

    fun clearAllFormatting() {
        val clearedText = SongFormatting.clearAll(lyricsField.text)
        val cleared = TextFieldValue(clearedText, selection = TextRange(clearedText.length))
        lyricsField = cleared
        set(state.copy(lyrics = clearedText))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(editorScrollState)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(AppColors.Primary.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                            .border(1.dp, AppColors.Primary.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AppColors.PrimaryLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (songId == null) "Новая песня" else "Редактор",
                        color = AppColors.TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    StageIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Сохранить песню",
                        active = true,
                        buttonSize = 42.dp,
                        iconSize = 20.dp,
                        onClick = { vm.save(onSaved) }
                    )
                }
            }

            if (songId != null) {
                EditorAudioAttachmentSection(songId)
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewResponder(NoParentBringIntoViewResponder)
                            .background(Color.Transparent, RoundedCornerShape(18.dp))
                    ) {
                        OutlinedTextField(
                            value = lyricsField,
                            onValueChange = {
                                lyricsField = it
                                set(state.copy(lyrics = it.text))
                            },
                            visualTransformation = EffectTagHidingTransformation,
                            minLines = 15,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.TextLight,
                                unfocusedTextColor = AppColors.TextLight,
                                focusedBorderColor = AppColors.BorderGlassStrong,
                                unfocusedBorderColor = AppColors.BorderGlassStrong,
                                focusedContainerColor = AppColors.BgCard,
                                unfocusedContainerColor = AppColors.BgCard,
                                cursorColor = AppColors.PrimaryLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Метаданные", color = AppColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            state.title,
                            { set(state.copy(title = it)) },
                            label = { Text("Название") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            state.artist,
                            { set(state.copy(artist = it)) },
                            label = { Text("Исполнитель") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            state.key,
                            { set(state.copy(key = it)) },
                            label = { Text("Тональность") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            state.bpm,
                            { set(state.copy(bpm = it)) },
                            label = { Text("BPM") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            state.capo,
                            { set(state.copy(capo = it)) },
                            label = { Text("Капо") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        state.notes,
                        { set(state.copy(notes = it)) },
                        label = { Text("Заметки") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .imePadding()
                .padding(end = 10.dp, bottom = 2.dp)
        ) {
            if (colorPickerExpanded) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 52.dp, bottom = 108.dp)
                        .background(AppColors.BgDeep.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                        .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(18.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorOptions.forEach { (hex, color) ->
                        ColorDot(
                            color = color,
                            selected = selectedColorHex.equals(hex, ignoreCase = true),
                            onClick = {
                                selectedColorHex = hex
                                colorPickerExpanded = false
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(AppColors.BgDeep.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                    .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MiniFormatButton("B") { toggleSelectionWithMarker("<b>", "</b>") }
                MiniFormatButton("I") { toggleSelectionWithMarker("<i>", "</i>") }
                MiniFormatButton("U") { toggleSelectionWithMarker("<u>", "</u>") }
                ColorDot(
                    color = parseColorOrDefault(selectedColorHex),
                    selected = colorPickerExpanded,
                    onClick = { colorPickerExpanded = !colorPickerExpanded }
                )
                MiniFormatButton("A", accent = parseColorOrDefault(selectedColorHex)) { applySelectedColor() }
                MiniFormatIconButton(
                    icon = Icons.Default.AutoAwesome,
                    contentDescription = "Подсветить фрагмент",
                    accent = AppColors.PrimaryLight,
                    onClick = { applyHighlightAccent() }
                )
                MiniFormatButton("[ ]") { wrapSelection() }
                MiniFormatIconButton(
                    icon = Icons.Default.FormatClear,
                    contentDescription = "Полностью сбросить форматирование песни",
                    onClick = { clearAllFormatting() }
                )
            }
        }
    }
}

@Composable
private fun EditorAudioAttachmentSection(songId: String) {
    val context = LocalContext.current
    val vm: SongViewerViewModel = viewModel(
        key = "song-editor-audio-$songId",
        factory = SongViewerFactory(songId)
    )
    val audio by vm.audio.collectAsState()
    val uploadStatus by vm.uploadStatus.collectAsState()

    var title by remember(songId) { mutableStateOf("") }
    var selectedUri by remember(songId) { mutableStateOf<Uri?>(null) }
    var selectedName by remember(songId) { mutableStateOf<String?>(null) }
    var url by remember(songId) { mutableStateOf("") }
    var urlMode by remember(songId) { mutableStateOf(false) }
    val trimmedUrl = url.trim()
    val validUrl = remember(trimmedUrl) {
        val scheme = runCatching { Uri.parse(trimmedUrl).scheme?.lowercase() }.getOrNull()
        trimmedUrl.isNotBlank() && (scheme == "http" || scheme == "https")
    }
    val canAttach = selectedUri != null || (urlMode && validUrl)

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        selectedUri = uri
        selectedName = uri.lastPathSegment?.substringAfterLast('/')
        urlMode = false
        url = ""
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Прикреплённые дорожки", color = AppColors.TextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(audio.size.toString(), color = AppColors.TextMuted, fontSize = 11.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название дорожки") },
                    placeholder = { Text("Минус, вокал, гитара…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                StageIconButton(
                    icon = Icons.Default.AttachFile,
                    contentDescription = "Выбрать аудиофайл",
                    active = selectedUri != null,
                    onClick = { fileLauncher.launch(arrayOf("audio/*")) }
                )
                StageIconButton(
                    icon = Icons.Default.Link,
                    contentDescription = "Прикрепить аудио по ссылке",
                    active = urlMode,
                    onClick = {
                        urlMode = !urlMode
                        if (urlMode) {
                            selectedUri = null
                            selectedName = null
                        }
                    }
                )
            }

            selectedUri?.let {
                Text(
                    "Выбран файл: ${selectedName ?: it.lastPathSegment ?: "аудио"}",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            if (urlMode) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Прямая ссылка на аудио") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    isError = url.isNotBlank() && !validUrl,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (url.isNotBlank() && !validUrl) {
                    Text("Нужна ссылка http/https", color = AppColors.Error, fontSize = 10.sp)
                }
            }

            if (selectedUri != null || urlMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StageIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Прикрепить выбранную дорожку",
                        active = canAttach,
                        enabled = canAttach,
                        onClick = {
                            val fallback = title.trim().ifBlank {
                                selectedName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                                    ?: trimmedUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
                                    ?: "Дорожка ${audio.size + 1}"
                            }
                            selectedUri?.let { uri ->
                                vm.addAudioFromUri(context, songId, fallback, uri, uploadedBy = null)
                            } ?: run {
                                if (validUrl) vm.addAudioFromUrl(songId, fallback, trimmedUrl, uploadedBy = null)
                            }
                            title = ""
                            selectedUri = null
                            selectedName = null
                            url = ""
                            urlMode = false
                        }
                    )
                }
            }

            when (val status = uploadStatus) {
                is SongViewerViewModel.AudioUploadStatus.InProgress ->
                    Text("Загружаем «${status.title}»…", color = AppColors.PrimaryLight, fontSize = 11.sp)
                is SongViewerViewModel.AudioUploadStatus.Failed ->
                    Text(status.reason, color = AppColors.Error, fontSize = 11.sp)
                is SongViewerViewModel.AudioUploadStatus.Success ->
                    Text("«${status.title}» добавлено", color = AppColors.PrimaryLight, fontSize = 11.sp)
                else -> Unit
            }

            audio.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.BgSurface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = AppColors.TextLight, fontSize = 12.sp, maxLines = 1)
                        Text(if (item.uri.isNotBlank()) "Файл" else "URL", color = AppColors.TextMuted, fontSize = 10.sp)
                    }
                    StageIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Открепить ${item.title}",
                        buttonSize = 36.dp,
                        iconSize = 17.dp,
                        onClick = { vm.removeAudio(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object NoParentBringIntoViewResponder : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect = localRect

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

@Composable
private fun MiniFormatButton(label: String, accent: Color? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(AppColors.BgSurface.copy(alpha = 0.94f), CircleShape)
            .border(1.dp, AppColors.BorderGlass.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent ?: AppColors.TextLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MiniFormatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accent: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(AppColors.BgSurface.copy(alpha = 0.94f), CircleShape)
            .border(1.dp, AppColors.BorderGlass.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accent ?: AppColors.TextLight,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (selected) 28.dp else 24.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.TextLight else AppColors.BorderGlass,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private fun parseColorOrDefault(rawHex: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(rawHex)) }
        .getOrElse { AppColors.PrimaryLight }
}

private object EffectTagHidingTransformation : VisualTransformation {
    private val effectTagRegex = Regex("(?i)</?(b|u|i|color(?:=[^>]+)?|mark)>")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val matches = effectTagRegex.findAll(raw).toList()
        if (matches.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val visible = StringBuilder(raw.length)
        val styledRanges = mutableListOf<StyledRange>()
        val originalToTransformed = IntArray(raw.length + 1)

        var boldDepth = 0
        var italicDepth = 0
        var underlineDepth = 0
        val colorStack = ArrayDeque<Color>()
        var markDepth = 0

        var rawPos = 0
        var transformedPos = 0
        originalToTransformed[0] = 0

        matches.forEach { match ->
            val start = match.range.first
            val endExclusive = match.range.last + 1

            if (rawPos < start) {
                val segmentStart = transformedPos
                while (rawPos < start) {
                    visible.append(raw[rawPos])
                    transformedPos++
                    originalToTransformed[rawPos + 1] = transformedPos
                    rawPos++
                }
                val style = currentEffectStyle(
                    boldDepth = boldDepth,
                    italicDepth = italicDepth,
                    underlineDepth = underlineDepth,
                    color = colorStack.lastOrNull(),
                    highlighted = markDepth > 0
                )
                if (segmentStart < transformedPos && style != SpanStyle()) {
                    styledRanges += StyledRange(segmentStart, transformedPos, style)
                }
            }

            while (rawPos < endExclusive) {
                originalToTransformed[rawPos + 1] = transformedPos
                rawPos++
            }

            val token = match.value
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
        }

        if (rawPos < raw.length) {
            val segmentStart = transformedPos
            while (rawPos < raw.length) {
                visible.append(raw[rawPos])
                transformedPos++
                originalToTransformed[rawPos + 1] = transformedPos
                rawPos++
            }
            val style = currentEffectStyle(
                boldDepth = boldDepth,
                italicDepth = italicDepth,
                underlineDepth = underlineDepth,
                color = colorStack.lastOrNull(),
                highlighted = markDepth > 0
            )
            if (segmentStart < transformedPos && style != SpanStyle()) {
                styledRanges += StyledRange(segmentStart, transformedPos, style)
            }
        }

        val transformedToOriginal = IntArray(transformedPos + 1)
        var originalOffset = 0
        for (transformedOffset in 0..transformedPos) {
            while (originalOffset < originalToTransformed.size - 1 && originalToTransformed[originalOffset] < transformedOffset) {
                originalOffset++
            }
            transformedToOriginal[transformedOffset] = originalOffset
        }

        val builder = AnnotatedString.Builder(visible.toString())
        styledRanges.forEach { range ->
            builder.addStyle(range.style, range.start, range.end)
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, originalToTransformed.lastIndex)
                return originalToTransformed[clamped]
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transformedToOriginal.lastIndex)
                return transformedToOriginal[clamped]
            }
        }

        return TransformedText(builder.toAnnotatedString(), mapping)
    }
}

private data class StyledRange(val start: Int, val end: Int, val style: SpanStyle)

private fun currentEffectStyle(
    boldDepth: Int,
    italicDepth: Int,
    underlineDepth: Int,
    color: Color?,
    highlighted: Boolean
): SpanStyle {
    return SpanStyle(
        color = color ?: Color.Unspecified,
        background = if (highlighted) {
            if (AppColors.isDark) AppColors.BgCardHover else AppColors.BgDeep.copy(alpha = 0.18f)
        } else {
            Color.Unspecified
        },
        fontWeight = if (boldDepth > 0 || highlighted) FontWeight.Bold else null,
        fontStyle = if (italicDepth > 0) FontStyle.Italic else null,
        textDecoration = if (underlineDepth > 0) TextDecoration.Underline else null
    )
}

private fun parseColorTag(tag: String): Color? {
    val value = tag.removePrefix("<color=").removePrefix("<COLOR=").removeSuffix(">")
        .trim().trim('"', '\'')
    return runCatching { Color(android.graphics.Color.parseColor(SongFormatting.toAndroidColor(value))) }.getOrNull()
}

private fun toggleSelectionColorTag(value: TextFieldValue, colorHex: String): TextFieldValue {
    val normalizedColorHex = colorHex.uppercase()
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    if (start == end) return value

    val openRegex = Regex("(?i)<color=\\s*['\"]?${Regex.escape(normalizedColorHex)}['\"]?>$")
    val closeTag = "</color>"
    val before = value.text.substring(0, start)
    val after = value.text.substring(end)
    val openMatch = openRegex.find(before)

    if (openMatch != null && after.startsWith(closeTag)) {
        val openStart = openMatch.range.first
        val openEnd = before.length
        val newText = value.text.removeRange(end, end + closeTag.length).removeRange(openStart, openEnd)
        return TextFieldValue(
            text = newText,
            selection = TextRange(openStart, end - (openEnd - openStart))
        )
    }

    return wrapSelectionWithCustomTags(value, "<color=$normalizedColorHex>", closeTag)
}


private fun toggleSelectionWithCustomTags(value: TextFieldValue, openTag: String, closeTag: String): TextFieldValue {
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    if (start == end) return value

    val selected = value.text.substring(start, end)
    if (selected.startsWith(openTag) && selected.endsWith(closeTag)) {
        val unwrapped = selected.removePrefix(openTag).removeSuffix(closeTag)
        val newText = value.text.substring(0, start) + unwrapped + value.text.substring(end)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start, start + unwrapped.length)
        )
    }

    val blockStart = value.text.lastIndexOf(openTag, startIndex = start)
    val rawBlockEnd = value.text.indexOf(closeTag, startIndex = start)
    val blockEnd = if (rawBlockEnd >= 0) rawBlockEnd + closeTag.length else -1
    if (blockStart >= 0 && blockEnd > blockStart && end <= blockEnd) {
        val unwrapped = value.text.substring(blockStart + openTag.length, blockEnd - closeTag.length)
        val newText = value.text.substring(0, blockStart) + unwrapped + value.text.substring(blockEnd)
        return TextFieldValue(
            text = newText,
            selection = TextRange(blockStart, blockStart + unwrapped.length)
        )
    }

    val before = value.text.substring(0, start)
    val after = value.text.substring(end)
    if (before.endsWith(openTag) && after.startsWith(closeTag)) {
        val newText = value.text.removeRange(end, end + closeTag.length).removeRange(start - openTag.length, start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start - openTag.length, end - openTag.length)
        )
    }

    return wrapSelectionWithCustomTags(value, openTag, closeTag)
}

private fun wrapSelectionWithChordTag(value: TextFieldValue): TextFieldValue {
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)

    if (start == end) {
        val insert = "[] "
        val newText = value.text.substring(0, start) + insert + value.text.substring(start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start + 1)
        )
    }

    val selected = value.text.substring(start, end)
    val replacement = "[$selected] "
    val newText = value.text.substring(0, start) + replacement + value.text.substring(end)
    return TextFieldValue(
        text = newText,
        selection = TextRange(start + replacement.length)
    )
}


private fun wrapSelectionWithCustomTags(value: TextFieldValue, openTag: String, closeTag: String): TextFieldValue {
    val start = min(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = max(value.selection.start, value.selection.end).coerceIn(0, value.text.length)

    if (start == end) {
        val insert = "$openTag$closeTag"
        val newText = value.text.substring(0, start) + insert + value.text.substring(start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start + openTag.length)
        )
    }

    val edit = SongFormatting.wrapBalanced(value.text, start, end, openTag, closeTag)
    return TextFieldValue(
        text = edit.text,
        selection = TextRange(edit.selectionStart, edit.selectionEnd)
    )
}
