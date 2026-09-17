package com.gbw.android.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.AudioQualityRules
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.log10

object WavInspector {
    private data class Fmt(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val blockAlign: Int,
        val effectiveFormat: Int,
    )

    fun inspect(context: Context, uri: Uri): AudioInspection {
        val resolver = context.contentResolver
        val name = displayName(resolver, uri)
        resolver.openInputStream(uri)?.use { raw ->
            val input = BufferedInputStream(raw, 128 * 1024)
            val riff = input.readAscii(4)
            if (riff != "RIFF" && riff != "RF64") throw IllegalArgumentException("Arquivo WAV inválido: cabeçalho RIFF ausente.")
            input.readUInt32LE() // RIFF size
            if (input.readAscii(4) != "WAVE") throw IllegalArgumentException("Arquivo inválido: WAVE ausente.")

            var fmt: Fmt? = null
            var dataBytes: Long? = null
            var peak = 0.0
            var scannedData = false

            while (true) {
                val chunkId = input.readAsciiOrNull(4) ?: break
                val chunkSize = input.readUInt32LE()
                when (chunkId) {
                    "fmt " -> {
                        if (chunkSize < 16) throw IllegalArgumentException("Chunk fmt inválido.")
                        val audioFormat = input.readUInt16LE()
                        val channels = input.readUInt16LE()
                        val sampleRate = input.readUInt32LE().toInt()
                        input.readUInt32LE() // byte rate
                        val blockAlign = input.readUInt16LE()
                        val bitsPerSample = input.readUInt16LE()
                        var effectiveFormat = audioFormat
                        var consumed = 16L
                        if (chunkSize > consumed) {
                            val extraSize = if (chunkSize - consumed >= 2) input.readUInt16LE().also { consumed += 2 } else 0
                            if (audioFormat == 0xFFFE && extraSize >= 22 && chunkSize - consumed >= 22) {
                                input.skipFully(6) // valid bits + channel mask
                                val subFormat = input.readUInt16LE()
                                effectiveFormat = subFormat
                                input.skipFully(14)
                                consumed += 22
                            }
                            if (chunkSize > consumed) input.skipFully(chunkSize - consumed)
                        }
                        if ((chunkSize and 1L) == 1L) input.skipFully(1)
                        fmt = Fmt(audioFormat, channels, sampleRate, bitsPerSample, blockAlign, effectiveFormat)
                    }
                    "data" -> {
                        dataBytes = chunkSize
                        val currentFmt = fmt
                        if (currentFmt == null) {
                            input.skipFully(chunkSize)
                        } else {
                            peak = scanPeak(input, chunkSize, currentFmt)
                            scannedData = true
                        }
                        if ((chunkSize and 1L) == 1L) input.skipFully(1)
                    }
                    else -> {
                        input.skipFully(chunkSize)
                        if ((chunkSize and 1L) == 1L) input.skipFully(1)
                    }
                }
                if (fmt != null && scannedData) break
            }

            val f = fmt ?: throw IllegalArgumentException("Arquivo WAV sem informações de formato.")
            val duration = if (dataBytes != null && f.blockAlign > 0 && f.sampleRate > 0) {
                dataBytes!!.toDouble() / f.blockAlign.toDouble() / f.sampleRate.toDouble()
            } else null
            val isFloat = f.effectiveFormat == 3
            val isPcm = f.effectiveFormat == 1
            if (!isPcm && !isFloat) throw IllegalArgumentException("WAV usa um codec interno ainda não suportado pelo inspetor nativo.")
            val peakDbfs = if (scannedData && peak > 0.0) 20.0 * log10(peak.coerceAtMost(1.0)) else if (scannedData) -999.0 else null
            return AudioQualityRules.classify(
                displayName = name,
                format = "WAV",
                codec = if (isFloat) "pcm_f${f.bitsPerSample}le" else "pcm_s${f.bitsPerSample}le",
                sampleRate = f.sampleRate,
                channels = f.channels,
                bitDepth = f.bitsPerSample,
                isFloat = isFloat,
                isLossless = true,
                durationSeconds = duration,
                peakDbfs = peakDbfs,
            )
        }
        throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
    }

    private fun scanPeak(input: InputStream, bytes: Long, fmt: Fmt): Double {
        var remaining = bytes
        var peak = 0.0
        val sampleBytes = fmt.bitsPerSample / 8
        if (sampleBytes !in setOf(1, 2, 3, 4)) {
            input.skipFully(bytes)
            return peak
        }
        val sample = ByteArray(sampleBytes)
        while (remaining >= sampleBytes) {
            input.readFully(sample)
            remaining -= sampleBytes
            val normalized = when {
                fmt.effectiveFormat == 3 && fmt.bitsPerSample == 32 -> {
                    val bits = (sample[0].toInt() and 0xff) or
                        ((sample[1].toInt() and 0xff) shl 8) or
                        ((sample[2].toInt() and 0xff) shl 16) or
                        ((sample[3].toInt() and 0xff) shl 24)
                    abs(Float.fromBits(bits).toDouble()).takeIf { it.isFinite() } ?: 0.0
                }
                fmt.effectiveFormat == 1 && fmt.bitsPerSample == 8 -> abs(((sample[0].toInt() and 0xff) - 128) / 128.0)
                fmt.effectiveFormat == 1 && fmt.bitsPerSample == 16 -> {
                    val value = ((sample[0].toInt() and 0xff) or (sample[1].toInt() shl 8)).toShort().toInt()
                    abs(value / 32768.0)
                }
                fmt.effectiveFormat == 1 && fmt.bitsPerSample == 24 -> {
                    var value = (sample[0].toInt() and 0xff) or
                        ((sample[1].toInt() and 0xff) shl 8) or
                        ((sample[2].toInt() and 0xff) shl 16)
                    if ((value and 0x800000) != 0) value = value or -0x1000000
                    abs(value / 8388608.0)
                }
                fmt.effectiveFormat == 1 && fmt.bitsPerSample == 32 -> {
                    val value = (sample[0].toInt() and 0xff) or
                        ((sample[1].toInt() and 0xff) shl 8) or
                        ((sample[2].toInt() and 0xff) shl 16) or
                        (sample[3].toInt() shl 24)
                    abs(value / 2147483648.0)
                }
                else -> 0.0
            }
            if (normalized > peak) peak = normalized
        }
        if (remaining > 0) input.skipFully(remaining)
        return peak
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "audio.wav"
        }
        return uri.lastPathSegment ?: "audio.wav"
    }

    private fun InputStream.readAscii(n: Int): String = String(ByteArray(n).also { readFully(it) }, Charsets.US_ASCII)
    private fun InputStream.readAsciiOrNull(n: Int): String? {
        val data = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(data, off, n - off)
            if (r < 0) return if (off == 0) null else throw EOFException()
            off += r
        }
        return String(data, Charsets.US_ASCII)
    }
    private fun InputStream.readUInt16LE(): Int {
        val a = read(); val b = read()
        if (a < 0 || b < 0) throw EOFException()
        return a or (b shl 8)
    }
    private fun InputStream.readUInt32LE(): Long {
        val a = read(); val b = read(); val c = read(); val d = read()
        if (a < 0 || b < 0 || c < 0 || d < 0) throw EOFException()
        return (a.toLong() or (b.toLong() shl 8) or (c.toLong() shl 16) or (d.toLong() shl 24)) and 0xffffffffL
    }
    private fun InputStream.readFully(buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val r = read(buffer, off, buffer.size - off)
            if (r < 0) throw EOFException()
            off += r
        }
    }
    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else {
                if (read() < 0) throw EOFException()
                remaining--
            }
        }
    }
}
