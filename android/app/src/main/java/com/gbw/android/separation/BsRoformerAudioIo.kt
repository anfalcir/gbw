package com.gbw.android.separation

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.gbw.android.audio.FloatWavInfo
import com.gbw.android.audio.FloatWavReader
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object BsRoformerAudioIo {
    suspend fun prepareInput(context: Context, inputUri: Uri, output: File): FloatWavInfo {
        val input = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        require(input.isNotBlank()) { "Não foi possível abrir a fonte pelo SAF." }
        output.parentFile?.mkdirs()
        val command =
            "-hide_banner -nostdin -y -v error -i ${quote(input)} " +
                "-map 0:a:0 -vn -ar ${BsRoformerContract.SAMPLE_RATE} " +
                "-ac ${BsRoformerContract.CHANNELS} -c:a pcm_f32le -f wav " +
                quote(output.absolutePath)
        execute(command)
        require(output.isFile && output.length() > 44L) {
            "A preparação do áudio para BS-RoFormer não gerou um WAV válido."
        }
        return FloatWavReader(output).use { reader ->
            reader.info.also { info ->
                require(info.sampleRate == BsRoformerContract.SAMPLE_RATE)
                require(info.channels == BsRoformerContract.CHANNELS)
                require(info.frames > 0L) { "Entrada BS-RoFormer preparada está vazia" }
            }
        }
    }

    private suspend fun execute(command: String) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val session = FFmpegKit.executeAsync(command) { completed ->
                if (!continuation.isActive) return@executeAsync
                if (ReturnCode.isSuccess(completed.returnCode)) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Falha ao preparar áudio estéreo 44,1 kHz para BS-RoFormer."
                        )
                    )
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
