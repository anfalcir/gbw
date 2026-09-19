package com.gbw.android.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

class FloatWavFileTest {
    @Test
    fun `float wav round trip preserves sample rate channels frames and samples`() {
        val file = File.createTempFile("gbw-f32-", ".wav")
        try {
            val rate = 48_000
            val channels = 2
            val frames = 8_192
            val samples = FloatArray(frames * channels) { index ->
                val frame = index / channels
                val channel = index % channels
                val hz = if (channel == 0) 440.0 else 880.0
                (0.4 * sin(2.0 * PI * hz * frame / rate)).toFloat()
            }
            FloatWavWriter(file, rate, channels).use { writer ->
                writer.write(samples.copyOfRange(0, 5_000 * channels))
                writer.write(samples.copyOfRange(5_000 * channels, samples.size))
                assertEquals(frames.toLong(), writer.framesWritten)
            }

            FloatWavReader(file).use { reader ->
                assertEquals(rate, reader.info.sampleRate)
                assertEquals(channels, reader.info.channels)
                assertEquals(frames.toLong(), reader.info.frames)
                val first = reader.readBlock(4_096)
                val second = reader.readBlock(4_096)
                assertTrue(reader.readBlock(4_096).isEmpty())
                assertArrayEquals(samples.copyOfRange(0, first.size), first, 0f)
                assertArrayEquals(samples.copyOfRange(first.size, samples.size), second, 0f)
                reader.rewind()
                assertEquals(frames.toLong(), reader.remainingFrames())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reader can seek to exact frame for bounded dsp windows`() {
        val file = File.createTempFile("gbw-f32-seek-", ".wav")
        try {
            val channels = 2
            val frames = 1_024
            val samples = FloatArray(frames * channels) { it.toFloat() / 10_000f }
            FloatWavWriter(file, 44_100, channels).use { it.write(samples) }

            FloatWavReader(file).use { reader ->
                reader.seekFrame(333L)
                assertEquals((frames - 333).toLong(), reader.remainingFrames())
                val block = reader.readBlock(17)
                assertArrayEquals(
                    samples.copyOfRange(333 * channels, (333 + 17) * channels),
                    block,
                    0f,
                )
                reader.seekFrame(frames.toLong())
                assertTrue(reader.readBlock(1).isEmpty())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `writer slice avoids caller-side core copies and preserves exact samples`() {
        val file = File.createTempFile("gbw-f32-slice-", ".wav")
        try {
            val source = FloatArray(512) { index -> (index - 256) / 512f }
            FloatWavWriter(file, 44_100, 2).use { writer ->
                writer.write(source, 100, 240)
                assertEquals(120L, writer.framesWritten)
            }
            FloatWavReader(file).use { reader ->
                assertEquals(120L, reader.info.frames)
                assertArrayEquals(source.copyOfRange(100, 340), reader.readBlock(120), 0f)
            }
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reader rejects non wav input`() {
        val file = File.createTempFile("gbw-bad-", ".wav")
        try {
            file.writeText("not a wave file")
            FloatWavReader(file).close()
        } finally {
            file.delete()
        }
    }

    @Test(expected = EOFException::class)
    fun `reader rejects truncated declared audio payload`() {
        val file = File.createTempFile("gbw-truncated-", ".wav")
        try {
            FloatWavWriter(file, 44_100, 2).use { writer ->
                writer.write(FloatArray(256))
            }
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(file.length() - 16L)
            }
            FloatWavReader(file).use { reader ->
                reader.readBlock(128)
            }
        } finally {
            file.delete()
        }
    }

}
