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
                    "$errorMessage (${error::class.java.simpleName})",
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
                    "$errorMessage (${error::class.java.simpleName})",
                    error,
                )
            }
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
