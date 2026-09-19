package com.gbw.android.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class BackupConflictStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("gbw_backup_conflicts", Context.MODE_PRIVATE)

    fun load(): List<BackupConflict> {
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            buildList {
                for (i in 0 until array.length()) {
                    val j = array.getJSONObject(i)
                    add(
                        BackupConflict(
                            projectId = j.getString("projectId"),
                            name = j.optString("name", "Projeto"),
                            localRevisionId = j.getString("localRevisionId"),
                            remoteRevisionId = j.getString("remoteRevisionId"),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(conflicts: List<BackupConflict>) {
        val array = JSONArray()
        conflicts.distinctBy { it.projectId }.forEach { c ->
            array.put(
                JSONObject()
                    .put("projectId", c.projectId)
                    .put("name", c.name)
                    .put("localRevisionId", c.localRevisionId)
                    .put("remoteRevisionId", c.remoteRevisionId)
            )
        }
        prefs.edit().putString(KEY, array.toString()).commit()
    }

    fun remove(projectId: String) = save(load().filterNot { it.projectId == projectId })
    fun clear() = prefs.edit().remove(KEY).commit()

    private companion object { const val KEY = "conflicts" }
}
