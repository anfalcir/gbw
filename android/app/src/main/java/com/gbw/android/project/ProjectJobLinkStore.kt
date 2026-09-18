package com.gbw.android.project

import android.content.Context
import org.json.JSONObject
import java.io.File

internal class ProjectJobLinkStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "state/project-job-links").apply { mkdirs() }
    fun link(jobId: String, projectId: String) = synchronized(LOCK) {
        require(isCanonicalProjectId(projectId)); file(jobId).writeText(JSONObject().put("jobId",jobId).put("projectId",projectId).toString())
    }
    fun projectId(jobId: String): String? = synchronized(LOCK) {
        val f=file(jobId); if(!f.isFile) null else runCatching { JSONObject(f.readText()).getString("projectId") }.getOrNull()?.takeIf(::isCanonicalProjectId)
    }
    fun remove(jobId: String) = synchronized(LOCK) { file(jobId).delete() }
    private fun file(jobId:String)=File(dir, jobId.replace(Regex("[^A-Za-z0-9_.-]"),"_")+".json")
    private companion object { val LOCK=Any() }
}
