package com.fithealthzone.bandsongbook.update

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Composable
fun AppUpdateHost(
    manualCheckRequest: Int = 0,
    notificationCheckRequest: Int = 0,
    notificationPermissionRequest: Int = 0
) {
    val context = LocalContext.current
    val manager = remember { AppUpdateManager(context.applicationContext) }
    val checkStore = remember { AppUpdateCheckStore(context.applicationContext) }
    val checkMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val initialNotificationRequest = remember { notificationCheckRequest }
    var available by remember { mutableStateOf<AppUpdateMetadata?>(null) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    suspend fun checkForUpdate(
        manual: Boolean,
        pendingFirst: Boolean = false,
        automatic: Boolean = false
    ) {
        checkMutex.lock()
        if (manual) {
            checking = true
            errorMessage = null
            infoMessage = null
        }
        try {
            if (pendingFirst) {
                val pending = checkStore.consumePending()
                if (
                    pending != null &&
                    AppUpdatePolicy.evaluate(manager.currentVersionCode, pending) is UpdateDecision.Available
                ) {
                    available = pending
                    return
                }
            }
            if (automatic && !checkStore.claimIfDue()) return
            when (val decision = manager.checkForUpdate()) {
                is UpdateDecision.Available -> available = decision.metadata
                is UpdateDecision.Invalid -> if (manual) {
                    errorMessage = "Сервер вернул некорректные данные обновления"
                }
                is UpdateDecision.Failure -> if (manual) {
                    errorMessage = "Не удалось проверить обновления. Проверьте подключение к интернету."
                }
                UpdateDecision.NotAvailable -> if (manual) {
                    infoMessage = "Установлена актуальная версия"
                }
            }
        } finally {
            checking = false
            checkMutex.unlock()
        }
    }

    fun openVerifiedInstaller(apk: File) {
        try {
            context.startActivity(manager.installIntent(apk))
            available = null
        } catch (_: ActivityNotFoundException) {
            errorMessage = "На устройстве не найден системный установщик APK"
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = pendingInstallApk
        pendingInstallApk = null
        if (apk != null && manager.canRequestPackageInstalls()) {
            openVerifiedInstaller(apk)
        } else if (apk != null) {
            errorMessage = "Разрешение на установку обновлений не предоставлено"
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        infoMessage = if (granted) {
            "Уведомления об обновлениях включены"
        } else {
            "Уведомления не разрешены. Еженедельная проверка продолжит работать, а обновление появится при открытии приложения."
        }
    }

    fun downloadAndOpenInstaller(metadata: AppUpdateMetadata) {
        available = null
        downloading = true
        progress = 0
        errorMessage = null
        scope.launch {
            try {
                val apk = manager.downloadAndVerify(metadata) { value ->
                    scope.launch { progress = value }
                }
                if (manager.canRequestPackageInstalls()) {
                    openVerifiedInstaller(apk)
                } else {
                    pendingInstallApk = apk
                    unknownSourcesLauncher.launch(manager.unknownSourcesSettingsIntent())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                errorMessage = "Не удалось безопасно скачать или проверить обновление"
            } finally {
                downloading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        AppUpdateScheduler.schedule(context.applicationContext)
        if (initialNotificationRequest > 0) {
            checkForUpdate(manual = true, pendingFirst = true)
        } else {
            checkForUpdate(manual = false, pendingFirst = true, automatic = true)
        }
    }

    LaunchedEffect(manualCheckRequest) {
        if (manualCheckRequest > 0) scope.launch {
            checkForUpdate(manual = true)
        }
    }

    LaunchedEffect(notificationCheckRequest) {
        if (notificationCheckRequest > initialNotificationRequest) scope.launch {
            checkForUpdate(manual = true, pendingFirst = true)
        }
    }

    LaunchedEffect(notificationPermissionRequest) {
        if (notificationPermissionRequest > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                infoMessage = "Уведомления об обновлениях доступны в настройках системы"
            }
        }
    }

    available?.let { metadata ->
        AlertDialog(
            onDismissRequest = { if (!downloading) available = null },
            title = { Text("Доступно обновление ${metadata.versionName}") },
            text = {
                Text(
                    if (metadata.releaseNotes.isNullOrBlank()) {
                        "Скачать новую версию приложения и открыть системный установщик?"
                    } else {
                        "${metadata.releaseNotes}\n\nСкачать обновление и установить его?"
                    }
                )
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = { downloadAndOpenInstaller(metadata) }) {
                    Text("Обновить")
                }
            },
            dismissButton = {
                TextButton(enabled = !downloading, onClick = { available = null }) {
                    Text("Позже")
                }
            }
        )
    }

    if (downloading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления") },
            text = { Text("Проверяем и скачиваем APK: $progress%") },
            confirmButton = { CircularProgressIndicator() }
        )
    }

    if (checking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Проверка обновлений") },
            text = { Text("Проверяем наличие новой версии…") },
            confirmButton = { CircularProgressIndicator() }
        )
    }

    infoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            title = { Text("Обновления") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) { Text("Закрыть") }
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Обновление не установлено") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("Закрыть") }
            }
        )
    }
}
