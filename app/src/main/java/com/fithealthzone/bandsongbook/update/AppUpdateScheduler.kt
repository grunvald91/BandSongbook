package com.fithealthzone.bandsongbook.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fithealthzone.bandsongbook.MainActivity
import com.fithealthzone.bandsongbook.R
import java.util.concurrent.TimeUnit

const val EXTRA_CHECK_UPDATES = "com.fithealthzone.bandsongbook.CHECK_UPDATES"

object AppUpdateCheckPolicy {
    const val WEEK_INTERVAL_MS: Long = 7L * 24L * 60L * 60L * 1000L

    fun isDue(lastCheckEpochMs: Long, nowEpochMs: Long): Boolean =
        lastCheckEpochMs <= 0L ||
            nowEpochMs < lastCheckEpochMs ||
            nowEpochMs - lastCheckEpochMs >= WEEK_INTERVAL_MS
}

class AppUpdateCheckStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_update_checks", Context.MODE_PRIVATE)

    fun claimIfDue(nowEpochMs: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (!AppUpdateCheckPolicy.isDue(lastCheck, nowEpochMs)) return@synchronized false
        preferences.edit().putLong(KEY_LAST_CHECK, nowEpochMs).commit()
    }

    fun savePending(metadata: AppUpdateMetadata) = synchronized(LOCK) {
        preferences.edit()
            .putInt(KEY_PENDING_CODE, metadata.versionCode)
            .putString(KEY_PENDING_NAME, metadata.versionName)
            .putString(KEY_PENDING_URL, metadata.apkUrl)
            .putString(KEY_PENDING_SHA, metadata.sha256)
            .putLong(KEY_PENDING_SIZE, metadata.sizeBytes)
            .putString(KEY_PENDING_NOTES, metadata.releaseNotes)
            .apply()
    }

    fun consumePending(): AppUpdateMetadata? = synchronized(LOCK) {
        val versionCode = preferences.getInt(KEY_PENDING_CODE, 0)
        if (versionCode <= 0) return@synchronized null
        val metadata = AppUpdateMetadata(
            versionCode = versionCode,
            versionName = preferences.getString(KEY_PENDING_NAME, null) ?: return@synchronized null,
            apkUrl = preferences.getString(KEY_PENDING_URL, null) ?: return@synchronized null,
            sha256 = preferences.getString(KEY_PENDING_SHA, null) ?: return@synchronized null,
            sizeBytes = preferences.getLong(KEY_PENDING_SIZE, 0L),
            releaseNotes = preferences.getString(KEY_PENDING_NOTES, null)
        )
        preferences.edit()
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_URL)
            .remove(KEY_PENDING_SHA)
            .remove(KEY_PENDING_SIZE)
            .remove(KEY_PENDING_NOTES)
            .apply()
        metadata
    }

    fun shouldNotify(versionCode: Int): Boolean = synchronized(LOCK) {
        preferences.getInt(KEY_NOTIFIED_CODE, 0) < versionCode
    }

    fun recordNotification(versionCode: Int) = synchronized(LOCK) {
        preferences.edit().putInt(KEY_NOTIFIED_CODE, versionCode).commit()
    }

    private companion object {
        val LOCK = Any()
        const val KEY_LAST_CHECK = "last_check_epoch_ms"
        const val KEY_NOTIFIED_CODE = "notified_version_code"
        const val KEY_PENDING_CODE = "pending_version_code"
        const val KEY_PENDING_NAME = "pending_version_name"
        const val KEY_PENDING_URL = "pending_apk_url"
        const val KEY_PENDING_SHA = "pending_sha256"
        const val KEY_PENDING_SIZE = "pending_size_bytes"
        const val KEY_PENDING_NOTES = "pending_release_notes"
    }
}

object AppUpdateScheduler {
    private const val WORK_NAME = "bandbook_weekly_update_check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = AppUpdateCheckStore(applicationContext)
        if (!store.claimIfDue()) return Result.success()

        val decision = AppUpdateManager(applicationContext).checkForUpdate()
        if (decision is UpdateDecision.Available) {
            store.savePending(decision.metadata)
            if (
                store.shouldNotify(decision.metadata.versionCode) &&
                showUpdateNotification(applicationContext, decision.metadata)
            ) {
                store.recordNotification(decision.metadata.versionCode)
            }
        }
        return Result.success()
    }

    private fun showUpdateNotification(context: Context, metadata: AppUpdateMetadata): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Обновления BandBook",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_CHECK_UPDATES, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            metadata.versionCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Доступно обновление BandBook ${metadata.versionName}")
            .setContentText("Нажмите, чтобы скачать и установить новую версию")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val CHANNEL_ID = "bandbook_updates"
        const val NOTIFICATION_ID = 2807
    }
}
