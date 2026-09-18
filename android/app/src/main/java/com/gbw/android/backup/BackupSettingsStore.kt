package com.gbw.android.backup

import android.content.Context

internal data class BackupSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Long = 1440L,
    val treeUri: String? = null,
    val destinationLabel: String? = null,
    val lastRunEpochMs: Long = 0L,
    val lastSuccessEpochMs: Long = 0L,
    val lastError: String? = null,
)

internal class BackupSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gbw_backup_settings", Context.MODE_PRIVATE)

    fun load(): BackupSettings = BackupSettings(
        enabled = prefs.getBoolean("enabled", false),
        intervalMinutes = prefs.getLong("intervalMinutes", 1440L),
        treeUri = prefs.getString("treeUri", null),
        destinationLabel = prefs.getString("destinationLabel", null),
        lastRunEpochMs = prefs.getLong("lastRunEpochMs", 0L),
        lastSuccessEpochMs = prefs.getLong("lastSuccessEpochMs", 0L),
        lastError = prefs.getString("lastError", null),
    )

    fun save(settings: BackupSettings) {
        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putLong("intervalMinutes", settings.intervalMinutes)
            .putString("treeUri", settings.treeUri)
            .putString("destinationLabel", settings.destinationLabel)
            .putLong("lastRunEpochMs", settings.lastRunEpochMs)
            .putLong("lastSuccessEpochMs", settings.lastSuccessEpochMs)
            .putString("lastError", settings.lastError)
            .commit()
    }

    fun updateRun(success: Boolean, error: String? = null, now: Long = System.currentTimeMillis()) {
        val current = load()
        save(current.copy(
            lastRunEpochMs = now,
            lastSuccessEpochMs = if (success) now else current.lastSuccessEpochMs,
            lastError = if (success) null else error,
        ))
    }

    fun disconnect() {
        val current = load()
        save(current.copy(enabled = false, treeUri = null, destinationLabel = null, lastError = null))
    }
}
