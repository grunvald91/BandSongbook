package com.fithealthzone.bandsongbook.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.net.ssl.HttpsURLConnection

private const val UPDATE_METADATA_URL = "https://bandbook.site/update.json"
private const val MAX_METADATA_BYTES = 64 * 1024
private const val MAX_APK_BYTES = 250L * 1024L * 1024L

class AppUpdateManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    @Suppress("DEPRECATION")
    private val currentPackageInfo: PackageInfo by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val currentVersionCode: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            currentPackageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            currentPackageInfo.versionCode
        }
    private val currentVersionName: String
        get() = currentPackageInfo.versionName ?: currentVersionCode.toString()

    suspend fun checkForUpdate(): UpdateDecision = withContext(Dispatchers.IO) {
        try {
            AppUpdatePolicy.evaluate(currentVersionCode, fetchMetadata())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateDecision.Failure(error.message ?: "Не удалось проверить обновления")
        }
    }

    suspend fun downloadAndVerify(
        metadata: AppUpdateMetadata,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val decision = AppUpdatePolicy.evaluate(currentVersionCode, metadata)
        require(decision is UpdateDecision.Available) { "Недопустимое обновление" }

        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(updateDir, "BandBook-${metadata.versionCode}.apk.part")
        val target = File(updateDir, "BandBook-${metadata.versionCode}.apk")
        partial.delete()
        target.delete()

        val connection = openHttps(metadata.apkUrl)
        var copied = 0L
        try {
            val length = connection.contentLengthLong
            require(length in 1..MAX_APK_BYTES) { "Некорректный размер APK" }
            require(length == metadata.sizeBytes) { "Размер APK не совпадает с метаданными" }
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 2)
                    var lastProgress = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        require(copied <= MAX_APK_BYTES) { "APK слишком большой" }
                        output.write(buffer, 0, read)
                        val progress = ((copied * 100L) / length).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        require(copied == metadata.sizeBytes) {
            partial.delete()
            "Размер загруженного APK не совпадает с метаданными"
        }

        require(AppUpdateIntegrity.matchesSha256(partial, metadata.sha256)) {
            partial.delete()
            "Контрольная сумма APK не совпадает"
        }
        require(validateArchive(partial, metadata.versionCode)) {
            partial.delete()
            "Подпись, пакет или версия APK не прошли проверку"
        }
        require(partial.renameTo(target)) { "Не удалось подготовить APK" }
        onProgress(100)
        target
    }

    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    private fun validateArchive(apk: File, expectedVersionCode: Int): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        if (archive.packageName != context.packageName || archiveVersion != expectedVersionCode.toLong()) return false

        val installedSigners = signingDigests(installed)
        val archiveSigners = signingDigests(archive)
        return installedSigners.isNotEmpty() && installedSigners == archiveSigners
    }

    @Suppress("DEPRECATION")
    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        }
        return signatures.orEmpty().mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun fetchMetadata(): AppUpdateMetadata {
        val connection = openHttps(UPDATE_METADATA_URL)
        return try {
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= MAX_METADATA_BYTES) { "Метаданные обновления слишком большие" }
            val bytes = connection.inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (output.size() <= MAX_METADATA_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            require(bytes.size <= MAX_METADATA_BYTES) { "Метаданные обновления слишком большие" }
            json.decodeFromString<AppUpdateMetadata>(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String): HttpsURLConnection {
        val parsed = URL(url)
        require(parsed.protocol.equals("https", ignoreCase = true))
        val connection = (parsed.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
            setRequestProperty("User-Agent", "BandSongbook/$currentVersionName")
        }
        try {
            connection.connect()
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            return connection
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }
}
