package com.fithealthzone.bandsongbook.ui.screens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithealthzone.bandsongbook.data.settings.LibraryMode
import com.fithealthzone.bandsongbook.ui.theme.AppColors
import com.fithealthzone.bandsongbook.ui.theme.GlassCard
import com.fithealthzone.bandsongbook.ui.theme.StageIconButton
import com.fithealthzone.bandsongbook.ui.viewmodel.SettingsViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class QrInvitePayload(
    val version: Int = 1,
    val groupCode: String,
    val baseUrl: String? = null,
    val memberName: String? = null
)

private data class QrInvitePreview(
    val groupCode: String,
    val baseUrl: String,
    val memberName: String?
)

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit = {}) {
    val vm: SettingsViewModel = viewModel()
    val sync by vm.syncSettings.collectAsState()
    val libraryMode by vm.libraryMode.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val groupMeta by vm.groupSyncMeta.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrElse { "1.0" }
    }

    var groupCode by remember { mutableStateOf("") }
    var memberName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var authToken by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var scannedInvitePreview by remember { mutableStateOf<QrInvitePreview?>(null) }
    var wipeOnModeSwitch by remember { mutableStateOf(true) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var pendingModeTarget by remember { mutableStateOf<LibraryMode?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            showScannerDialog = true
        } else {
            vm.setStatus("Без доступа к камере сканирование QR недоступно")
        }
    }

    LaunchedEffect(sync, libraryMode) {
        groupCode = sync.groupCode
        memberName = sync.memberName
        baseUrl = sync.baseUrl
        authToken = sync.authToken
        if (libraryMode == LibraryMode.GROUP && sync.baseUrl.isNotBlank() && sync.groupCode.isNotBlank()) {
            vm.refreshGroupState(sync.baseUrl, sync.groupCode, sync.authToken, silent = true)
        }
    }

    val displayName = memberName.ifBlank { "Музыкант" }
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "BB" }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.TextWhite,
        unfocusedTextColor = AppColors.TextLight,
        focusedBorderColor = AppColors.PrimaryLight,
        unfocusedBorderColor = AppColors.BorderGlassStrong,
        focusedLabelColor = AppColors.PrimaryLight,
        unfocusedLabelColor = AppColors.TextMuted,
        cursorColor = AppColors.PrimaryLight
    )

    val normalizedGroupCode = remember(groupCode) { normalizeGroupCode(groupCode) }
    val qrPayload = remember(normalizedGroupCode, baseUrl) {
        buildInvitePayload(normalizedGroupCode, baseUrl)
    }
    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { createQrBitmap(it, size = 820) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("ПРОФИЛЬ", color = AppColors.TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Black)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = AppColors.PrimaryLight, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(displayName, color = AppColors.TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        text = if (groupCode.isBlank()) "Группа не подключена" else "Группа: ${normalizeGroupCode(groupCode)}",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (baseUrl.isBlank()) "Сервер не задан" else baseUrl,
                        color = AppColors.TextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = "Режим библиотеки", tint = AppColors.PrimaryLight)
                    Text("Режим библиотеки", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                }
                ModeSegmentRow(
                    selectedMode = libraryMode,
                    onSelectLocal = { pendingModeTarget = LibraryMode.LOCAL },
                    onSelectGroup = { pendingModeTarget = LibraryMode.GROUP }
                )
                WipeToggleRow(
                    enabled = wipeOnModeSwitch,
                    onToggle = {
                        if (!wipeOnModeSwitch) {
                            showWipeConfirmDialog = true
                        } else {
                            wipeOnModeSwitch = false
                        }
                    }
                )
                if (showWipeConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showWipeConfirmDialog = false },
                        title = { Text("Включить очистку при смене режима?") },
                        text = {
                            Text("При каждом переключении между облачным и локальным режимом на этом устройстве будут безвозвратно удаляться все песни, аудио и сетлисты. Это нельзя отменить.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                wipeOnModeSwitch = true
                                showWipeConfirmDialog = false
                            }) {
                                Text("Включить", color = AppColors.Error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWipeConfirmDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
                Text(
                    text = if (libraryMode == LibraryMode.GROUP) {
                        "Сейчас активен режим группы. Синхронизация и общий каталог включены."
                    } else {
                        "Сейчас активен локальный режим. Фоновые group-sync действия отключены."
                    },
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = "Данные участника", tint = AppColors.PrimaryLight)
                    Text("Участник и синхронизация", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    label = { Text("Ваше имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = tfColors
                )

                OutlinedTextField(
                    value = groupCode,
                    onValueChange = { groupCode = normalizeGroupCode(it) },
                    label = { Text("Код группы") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = tfColors
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL сервера") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = tfColors
                )

                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it.trim().replace(Regex("(?i)^bearer\\s+"), "") },
                    label = { Text("Токен") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = tfColors
                )

                ActionGroup(
                    title = "Синхронизация",
                    actions = listOf(
                        ProfileAction(
                            icon = Icons.Default.Check,
                            contentDescription = "Сохранить настройки синхронизации",
                            onClick = { vm.saveSync(baseUrl, groupCode, memberName, authToken) }
                        ),
                        ProfileAction(
                            icon = Icons.Default.Download,
                            contentDescription = "Загрузить изменения с сервера",
                            onClick = {
                                vm.saveSync(baseUrl, groupCode, memberName, authToken)
                                vm.pullNow(baseUrl, groupCode, authToken)
                            }
                        ),
                        ProfileAction(
                            icon = Icons.Default.Sync,
                            contentDescription = "Синхронизировать группу",
                            active = true,
                            onClick = {
                                vm.saveSync(baseUrl, groupCode, memberName, authToken)
                                vm.syncNow(baseUrl, groupCode, authToken, memberName)
                            }
                        )
                    )
                )

                ActionGroup(
                    title = "Приглашение по QR",
                    actions = listOf(
                        ProfileAction(
                            icon = Icons.Default.QrCode2,
                            contentDescription = "Показать QR группы",
                            enabled = qrPayload != null && qrBitmap != null,
                            onClick = {
                                if (qrPayload == null || qrBitmap == null) {
                                    vm.setStatus("Сначала укажи корректный код группы")
                                } else {
                                    showQrDialog = true
                                }
                            }
                        ),
                        ProfileAction(
                            icon = Icons.Default.CameraAlt,
                            contentDescription = "Сканировать QR приглашения",
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    )
                )

                if (normalizedGroupCode.isNotBlank()) {
                    InfoChip(
                        label = "Код подключения",
                        value = normalizedGroupCode,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(normalizedGroupCode))
                            vm.setStatus("Код группы скопирован")
                        }
                    )
                }

                syncStatus?.let {
                    Text(
                        text = it,
                        color = if (it.startsWith("Ошибка")) AppColors.Error else AppColors.TextMuted,
                        fontSize = 12.sp
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = "Состояние группы", tint = AppColors.TextMuted)
                    Text("Состояние группы", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                }
                InfoRow("Участник", displayName)
                InfoRow("Режим", if (libraryMode == LibraryMode.GROUP) "Группа" else "Локальный")
                InfoRow("Код группы", normalizedGroupCode.ifBlank { "—" })
                InfoRow("Последний sync", if (sync.lastSyncSuccessEpochMs > 0L) formatEpochMillis(sync.lastSyncSuccessEpochMs) else "—")
                groupMeta?.let {
                    InfoRow("Последний push", it.lastPushedBy)
                    InfoRow("Сервер обновлён", it.serverUpdatedAtText)
                    InfoRow("Участников", it.members.size.toString())
                }
            }
        }

        groupMeta?.members?.takeIf { it.isNotEmpty() }?.let { members ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Участники группы", tint = AppColors.TextMuted)
                        Text("Участники группы", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                    }
                    members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.BgSurface.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(member.name, color = AppColors.TextLight, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(member.lastSeenAtText, color = AppColors.TextMuted, fontSize = 11.sp)
                        }
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "О приложении", tint = AppColors.TextMuted)
                    Text("О приложении", color = AppColors.TextWhite, fontWeight = FontWeight.SemiBold)
                }
                Text("BandBook • v$versionName", color = AppColors.TextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.BgSurface.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Открыть настройки приложения", tint = AppColors.PrimaryLight)
                    Text("Настройки приложения", color = AppColors.TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    pendingModeTarget?.let { targetMode ->
        val targetLabel = if (targetMode == LibraryMode.GROUP) "режим группы" else "локальный режим"
        AlertDialog(
            onDismissRequest = { pendingModeTarget = null },
            title = { Text("Переключить режим") },
            text = {
                Text(
                    if (wipeOnModeSwitch) {
                        "Переключить библиотеку в $targetLabel с очисткой песен, аудио и сетлистов на этом устройстве?"
                    } else {
                        "Переключить библиотеку в $targetLabel без очистки локальной базы?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (targetMode == LibraryMode.GROUP) {
                        vm.activateGroupMode(
                            context = context,
                            baseUrl = baseUrl,
                            groupCode = groupCode,
                            memberName = memberName,
                            authToken = authToken,
                            wipeLibrary = wipeOnModeSwitch
                        )
                    } else {
                        vm.activateLocalMode(
                            context = context,
                            wipeLibrary = wipeOnModeSwitch
                        )
                    }
                    pendingModeTarget = null
                }) {
                    Text("Переключить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModeTarget = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showQrDialog && qrPayload != null && qrBitmap != null) {
        GroupQrDialog(
            groupCode = normalizedGroupCode,
            baseUrl = baseUrl.trim(),
            bitmap = qrBitmap,
            onDismiss = { showQrDialog = false },
            onCopyCode = {
                clipboardManager.setText(AnnotatedString(normalizedGroupCode))
                vm.setStatus("Код группы скопирован")
            },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, qrPayload)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Поделиться приглашением"))
            }
        )
    }

    if (showScannerDialog) {
        QrScannerDialog(
            hasCameraPermission = hasCameraPermission,
            onDismiss = { showScannerDialog = false },
            onDetected = { raw ->
                val parsed = parseQrInvite(raw)
                if (parsed == null) {
                    vm.setStatus("QR не распознан. Нужен код группы или приглашение BandBook.")
                } else {
                    scannedInvitePreview = parsed
                    showScannerDialog = false
                }
            }
        )
    }

    scannedInvitePreview?.let { invite ->
        AlertDialog(
            onDismissRequest = { scannedInvitePreview = null },
            title = { Text("Подключиться к группе") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Код группы: ${invite.groupCode}")
                    Text("Сервер: ${invite.baseUrl.ifBlank { "не указан" }}")
                    invite.memberName?.takeIf { it.isNotBlank() }?.let {
                        Text("Имя в приглашении: $it")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    groupCode = invite.groupCode
                    if (invite.baseUrl.isNotBlank()) {
                        baseUrl = invite.baseUrl
                    }
                    if (memberName.isBlank() && !invite.memberName.isNullOrBlank()) {
                        memberName = invite.memberName
                    }
                    vm.activateGroupMode(
                        context = context,
                        baseUrl = baseUrl.ifBlank { invite.baseUrl },
                        groupCode = invite.groupCode,
                        memberName = memberName.ifBlank { invite.memberName.orEmpty() },
                        authToken = authToken,
                        wipeLibrary = false
                    )
                    scannedInvitePreview = null
                }) {
                    Text("Подключиться")
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedInvitePreview = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private data class ProfileAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
private fun ActionGroup(title: String, actions: List<ProfileAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = AppColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                ProfileActionButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    active = action.active,
                    enabled = action.enabled,
                    onClick = action.onClick
                )
            }
        }
    }
}

@Composable
private fun ModeSegmentRow(
    selectedMode: LibraryMode,
    onSelectLocal: () -> Unit,
    onSelectGroup: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeSegmentButton(
            label = "Локальный",
            selected = selectedMode == LibraryMode.LOCAL,
            onClick = onSelectLocal,
            modifier = Modifier.weight(1f)
        )
        ModeSegmentButton(
            label = "Группа",
            selected = selectedMode == LibraryMode.GROUP,
            onClick = onSelectGroup,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AppColors.Primary.copy(alpha = 0.18f) else AppColors.BgSurface.copy(alpha = 0.78f))
            .border(1.dp, if (selected) AppColors.Primary.copy(alpha = 0.32f) else AppColors.BorderGlassStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.PrimaryLight else AppColors.TextLight,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WipeToggleRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.BgSurface.copy(alpha = 0.72f))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Очистка при смене режима", color = AppColors.TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "При переключении между облачным и локальным режимом удалять с этого устройства песни, аудио и сетлисты",
                color = AppColors.TextMuted,
                fontSize = 11.sp
            )
        }
        Text(
            text = if (enabled) "ВКЛ" else "ВЫКЛ",
            color = if (enabled) AppColors.PrimaryLight else AppColors.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BgSurface.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = AppColors.TextMuted, fontSize = 11.sp)
            Text(value, color = AppColors.TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        ProfileActionButton(
            icon = Icons.Default.ContentCopy,
            contentDescription = "Скопировать $label",
            onClick = onCopy
        )
    }
}

@Composable
private fun DialogBadge(text: String) {
    Text(
        text = text,
        color = AppColors.PrimaryLight,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(AppColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .border(1.dp, AppColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun GroupQrDialog(
    groupCode: String,
    baseUrl: String,
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onCopyCode: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR группы") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DialogBadge(text = "BandBook Invite")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.BgSurface.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
                        .border(1.dp, AppColors.Primary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(24.dp))
                            .border(1.dp, AppColors.PrimaryGlow, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR группы $groupCode",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Код группы",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = groupCode,
                        color = AppColors.TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (baseUrl.isBlank()) {
                        "Покажи QR участнику или отправь код группы вручную"
                    } else {
                        "Сервер: $baseUrl"
                    },
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileActionButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Скопировать код группы",
                        onClick = onCopyCode
                    )
                    ProfileActionButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Поделиться приглашением",
                        onClick = onShare
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
private fun QrScannerDialog(
    hasCameraPermission: Boolean,
    onDismiss: () -> Unit,
    onDetected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сканировать QR") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Наведи камеру на QR BandBook. Камера живёт только внутри этого окна и сразу закроется после считывания.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
                if (hasCameraPermission) {
                    EmbeddedQrScanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        onDetected = onDetected
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.BgSurface, RoundedCornerShape(18.dp))
                            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нужен доступ к камере", color = AppColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
private fun EmbeddedQrScanner(
    modifier: Modifier = Modifier,
    onDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val scannedOnce = remember { mutableStateOf(false) }
    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            setTorchListener(null)
            barcodeView.decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            statusView.text = ""
        }
    }

    DisposableEffect(barcodeView) {
        val callback = object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val text = result?.text.orEmpty()
                if (text.isNotBlank() && !scannedOnce.value) {
                    scannedOnce.value = true
                    barcodeView.pause()
                    onDetected(text)
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
        }
        barcodeView.decodeContinuous(callback)
        barcodeView.resume()
        onDispose {
            barcodeView.pause()
        }
    }

    Box(
        modifier = modifier
            .background(AppColors.BgSurface, RoundedCornerShape(22.dp))
            .border(1.dp, AppColors.BorderGlassStrong, RoundedCornerShape(22.dp))
            .padding(6.dp)
    ) {
        AndroidView(
            factory = { barcodeView },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) { view ->
            view.resume()
        }
        ScannerOverlay(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "qr-scan-line")
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr-scan-line-progress"
    )

    Box(modifier = modifier.padding(22.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, AppColors.Primary.copy(alpha = 0.26f), RoundedCornerShape(18.dp))
        )
        ScannerCorner(modifier = Modifier.align(Alignment.TopStart), cutoutOffsetX = 12.dp, cutoutOffsetY = 12.dp)
        ScannerCorner(modifier = Modifier.align(Alignment.TopEnd), cutoutOffsetX = (-12).dp, cutoutOffsetY = 12.dp)
        ScannerCorner(modifier = Modifier.align(Alignment.BottomEnd), cutoutOffsetX = (-12).dp, cutoutOffsetY = (-12).dp)
        ScannerCorner(modifier = Modifier.align(Alignment.BottomStart), cutoutOffsetX = 12.dp, cutoutOffsetY = (-12).dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (32 + 160 * lineProgress).dp)
                .width(170.dp)
                .height(3.dp)
                .background(AppColors.PrimaryLight.copy(alpha = 0.95f), RoundedCornerShape(999.dp))
        )
        Text(
            text = "Держи QR внутри рамки",
            color = AppColors.TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(AppColors.BgDeep.copy(alpha = 0.74f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ScannerCorner(
    modifier: Modifier = Modifier,
    cutoutOffsetX: androidx.compose.ui.unit.Dp,
    cutoutOffsetY: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, AppColors.PrimaryLight, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = cutoutOffsetX, y = cutoutOffsetY)
                .background(AppColors.BgSurface)
        )
    }
}

@Composable
private fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    StageIconButton(
        icon = icon,
        contentDescription = contentDescription,
        active = active,
        enabled = enabled,
        tint = if (active) Color.White else AppColors.PrimaryLight,
        backgroundColor = AppColors.BgSurface,
        activeBackgroundColor = AppColors.Primary,
        borderColor = AppColors.BorderGlassStrong,
        activeBorderColor = AppColors.PrimaryLight,
        onClick = onClick
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.TextMuted, fontSize = 12.sp)
        Text(
            value,
            color = AppColors.TextLight,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun buildInvitePayload(groupCode: String, baseUrl: String): String? {
    val normalizedGroup = normalizeGroupCode(groupCode)
    if (!isValidGroupCode(normalizedGroup)) return null
    return Json.encodeToString<QrInvitePayload>(
        QrInvitePayload(
            version = 1,
            groupCode = normalizedGroup,
            baseUrl = baseUrl.trim().trimEnd('/').ifBlank { null }
        )
    )
}

private fun parseQrInvite(raw: String): QrInvitePreview? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith("{")) {
        runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<QrInvitePayload>(trimmed)
        }.getOrNull()?.let { payload ->
            val normalizedGroup = normalizeGroupCode(payload.groupCode)
            if (isValidGroupCode(normalizedGroup)) {
                return QrInvitePreview(
                    groupCode = normalizedGroup,
                    baseUrl = payload.baseUrl?.trim().orEmpty(),
                    memberName = payload.memberName?.trim()
                )
            }
        }
    }

    runCatching { Uri.parse(trimmed) }.getOrNull()?.let { uri ->
        val group = sequenceOf("groupCode", "group", "code")
            .mapNotNull { uri.getQueryParameter(it) }
            .firstOrNull()
            ?.let(::normalizeGroupCode)
        if (!group.isNullOrBlank() && isValidGroupCode(group)) {
            val server = sequenceOf("baseUrl", "server", "url")
                .mapNotNull { uri.getQueryParameter(it) }
                .firstOrNull()
                .orEmpty()
            val member = sequenceOf("memberName", "name")
                .mapNotNull { uri.getQueryParameter(it) }
                .firstOrNull()
            return QrInvitePreview(groupCode = group, baseUrl = server, memberName = member)
        }
    }

    if ('|' in trimmed) {
        val parts = trimmed.split('|')
        if (parts.isNotEmpty()) {
            val group = normalizeGroupCode(parts.getOrNull(0).orEmpty())
            if (isValidGroupCode(group)) {
                return QrInvitePreview(
                    groupCode = group,
                    memberName = parts.getOrNull(1)?.trim(),
                    baseUrl = parts.getOrNull(2)?.trim().orEmpty()
                )
            }
        }
    }

    val directGroup = normalizeGroupCode(trimmed)
    if (isValidGroupCode(directGroup)) {
        return QrInvitePreview(groupCode = directGroup, baseUrl = "", memberName = null)
    }

    return null
}

private fun createQrBitmap(content: String, size: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }.getOrNull()
}

private fun normalizeGroupCode(raw: String): String {
    return raw
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9_.-]"), "")
        .replace(Regex("-+"), "-")
        .trim('-')
}

private fun isValidGroupCode(groupCode: String): Boolean {
    return Regex("^[a-z0-9][a-z0-9_.-]{1,63}$").matches(groupCode)
}

private fun formatEpochMillis(epochMs: Long): String {
    return runCatching {
        java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrElse { epochMs.toString() }
}
