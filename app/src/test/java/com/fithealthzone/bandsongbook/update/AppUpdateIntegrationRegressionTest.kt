package com.fithealthzone.bandsongbook.update

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateIntegrationRegressionTest {

    @Test
    fun `application checks for updates from its root composable`() {
        val app = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/BandSongbookApp.kt").readText()
        assertTrue(app.contains("AppUpdateHost("))
    }

    @Test
    fun `settings exposes manual and weekly update checks`() {
        val settings = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/screens/SettingsScreen.kt").readText()
        val app = Path.of("src/main/java/com/fithealthzone/bandsongbook/ui/BandSongbookApp.kt").readText()

        assertTrue(settings.contains("Проверить обновления"))
        assertTrue(settings.contains("Автоматическая проверка: раз в неделю"))
        assertTrue(app.contains("manualUpdateCheckRequest"))
        assertTrue(app.contains("SettingsScreen("))
        assertTrue(app.contains("onCheckUpdates ="))
        assertTrue(app.contains("onEnableUpdateNotifications ="))
    }

    @Test
    fun `weekly updater uses unique periodic work with connected network`() {
        val scheduler = Path.of("src/main/java/com/fithealthzone/bandsongbook/update/AppUpdateScheduler.kt").readText()

        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<AppUpdateWorker>(7, TimeUnit.DAYS)"))
        assertTrue(scheduler.contains("NetworkType.CONNECTED"))
        assertTrue(scheduler.contains("enqueueUniquePeriodicWork"))
        assertTrue(scheduler.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(scheduler.contains("claimIfDue"))
        assertTrue(scheduler.contains("areNotificationsEnabled"))
        assertTrue(scheduler.contains("EXTRA_CHECK_UPDATES"))
    }

    @Test
    fun `manual check distinguishes no update from check failure`() {
        val host = Path.of("src/main/java/com/fithealthzone/bandsongbook/update/AppUpdateHost.kt").readText()

        assertTrue(host.contains("Установлена актуальная версия"))
        assertTrue(host.contains("Не удалось проверить обновления"))
        assertTrue(host.contains("Mutex()"))
        assertTrue(host.contains("checkMutex.lock()"))
        assertFalse(host.contains("checkMutex.tryLock()"))
        assertTrue(host.contains("UpdateDecision.Failure"))
        assertTrue(host.contains("notificationCheckRequest"))
        assertTrue(host.contains("pendingInstallApk"))
        assertTrue(host.contains("ActivityResultContracts.RequestPermission"))
    }

    @Test
    fun `download verifies declared size and exact signer set`() {
        val manager = Path.of("src/main/java/com/fithealthzone/bandsongbook/update/AppUpdateManager.kt").readText()

        assertTrue(manager.contains("copied == metadata.sizeBytes"))
        assertTrue(manager.contains("installedSigners == archiveSigners"))
        assertTrue(manager.contains("throw error"))
    }

    @Test
    fun `manifest permits user approved package installation through file provider`() {
        val manifest = Path.of("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("@xml/update_file_paths"))
    }

    @Test
    fun `archive signature verification supports Android 8 and newer`() {
        val manager = Path.of(
            "src/main/java/com/fithealthzone/bandsongbook/update/AppUpdateManager.kt"
        ).readText()
        assertTrue(manager.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.P"))
        assertTrue(manager.contains("PackageManager.GET_SIGNING_CERTIFICATES"))
        assertTrue(manager.contains("PackageManager.GET_SIGNATURES"))
    }
}
