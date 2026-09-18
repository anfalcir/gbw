package com.gbw.android.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

internal class AutomaticBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsStore = BackupSettingsStore(applicationContext)
        val settings = settingsStore.load()
        if (settings.treeUri.isNullOrBlank()) return Result.success()
        val backupAll = inputData.getBoolean(KEY_BACKUP_ALL, false)
        return try {
            setForeground(foregroundInfo("Preparando backup…"))
            if (backupAll) {
                ProjectBackupCoordinator(applicationContext).backup(null)
            } else {
                runCoalesced()
            }
            settingsStore.updateRun(success = true)
            Result.success()
        } catch (error: Exception) {
            settingsStore.updateRun(success = false, error = error.message ?: error::class.java.simpleName)
            Result.retry()
        }
    }

    private suspend fun runCoalesced() {
        val dirtyStore = BackupDirtyStore(applicationContext)
        var followUps = 0
        while (true) {
            var states = dirtyStore.list()
            if (states.isEmpty()) return
            val now = System.currentTimeMillis()
            val eligible = states.filter {
                now - it.lastChangedAtEpochMs >= BackupScheduler.QUIET_WINDOW_MS ||
                    now - it.dirtySinceEpochMs >= BackupScheduler.MAX_LATENCY_MS
            }
            if (eligible.isEmpty()) {
                val wait = states.minOf {
                    minOf(
                        (it.lastChangedAtEpochMs + BackupScheduler.QUIET_WINDOW_MS - now).coerceAtLeast(250L),
                        (it.dirtySinceEpochMs + BackupScheduler.MAX_LATENCY_MS - now).coerceAtLeast(250L),
                    )
                }.coerceAtMost(BackupScheduler.QUIET_WINDOW_MS)
                delay(wait)
                continue
            }
            setForeground(foregroundInfo("Salvando ${eligible.size} projeto(s)…"))
            ProjectBackupCoordinator(applicationContext).backup(eligible.map { it.projectId }.toSet())
            states = dirtyStore.list()
            if (states.isEmpty()) return
            // A mutation during upload remains dirty. One coalesced follow-up is
            // executed by this same UNIQUE worker; no second worker is spawned.
            followUps += 1
            if (followUps >= 8) return
        }
    }

    private fun foregroundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backup do GBW", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val cancel = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.gbw.android.R.drawable.ic_stat_gbw)
            .setContentTitle("GBW — Backup")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, "Cancelar", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_BACKUP_ALL = "backup_all"
        private const val CHANNEL_ID = "gbw_backup"
        private const val NOTIFICATION_ID = 2401
    }
}
