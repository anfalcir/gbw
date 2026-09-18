package com.gbw.android.source

import android.content.Context
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PreparedSourceRecord(
    val jobId: String,
    val sourceUrl: String,
    val title: String,
    val nativePath: String,
    val preparedPath: String,
    val formatId: String,
    val durationSeconds: Double,
) {
    fun preparedFile(): File = File(preparedPath)
}

class PreparedSourceStore(context: Context) {
    private val appContext = context.applicationContext
    private val stateDir = File(appContext.filesDir, "state").apply { mkdirs() }
    private val file = File(stateDir, "prepared_source.json")
    private val temp = File(stateDir, "prepared_source.json.tmp")

    fun save(result: PreparedOnlineSource) {
        val json =
            JSONObject()
                .put("jobId", result.jobId)
                .put("sourceUrl", result.sourceUrl)
                .put("title", result.title)
                .put("nativePath", result.nativeFile.absolutePath)
                .put("preparedPath", result.preparedFile.absolutePath)
                .put("formatId", result.formatId)
                .put("durationSeconds", result.durationSeconds)
                .toString()
                .toByteArray(Charsets.UTF_8)
        synchronized(LOCK) {
            FileOutputStream(temp).use {
                it.write(json)
                it.flush()
                it.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun load(): PreparedSourceRecord? = synchronized(LOCK) {
        if (!file.isFile) return@synchronized null
        runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            PreparedSourceRecord(
                jobId = json.getString("jobId"),
                sourceUrl = json.optString("sourceUrl"),
                title = json.optString("title"),
                nativePath = json.optString("nativePath"),
                preparedPath = json.getString("preparedPath"),
                formatId = json.optString("formatId"),
                durationSeconds = json.optDouble("durationSeconds", 0.0),
            )
        }.getOrNull()
    }

    fun contentUri(record: PreparedSourceRecord) =
        FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".files",
            record.preparedFile(),
        )

    companion object {
        private val LOCK = Any()
    }
}
