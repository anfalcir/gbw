package com.gbw.android.audio

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal seekable IEEE-float WAV reader/writer used by native DSP pipelines. */
data class FloatWavInfo(
    val sampleRate: Int,
    val channels: Int,
    val frames: Long,
    val dataOffset: Long,
    val dataBytes: Long,
)

class FloatWavReader(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    val info: FloatWavInfo = parseHeader(input)
    private var framesRemaining: Long = info.frames

    init {
        input.seek(info.dataOffset)
    }

    fun rewind() = seekFrame(0L)

    fun seekFrame(frame: Long) {
        require(frame in 0L..info.frames) { "WAV frame fora dos limites: $frame / ${info.frames}" }
        val bytesPerFrame = Math.multiplyExact(info.channels.toLong(), 4L)
        val byteOffset = Math.multiplyExact(frame, bytesPerFrame)
        input.seek(Math.addExact(info.dataOffset, byteOffset))
        framesRemaining = info.frames - frame
    }

    fun remainingFrames(): Long = framesRemaining

    fun readBlock(maxFrames: Int): FloatArray {
        require(maxFrames > 0) { "maxFrames must be positive" }
        if (framesRemaining <= 0L) return FloatArray(0)
        val frames = minOf(framesRemaining, maxFrames.toLong()).toInt()
        val sampleCount = Math.multiplyExact(frames, info.channels)
        val byteCount = Math.multiplyExact(sampleCount, 4)
        val bytes = ByteArray(byteCount)
        input.readFully(bytes)
        framesRemaining -= frames.toLong()
        val result = FloatArray(sampleCount)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(result)
        return result
    }

    override fun close() = input.close()

    private fun parseHeader(raf: RandomAccessFile): FloatWavInfo {
        raf.seek(0L)
        val riff = raf.readAscii(4)
        require(riff == "RIFF") { "Pitch temporário deve ser WAV RIFF" }
        raf.readUInt32LE()
        require(raf.readAscii(4) == "WAVE") { "Arquivo temporário não é WAVE" }

        var formatCode: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var blockAlign: Int? = null
        var dataOffset: Long? = null
        var dataBytes: Long? = null

        while (raf.filePointer + 8L <= raf.length()) {
            val id = raf.readAscii(4)
            val size = raf.readUInt32LE()
            val payload = raf.filePointer
            when (id) {
                "fmt " -> {
                    require(size >= 16L) { "Chunk fmt WAV inválido" }
                    var effectiveFormat = raf.readUInt16LE()
                    channels = raf.readUInt16LE()
                    sampleRate = raf.readUInt32LE().toInt()
                    raf.readUInt32LE()
                    blockAlign = raf.readUInt16LE()
                    bitsPerSample = raf.readUInt16LE()
                    var consumed = 16L
                    if (effectiveFormat == 0xFFFE && size >= 40L) {
                        val cbSize = raf.readUInt16LE()
                        consumed += 2L
                        if (cbSize >= 22 && size - consumed >= 22L) {
                            raf.skipBytes(6)
                            effectiveFormat = raf.readUInt16LE()
                            raf.skipBytes(14)
                            consumed += 22L
                        }
                    }
                    if (size > consumed) raf.seek(payload + size)
                    formatCode = effectiveFormat
                }
                "data" -> {
                    dataOffset = payload
                    dataBytes = size
                    raf.seek(payload + size)
                }
                else -> raf.seek(payload + size)
            }
            if ((size and 1L) == 1L && raf.filePointer < raf.length()) raf.seek(raf.filePointer + 1L)
            if (formatCode != null && dataOffset != null) break
        }

        require(formatCode == 3) { "WAV temporário precisa ser IEEE float" }
        require(bitsPerSample == 32) { "WAV temporário precisa ser float32" }
        val ch = requireNotNull(channels).also { require(it in 1..8) { "Quantidade de canais inválida" } }
        val rate = requireNotNull(sampleRate).also { require(it in 8_000..192_000) { "Sample rate inválido" } }
        val align = requireNotNull(blockAlign)
        require(align == ch * 4) { "Block align inesperado para float32" }
        val bytes = requireNotNull(dataBytes)
        require(bytes % align.toLong() == 0L) { "Chunk de áudio WAV truncado" }
        return FloatWavInfo(
            sampleRate = rate,
            channels = ch,
            frames = bytes / align.toLong(),
            dataOffset = requireNotNull(dataOffset),
            dataBytes = bytes,
        )
    }
}

