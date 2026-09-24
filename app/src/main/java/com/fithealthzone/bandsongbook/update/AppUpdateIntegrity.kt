package com.fithealthzone.bandsongbook.update

import java.io.File
import java.security.MessageDigest

object AppUpdateIntegrity {
    fun matchesSha256(file: File, expectedSha256: String): Boolean {
        if (!expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }
}
