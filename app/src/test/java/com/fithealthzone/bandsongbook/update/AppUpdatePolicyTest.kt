package com.fithealthzone.bandsongbook.update

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {

    @Test
    fun `offers a newer signed release from the trusted host`() {
        val metadata = AppUpdateMetadata(
            versionCode = 7,
            versionName = "2.5",
            apkUrl = "https://bandbook.site/BandBook.apk",
            sha256 = "a".repeat(64),
            sizeBytes = 18_000_000L,
            releaseNotes = "Новая версия"
        )

        val result = AppUpdatePolicy.evaluate(currentVersionCode = 5, metadata = metadata)

        assertEquals(UpdateDecision.Available(metadata), result)
    }

    @Test
    fun `does not offer current or older version`() {
        val metadata = validMetadata(versionCode = 5)

        assertEquals(UpdateDecision.NotAvailable, AppUpdatePolicy.evaluate(5, metadata))
        assertEquals(UpdateDecision.NotAvailable, AppUpdatePolicy.evaluate(6, metadata))
    }

    @Test
    fun `rejects non https and untrusted download hosts`() {
        val insecure = validMetadata(apkUrl = "http://bandbook.site/BandBook.apk")
        val foreign = validMetadata(apkUrl = "https://example.com/BandBook.apk")

        assertTrue(AppUpdatePolicy.evaluate(5, insecure) is UpdateDecision.Invalid)
        assertTrue(AppUpdatePolicy.evaluate(5, foreign) is UpdateDecision.Invalid)
    }

    @Test
    fun `rejects malformed sha256`() {
        val metadata = validMetadata(sha256 = "not-a-sha")

        assertTrue(AppUpdatePolicy.evaluate(5, metadata) is UpdateDecision.Invalid)
    }

    @Test
    fun `rejects credentials or a nonstandard port in download url`() {
        assertTrue(
            AppUpdatePolicy.evaluate(
                5,
                validMetadata(apkUrl = "https://user@bandbook.site/BandBook.apk")
            ) is UpdateDecision.Invalid
        )
        assertTrue(
            AppUpdatePolicy.evaluate(
                5,
                validMetadata(apkUrl = "https://bandbook.site:8443/BandBook.apk")
            ) is UpdateDecision.Invalid
        )
    }

    @Test
    fun `automatic foreground check becomes due once per seven days`() {
        val week = AppUpdateCheckPolicy.WEEK_INTERVAL_MS

        assertTrue(AppUpdateCheckPolicy.isDue(lastCheckEpochMs = 0L, nowEpochMs = 1L))
        assertTrue(!AppUpdateCheckPolicy.isDue(lastCheckEpochMs = 100L, nowEpochMs = 100L + week - 1L))
        assertTrue(AppUpdateCheckPolicy.isDue(lastCheckEpochMs = 100L, nowEpochMs = 100L + week))
        assertTrue(AppUpdateCheckPolicy.isDue(lastCheckEpochMs = 1_000L, nowEpochMs = 500L))
    }

    @Test
    fun `rejects missing expected apk size`() {
        val metadata = validMetadata().copy(sizeBytes = 0L)

        assertTrue(AppUpdatePolicy.evaluate(5, metadata) is UpdateDecision.Invalid)
    }

    @Test
    fun `verifies downloaded apk bytes against metadata hash`() {
        val file = Files.createTempFile("bandbook-update", ".apk").toFile()
        file.writeText("BandBook update test")

        assertTrue(
            AppUpdateIntegrity.matchesSha256(
                file,
                "e707c7efdd3ed3fb7c349bc2fd551dddf022707622550fdfaa6685e487ae586b"
            )
        )
        assertTrue(!AppUpdateIntegrity.matchesSha256(file, "0".repeat(64)))
        file.delete()
    }

    private fun validMetadata(
        versionCode: Int = 6,
        apkUrl: String = "https://bandbook.site/BandBook.apk",
        sha256: String = "b".repeat(64)
    ) = AppUpdateMetadata(
        versionCode = versionCode,
        versionName = "2.4",
        apkUrl = apkUrl,
        sha256 = sha256,
        sizeBytes = 18_000_000L,
        releaseNotes = null
    )
}
