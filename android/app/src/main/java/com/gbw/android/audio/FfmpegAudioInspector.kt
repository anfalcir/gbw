package com.gbw.android.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.AudioQualityRules
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FfmpegAudioInspector {
    suspend fun inspect(context: Context, uri: Uri): AudioInspection {
        val input = FFmpegKitConfig.getSafParameterForRead(context, uri)
        if (input.isBlank()) throw IllegalArgumentException("Não foi possível abrir o arquivo pelo seletor do Android.")
        val name = displayName(context, uri)
        val json = probe(input)
        val stream = json.optJSONArray("streams")?.optJSONObject(0)
            ?: throw IllegalArgumentException("O arquivo não possui uma faixa de áudio válida.")
        val format = json.optJSONObject("format") ?: JSONObject()

        val codec = stream.optString("codec_name", "").lowercase()
        val sampleFmt = stream.optString("sample_fmt", "").lowercase()
        val sampleRate = stream.optString("sample_rate", "0").toIntOrNull()?.takeIf { it > 0 }
        val channels = stream.optInt("channels", 0).takeIf { it > 0 }
        val rawBits = stream.optString("bits_per_raw_sample", "").toIntOrNull()?.takeIf { it > 0 }
            ?: stream.optInt("bits_per_sample", 0).takeIf { it > 0 }
        val isFloat = sampleFmt.startsWith("flt") || sampleFmt.startsWith("dbl")
        val bitDepth = rawBits ?: inferBitDepth(sampleFmt)
        val duration = format.optString("duration", "").toDoubleOrNull()
        val formatName = format.optString("format_name", "")
        val ext = name.substringAfterLast('.', "").uppercase()
        val lossless = codec.startsWith("pcm_") || codec in LOSSLESS_CODECS
        val peak = measurePeak(input)

        return AudioQualityRules.classify(
            displayName = name,
            format = ext.ifBlank { formatName.substringBefore(',').uppercase() },
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            isFloat = isFloat,
            isLossless = lossless,
            durationSeconds = duration,
            peakDbfs = peak,
        )
    }

    private suspend fun probe(input: String): JSONObject = suspendCancellableCoroutine { cont ->
        val command = "-v error -select_streams a:0 " +
            "-show_entries stream=codec_name,sample_fmt,sample_rate,channels,channel_layout,bits_per_sample,bits_per_raw_sample,bit_rate:" +
            "format=format_name,duration,bit_rate -of json ${quote(input)}"
        val session = FFprobeKit.executeAsync(command) { completed ->
            if (!cont.isActive) return@executeAsync
            if (ReturnCode.isSuccess(completed.returnCode)) {
                try { cont.resume(JSONObject(completed.output ?: "{}")) }
                catch (e: Exception) { cont.resumeWithException(IllegalArgumentException("Não foi possível interpretar os dados do áudio.", e)) }
            } else {
                cont.resumeWithException(IllegalArgumentException("Não foi possível analisar o arquivo de áudio."))
            }
        }
        cont.invokeOnCancellation { session.cancel() }
    }

    private suspend fun measurePeak(input: String): Double? = suspendCancellableCoroutine { cont ->
        val command = "-hide_banner -nostats -v info -xerror -i ${quote(input)} -map 0:a:0 -af volumedetect -f null -"
        val session = FFmpegKit.executeAsync(command) { completed ->
            if (!cont.isActive) return@executeAsync
            if (!ReturnCode.isSuccess(completed.returnCode)) {
                cont.resumeWithException(IllegalArgumentException("O arquivo apresentou erro durante a decodificação de integridade."))
                return@executeAsync
            }
            val text = completed.output ?: ""
            val match = Regex("max_volume:\\s*(-?(?:\\d+(?:\\.\\d+)?|inf))\\s*dB", RegexOption.IGNORE_CASE)
                .findAll(text).lastOrNull()?.groupValues?.getOrNull(1)
            val peak = when {
                match == null -> null
                match.equals("-inf", true) -> -999.0
                else -> match.toDoubleOrNull()
            }
            cont.resume(peak)
        }
        cont.invokeOnCancellation { session.cancel() }
    }

    private fun inferBitDepth(sampleFmt: String): Int? = when {
        sampleFmt.startsWith("u8") -> 8
        sampleFmt.startsWith("s16") -> 16
        sampleFmt.startsWith("s32") -> 32
        sampleFmt.startsWith("s64") -> 64
        sampleFmt.startsWith("flt") -> 32
        sampleFmt.startsWith("dbl") -> 64
        else -> null
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "audio"
        }
        return uri.lastPathSegment ?: "audio"
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private val LOSSLESS_CODECS = setOf(
        "pcm_s8", "pcm_u8", "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be",
        "pcm_s32le", "pcm_s32be", "pcm_f32le", "pcm_f32be", "pcm_f64le", "pcm_f64be",
        "flac", "alac", "wavpack", "ape"
    )
}
