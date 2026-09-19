package com.gbw.android.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object BackupScheduler {
    const val MANUAL_ONLY = 0L
    val supportedIntervals = listOf(0L, 15L, 60L, 360L, 720L, 1440L)
    private const val PERIODIC_NAME = "gbw-backup-periodic"
    private const val DIRTY_NAME = "gbw-backup-dirty"
    private const val MANUAL_NAME = "gbw-backup-manual"
    const val QUIET_WINDOW_MS = 30_000L
    const val MAX_LATENCY_MS = 5L * 60L * 1000L

    fun applySettings(context: Context, settings: BackupSettings) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!settings.enabled || settings.treeUri.isNullOrBlank() || settings.intervalMinutes <= 0L) {
            wm.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val minutes = settings.intervalMinutes.coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(AutomaticBackupWorker.KEY_RECONCILE, true).build())
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("gbw-backup")
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun enqueueDirty(context: Context, delayMs: Long = QUIET_WINDOW_MS) {
        val settings = BackupSettingsStore(context).load()
        if (!settings.enabled || settings.treeUri.isNullOrBlank()) return
        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("gbw-backup")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(DIRTY_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueInitialSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setInputData(Data.Builder().putBoolean(AutomaticBackupWorker.KEY_RECONCILE, true).build())
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("gbw-backup")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(MANUAL_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setInputData(Data.Builder()
                .putBoolean(AutomaticBackupWorker.KEY_BACKUP_ALL, true)
                .putBoolean(AutomaticBackupWorker.KEY_RECONCILE, true)
                .build())
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("gbw-backup")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(MANUAL_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAutomatic(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_NAME)
        wm.cancelUniqueWork(DIRTY_NAME)
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
