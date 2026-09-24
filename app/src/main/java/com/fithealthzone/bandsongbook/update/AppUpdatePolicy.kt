package com.fithealthzone.bandsongbook.update

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateMetadata(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseNotes: String? = null
)

sealed interface UpdateDecision {
    data object NotAvailable : UpdateDecision
    data class Available(val metadata: AppUpdateMetadata) : UpdateDecision
    data class Invalid(val reason: String) : UpdateDecision
    data class Failure(val reason: String) : UpdateDecision
}

object AppUpdatePolicy {
    private const val TRUSTED_HOST = "bandbook.site"
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    fun evaluate(currentVersionCode: Int, metadata: AppUpdateMetadata): UpdateDecision {
        if (metadata.versionCode <= currentVersionCode) return UpdateDecision.NotAvailable
        if (metadata.versionName.isBlank()) return UpdateDecision.Invalid("Не указана версия")
        if (!sha256Pattern.matches(metadata.sha256)) return UpdateDecision.Invalid("Некорректная контрольная сумма")
        if (metadata.sizeBytes <= 0L) return UpdateDecision.Invalid("Некорректный размер APK")

        val uri = runCatching { URI(metadata.apkUrl) }.getOrNull()
            ?: return UpdateDecision.Invalid("Некорректная ссылка")
        if (
            uri.scheme?.lowercase() != "https" ||
            uri.host?.lowercase() != TRUSTED_HOST ||
            uri.userInfo != null ||
            uri.port !in listOf(-1, 443)
        ) {
            return UpdateDecision.Invalid("Обновление должно загружаться только с доверенного HTTPS-адреса")
        }

        return UpdateDecision.Available(metadata)
    }
}
