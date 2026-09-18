package com.gbw.android.separation

import android.content.Context
import android.net.Uri
import com.gbw.android.audio.FloatWavInfo
import com.gbw.android.audio.FloatWavReader
import com.gbw.android.audio.LocalFfmpeg
import com.gbw.android.audio.SafAudioStager
import com.gbw.android.background.WorkerExitDiagnostics
import java.io.File

internal object BsRoformerAudioIo {
    suspend fun prepareInput(context: Context, inputUri: Uri, output: File): FloatWavInfo {
        val workDir = requireNotNull(output.parentFile) { "Área temporária BS-RoFormer ausente." }
        output.parentFile?.mkdirs()

        WorkerExitDiagnostics.markPhase(context, "bsroformer:audio-stage")
        val staged = SafAudioStager.stage(context, inputUri, workDir, "source-input")
        try {
            WorkerExitDiagnostics.markPhase(context, "bsroformer:audio-ffmpeg")
            val command =
                "-hide_banner -nostdin -y -v error -i ${quote(staged.file.absolutePath)} " +
                    "-map 0:a:0 -vn -ar ${BsRoformerContract.SAMPLE_RATE} " +
                    "-ac ${BsRoformerContract.CHANNELS} -c:a pcm_f32le -f wav " +
                    quote(output.absolutePath)
            LocalFfmpeg.execute(
                command,
                "Falha ao preparar áudio estéreo 44,1 kHz para BS-RoFormer.",
            )

            WorkerExitDiagnostics.markPhase(context, "bsroformer:audio-validate")
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
        } finally {
            staged.file.delete()
        }
    }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
