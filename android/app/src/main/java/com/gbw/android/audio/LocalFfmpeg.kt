package com.gbw.android.audio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LocalFfmpeg {
    suspend fun execute(command: String, errorMessage: String) =
        withContext(Dispatchers.IO) {
            try {
                val session = FFmpegKit.execute(command)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    val detail = compactDetail(session.output)
                    throw IllegalStateException(
                        if (detail == null) errorMessage else "$errorMessage • $detail"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                throw error
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "$errorMessage • ${throwableDetail(error)}",
                    error,
                )
            }
        }

    suspend fun probe(command: String, errorMessage: String): String =
        withContext(Dispatchers.IO) {
            try {
                val session = FFprobeKit.execute(command)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    val detail = compactDetail(session.output)
                    throw IllegalStateException(
                        if (detail == null) errorMessage else "$errorMessage • $detail"
                    )
                }
                session.output ?: ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                throw error
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "$errorMessage • ${throwableDetail(error)}",
                    error,
                )
            }
        }

    internal fun throwableDetail(error: Throwable, maxDepth: Int = 4): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < maxDepth.coerceAtLeast(1)) {
            val type = current::class.java.simpleName.ifBlank { current::class.java.name }
            val message = current.message?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() }
            val item = if (message == null) type else "$type: $message"
            if (parts.lastOrNull() != item) parts += item
            current = current.cause
            depth += 1
        }
        return parts.joinToString(" <- ").take(520)
    }

    internal fun compactDetail(output: String?, maxChars: Int = 320): String? {
        val compact =
            output
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toList()
                ?.takeLast(4)
                ?.joinToString(" | ")
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return compact.takeLast(maxChars.coerceAtLeast(40))
    }
}
