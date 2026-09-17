package com.gbw.android.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gbw.android.domain.AudioInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioInspectionDispatcher {
    suspend fun inspect(context: Context, uri: Uri): AudioInspection {
        val name = displayName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext == "wav") {
            withContext(Dispatchers.IO) { WavInspector.inspect(context, uri) }
        } else {
            FfmpegAudioInspector.inspect(context, uri)
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "audio"
        }
        return uri.lastPathSegment ?: "audio"
    }
}
