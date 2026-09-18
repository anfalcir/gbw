package com.gbw.android.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.AudioQualityRules

internal object PlatformAudioInspector {
    fun inspect(context: Context, uri: Uri, displayName: String): AudioInspection {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
                if (asset.declaredLength >= 0L) {
                    extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.declaredLength)
                } else {
                    extractor.setDataSource(asset.fileDescriptor)
                }
            } ?: throw IllegalArgumentException("O Android não conseguiu abrir o arquivo selecionado.")

            val format = findAudioTrack(extractor)
                ?: throw IllegalArgumentException("O arquivo não possui uma faixa de áudio reconhecida pelo Android.")

            val mime = stringValue(format, MediaFormat.KEY_MIME).orEmpty().lowercase()
            val sampleRate = intValue(format, MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 }
            val channels = intValue(format, MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it > 0 }
            val durationUs = longValue(format, MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
            val pcmEncoding = intValue(format, MediaFormat.KEY_PCM_ENCODING)
            val explicitBits = intValue(format, "bits-per-sample")?.takeIf { it > 0 }
            val isFloat = pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
            val bitDepth = explicitBits ?: when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_FLOAT -> 32
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                AudioFormat.ENCODING_PCM_32BIT -> 32
                else -> null
            }
            val extension = displayName.substringAfterLast('.', "").uppercase()
            val container = extension.ifBlank { mime.substringAfter('/', "AUDIO").uppercase() }

            val result = AudioQualityRules.classify(
                displayName = displayName,
                format = container,
                codec = mime,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                isFloat = isFloat,
                isLossless = isLosslessMime(mime),
                durationSeconds = durationUs?.div(1_000_000.0),
                peakDbfs = null,
            )
            return result.copy(notes = result.notes + PLATFORM_NOTE)
        } catch (error: SecurityException) {
            throw IllegalArgumentException(
                "O Android perdeu acesso ao arquivo selecionado. Selecione-o novamente.",
                error,
            )
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Throwable) {
            throw IllegalArgumentException(
                "Não foi possível inspecionar este áudio com o extrator seguro do Android.",
                error,
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    internal fun isLosslessMime(mime: String): Boolean {
        val normalized = mime.lowercase()
        return normalized in setOf(
            "audio/flac",
            "audio/alac",
            "audio/raw",
            "audio/wav",
            "audio/x-wav",
        ) || normalized.contains("lossless")
    }

    private fun findAudioTrack(extractor: MediaExtractor): MediaFormat? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = stringValue(format, MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/", ignoreCase = true)) return format
        }
        return null
    }

    private fun stringValue(format: MediaFormat, key: String): String? =
        if (format.containsKey(key)) runCatching { format.getString(key) }.getOrNull() else null

    private fun intValue(format: MediaFormat, key: String): Int? =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrNull() else null

    private fun longValue(format: MediaFormat, key: String): Long? =
        if (format.containsKey(key)) runCatching { format.getLong(key) }.getOrNull() else null

    private const val PLATFORM_NOTE =
        "Inspeção inicial feita pelo extrator nativo do Android; a decodificação completa é validada no processamento."
}