class FloatWavWriter(
    file: File,
    val sampleRate: Int,
    val channels: Int,
) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    var framesWritten: Long = 0L
        private set
    private val writeScratch = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)

    init {
        require(sampleRate in 8_000..192_000)
        require(channels in 1..8)
        output.setLength(0L)
        writeHeader(dataBytes = 0L)
        output.seek(44L)
    }

    fun write(interleaved: FloatArray) = write(interleaved, 0, interleaved.size)

    /** Writes a slice without allocating a second FloatArray or a block-sized byte buffer. */
    fun write(interleaved: FloatArray, offset: Int, sampleCount: Int) {
        require(offset >= 0 && sampleCount >= 0 && offset <= interleaved.size - sampleCount) {
            "Fatia float32 fora dos limites"
        }
        require(sampleCount % channels == 0) { "Bloco de saída não é divisível pelos canais" }
        if (sampleCount == 0) return
        var cursor = offset
        val end = offset + sampleCount
        val scratchSamples = writeScratch.capacity() / 4
        while (cursor < end) {
            val count = minOf(scratchSamples, end - cursor)
            writeScratch.clear()
            repeat(count) { index -> writeScratch.putFloat(interleaved[cursor + index]) }
            output.write(writeScratch.array(), 0, count * 4)
            cursor += count
        }
        framesWritten += sampleCount / channels
    }

    /**
     * Writes caller-owned little-endian float32 bytes without materializing a
     * FloatArray. The source position is not modified.
     */
    fun write(interleavedFloat32: ByteBuffer, frames: Int) {
        require(frames >= 0)
        if (frames == 0) return
        require(interleavedFloat32.order() == ByteOrder.LITTLE_ENDIAN) {
            "WAV float32 bytes must be little-endian"
        }
        val sampleCount = Math.multiplyExact(frames, channels)
        val byteCount = Math.multiplyExact(sampleCount, 4)
        val source = interleavedFloat32.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        require(source.remaining() >= byteCount) {
            "Direct WAV source has " + source.remaining() +
                " bytes; expected at least " + byteCount
        }
        source.limit(source.position() + byteCount)
        while (source.hasRemaining()) {
            output.channel.write(source)
        }
        framesWritten += frames.toLong()
    }

    override fun close() {
        val dataBytes = Math.multiplyExact(Math.multiplyExact(framesWritten, channels.toLong()), 4L)
        require(dataBytes <= 0xffff_ffffL) { "WAV RIFF excede 4 GiB; RF64 ainda não está habilitado" }
        writeHeader(dataBytes)
        output.fd.sync()
        output.close()
    }

    private fun writeHeader(dataBytes: Long) {
        val current = output.filePointer
        output.seek(0L)
        output.writeBytes("RIFF")
        output.writeUInt32LE(36L + dataBytes)
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        output.writeUInt32LE(16L)
        output.writeUInt16LE(3)
        output.writeUInt16LE(channels)
        output.writeUInt32LE(sampleRate.toLong())
        output.writeUInt32LE(sampleRate.toLong() * channels.toLong() * 4L)
        output.writeUInt16LE(channels * 4)
        output.writeUInt16LE(32)
        output.writeBytes("data")
        output.writeUInt32LE(dataBytes)
        if (current > 44L) output.seek(current)
    }
}

private fun RandomAccessFile.readAscii(count: Int): String {
    val bytes = ByteArray(count)
    readFully(bytes)
    return String(bytes, Charsets.US_ASCII)
}

private fun RandomAccessFile.readUInt16LE(): Int {
    val a = read()
    val b = read()
    if (a < 0 || b < 0) throw EOFException()
    return a or (b shl 8)
}

private fun RandomAccessFile.readUInt32LE(): Long {
    val a = read(); val b = read(); val c = read(); val d = read()
    if (a < 0 || b < 0 || c < 0 || d < 0) throw EOFException()
    return (a.toLong() or (b.toLong() shl 8) or (c.toLong() shl 16) or (d.toLong() shl 24)) and 0xffff_ffffL
}

private fun RandomAccessFile.writeUInt16LE(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun RandomAccessFile.writeUInt32LE(value: Long) {
    require(value in 0L..0xffff_ffffL)
    write((value and 0xff).toInt())
    write(((value ushr 8) and 0xff).toInt())
    write(((value ushr 16) and 0xff).toInt())
    write(((value ushr 24) and 0xff).toInt())
}
