package com.gbw.android.backup

import android.content.Context

internal class ProjectDeletionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gbw_remote_ignore", Context.MODE_PRIVATE)
    fun ignore(projectId: String) { prefs.edit().putBoolean(projectId, true).commit() }
    fun clear(projectId: String) { prefs.edit().remove(projectId).commit() }
    fun isIgnored(projectId: String): Boolean = prefs.getBoolean(projectId, false)
}
