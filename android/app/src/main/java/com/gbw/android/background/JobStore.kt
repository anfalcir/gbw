package com.gbw.android.background

import android.content.Context

data class PersistedJob(
    val id: String,
    val type: String,
    val label: String,
    val state: String,
    val progress: Int,
    val startedAt: Long,
    val message: String,
)

class JobStore(context: Context) {
    private val prefs = context.getSharedPreferences("gbw_jobs", Context.MODE_PRIVATE)

    fun save(job: PersistedJob) {
        prefs.edit()
            .putString("id", job.id)
            .putString("type", job.type)
            .putString("label", job.label)
            .putString("state", job.state)
            .putInt("progress", job.progress)
            .putLong("startedAt", job.startedAt)
            .putString("message", job.message)
            .apply()
    }

    fun load(): PersistedJob? {
        val id = prefs.getString("id", null) ?: return null
        return PersistedJob(
            id = id,
            type = prefs.getString("type", "") ?: "",
            label = prefs.getString("label", "") ?: "",
            state = prefs.getString("state", "") ?: "",
            progress = prefs.getInt("progress", 0),
            startedAt = prefs.getLong("startedAt", 0L),
            message = prefs.getString("message", "") ?: "",
        )
    }

    fun clear() = prefs.edit().clear().apply()
}
