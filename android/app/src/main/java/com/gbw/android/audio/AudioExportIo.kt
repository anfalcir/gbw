package com.gbw.android.audio

import com.gbw.android.domain.OutputFormat
import org.json.JSONObject
import java.io.File

internal data class EncodedAudioProbe(
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Double,
    val codec: String,
    val bitDepth: Int?,
)

internal object AudioExportIo {
    suspend fun encodeOutput(inputFloatWav: File, output: File, format: OutputFormat) {
        output.parentFile?.mkdirs()
        val codecArgs = when (format) {
            OutputFormat.WAV_FLOAT32 -> error("WAV float32 não requer recodificação")
            OutputFormat.WAV_24 -> "-c:a pcm_s24le -f wav"
            OutputFormat.FLAC_24 -> "-c:a flac -sample_fmt s32 -bits_per_raw_sample 24 -f flac"
        }
        val command =
            "-hide_banner -nostdin -y -v error -i ${quote(inputFloatWav.absolutePath)} " +
                "-map 0:a:0 -vn $codecArgs ${quote(output.absolutePath)}"
        LocalFfmpeg.execute(command, "Falha ao gerar o formato final selecionado.")
        require(output.isFile && output.length() > 0L) {
            "A conversão final não gerou arquivo."
        }
    }

    suspend fun probe(file: File): EncodedAudioProbe {
        val command =
            "-v error -select_streams a:0 " +
                "-show_entries stream=codec_name,sample_rate,channels,bits_per_sample,bits_per_raw_sample:" +
                "format=duration -of json ${quote(file.absolutePath)}"
        val output = LocalFfmpeg.probe(command, "Falha ao validar o arquivo renderizado.")
        try {
            val json = JSONObject(output.ifBlank { "{}" })
            val stream =
                json.optJSONArray("streams")?.optJSONObject(0)
                    ?: error("Saída sem stream de áudio")
            val format = json.optJSONObject("format") ?: JSONObject()
            val rate =
                stream.optString("sample_rate").toIntOrNull()
                    ?: error("Sample rate ausente")
            val channels =
                stream.optInt("channels", 0).takeIf { it > 0 }
                    ?: error("Canais ausentes")
            val duration =
                format.optString("duration").toDoubleOrNull()
                    ?: error("Duração ausente")
            val bits =
                stream.optString("bits_per_raw_sample").toIntOrNull()?.takeIf { it > 0 }
                    ?: stream.optInt("bits_per_sample", 0).takeIf { it > 0 }
            return EncodedAudioProbe(
                sampleRate = rate,
                channels = channels,
                durationSeconds = duration,
                codec = stream.optString("codec_name", ""),
                bitDepth = bits,
            )
        } catch (error: Exception) {
            throw IllegalStateException("Metadados inválidos no arquivo renderizado.", error)
        }
    }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
