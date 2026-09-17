package com.gbw.android.audio

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.OutputFormat
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class EncodedAudioProbe(
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Double,
    val codec: String,
    val bitDepth: Int?,
)

internal object FfmpegPitchIo {
    suspend fun prepareFloatWav(
        context: Context,
        inputUri: Uri,
        inspection: AudioInspection,
        output: File,
    ) {
        ensureWorkingSpace(context, inspection)
        val input = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        require(input.isNotBlank()) { "Não foi possível abrir o arquivo de entrada pelo SAF." }
        output.parentFile?.mkdirs()
        val canRemux = inspection.format.equals("WAV", true) &&
            inspection.codec.equals("pcm_f32le", true) && inspection.isFloat && inspection.bitDepth == 32
        val codecArgs = if (canRemux) "-c:a copy" else "-c:a pcm_f32le"
        val command = "-hide_banner -nostdin -y -v error -i ${quote(input)} " +
            "-map 0:a:0 -vn $codecArgs -f wav ${quote(output.absolutePath)}"
        execute(command, "Falha ao preparar o áudio em WAV float32.")
        require(output.isFile && output.length() > 44L) { "A preparação de áudio não gerou um WAV válido." }
    }

    suspend fun encodeOutput(inputFloatWav: File, output: File, format: OutputFormat) {
        output.parentFile?.mkdirs()
        val codecArgs = when (format) {
            OutputFormat.WAV_FLOAT32 -> error("WAV float32 não requer recodificação")
            OutputFormat.WAV_24 -> "-c:a pcm_s24le -f wav"
            OutputFormat.FLAC_24 -> "-c:a flac -sample_fmt s32 -bits_per_raw_sample 24 -f flac"
        }
        val command = "-hide_banner -nostdin -y -v error -i ${quote(inputFloatWav.absolutePath)} " +
            "-map 0:a:0 -vn $codecArgs ${quote(output.absolutePath)}"
        execute(command, "Falha ao gerar o formato final selecionado.")
        require(output.isFile && output.length() > 0L) { "A conversão final não gerou arquivo." }
    }

    suspend fun probe(file: File): EncodedAudioProbe = suspendCancellableCoroutine { cont ->
        val command = "-v error -select_streams a:0 " +
            "-show_entries stream=codec_name,sample_rate,channels,bits_per_sample,bits_per_raw_sample:" +
            "format=duration -of json ${quote(file.absolutePath)}"
        val session = FFprobeKit.executeAsync(command) { completed ->
            if (!cont.isActive) return@executeAsync
            if (!ReturnCode.isSuccess(completed.returnCode)) {
                cont.resumeWithException(IllegalStateException("Falha ao validar o arquivo renderizado."))
                return@executeAsync
            }
            try {
                val json = JSONObject(completed.output ?: "{}")
                val stream = json.optJSONArray("streams")?.optJSONObject(0)
                    ?: error("Saída sem stream de áudio")
                val format = json.optJSONObject("format") ?: JSONObject()
                val rate = stream.optString("sample_rate").toIntOrNull() ?: error("Sample rate ausente")
                val channels = stream.optInt("channels", 0).takeIf { it > 0 } ?: error("Canais ausentes")
                val duration = format.optString("duration").toDoubleOrNull() ?: error("Duração ausente")
                val bits = stream.optString("bits_per_raw_sample").toIntOrNull()?.takeIf { it > 0 }
                    ?: stream.optInt("bits_per_sample", 0).takeIf { it > 0 }
                cont.resume(
                    EncodedAudioProbe(
                        sampleRate = rate,
                        channels = channels,
                        durationSeconds = duration,
                        codec = stream.optString("codec_name", ""),
                        bitDepth = bits,
                    )
                )
            } catch (e: Exception) {
                cont.resumeWithException(IllegalStateException("Metadados inválidos no arquivo renderizado.", e))
            }
        }
        cont.invokeOnCancellation { session.cancel() }
    }

    private fun ensureWorkingSpace(context: Context, inspection: AudioInspection) {
        val rate = inspection.sampleRate ?: return
        val channels = inspection.channels ?: return
        // Use the three-copy case as a conservative preflight even when the selected final output is float32.
        val required = FilePitchStorageBudget.requiredBytes(
            durationSeconds = inspection.durationSeconds,
            sampleRate = rate,
            channels = channels,
            outputFormat = OutputFormat.WAV_24,
        ) ?: return
        val available = StatFs(context.cacheDir.absolutePath).availableBytes
        require(available >= required) {
            val requiredMiB = required / (1024L * 1024L)
            val availableMiB = available / (1024L * 1024L)
            "Espaço temporário insuficiente: são necessários cerca de ${requiredMiB} MiB; disponíveis ${availableMiB} MiB."
        }
    }

    private suspend fun execute(command: String, errorMessage: String) = suspendCancellableCoroutine<Unit> { cont ->
        val session = FFmpegKit.executeAsync(command) { completed ->
            if (!cont.isActive) return@executeAsync
            if (ReturnCode.isSuccess(completed.returnCode)) cont.resume(Unit)
            else cont.resumeWithException(IllegalStateException(errorMessage))
        }
        cont.invokeOnCancellation { session.cancel() }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
