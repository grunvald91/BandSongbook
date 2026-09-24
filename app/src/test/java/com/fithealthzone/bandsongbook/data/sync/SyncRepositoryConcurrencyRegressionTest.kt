package com.fithealthzone.bandsongbook.data.sync

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryConcurrencyRegressionTest {

    private val source = Path.of(
        "src/main/java/com/fithealthzone/bandsongbook/data/sync/SyncRepository.kt"
    ).readText()

    @Test
    fun `all snapshot entry points share one mutex`() {
        assertTrue(source.contains("private val snapshotMutex = Mutex()"))
        assertTrue(source.contains("snapshotMutex.withLock { pushUnlocked(baseUrl, groupCode, authToken, memberName) }"))
        assertTrue(source.contains("snapshotMutex.withLock { pullUnlocked(baseUrl, groupCode, authToken, memberName) }"))
        assertTrue(source.contains("snapshotMutex.withLock { importSnapshotUnlocked(snapshot) }"))
        assertTrue(source.contains("snapshotMutex.withLock { exportSnapshotUnlocked(memberName) }"))
    }

    @Test
    fun `worker preserves coroutine cancellation`() {
        val worker = Path.of(
            "src/main/java/com/fithealthzone/bandsongbook/sync/SyncWorker.kt"
        ).readText()

        assertTrue(worker.contains("catch (error: CancellationException)"))
        assertTrue(worker.contains("throw error"))
    }

    @Test
    fun `round trip uses unlocked helpers inside its single lock`() {
        val roundTrip = source.substringAfter("suspend fun roundTrip(").substringBefore("suspend fun fetchGroupMeta(")

        assertTrue(roundTrip.contains("snapshotMutex.withLock"))
        assertTrue(roundTrip.contains("pullUnlocked("))
        assertTrue(roundTrip.contains("pushUnlocked("))
        assertFalse(roundTrip.contains("val snapshot = pull("))
        assertFalse(roundTrip.contains("push(baseUrl"))
    }

    @Test
    fun `group activation fetches remote state before an atomic optional wipe and never pushes`() {
        val activation = source.substringAfter("suspend fun activateFromRemote(")
            .substringBefore("suspend fun push(")
        assertTrue(activation.contains("snapshotMutex.withLock"))
        assertTrue(activation.indexOf("api.pull(") < activation.indexOf("clearAll()"))
        assertTrue(activation.contains("db.withTransaction"))
        assertFalse(activation.contains("api.push("))

        val settings = Path.of(
            "src/main/java/com/fithealthzone/bandsongbook/ui/viewmodel/SettingsViewModel.kt"
        ).readText().substringAfter("fun activateGroupMode(").substringBefore("fun activateLocalMode(")
        assertTrue(settings.contains("activateFromRemote("))
        assertFalse(settings.contains("roundTrip("))
        assertFalse(settings.contains("wipeLibraryData()"))
        assertTrue(settings.contains("catch (error: CancellationException)"))
        assertTrue(settings.contains("withContext(NonCancellable)"))
    }
}
