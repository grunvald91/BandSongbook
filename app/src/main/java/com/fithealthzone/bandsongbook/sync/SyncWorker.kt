package com.fithealthzone.bandsongbook.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fithealthzone.bandsongbook.AppContainer
import com.fithealthzone.bandsongbook.data.settings.LibraryMode
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppContainer.init(applicationContext)
        val mode = AppContainer.settingsRepository.getLibraryModeSnapshot()
        val sync = AppContainer.settingsRepository.getSyncSettingsSnapshot()

        if (mode != LibraryMode.GROUP) return Result.success()
        if (!sync.backgroundEnabled) return Result.success()
        if (sync.baseUrl.isBlank() || sync.groupCode.isBlank()) return Result.success()

        return try {
            AppContainer.syncRepository.roundTrip(
                sync.baseUrl,
                sync.groupCode,
                sync.authToken,
                sync.memberName.ifBlank { "Unknown" }
            )
            AppContainer.settingsRepository.setLastSyncSuccessEpochMs()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            when (error) {
                is ClientRequestException -> {
                    // 4xx: чаще всего неверный group/token/контракт — не бесконечно ретраить.
                    val code = error.response.status.value
                    if (code == 408 || code == 429) Result.retry() else Result.failure()
                }
                is ServerResponseException -> Result.retry() // 5xx
                else -> Result.retry() // сеть/таймаут/прочее
            }
        }
    }
}
