package com.gbw.android.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
}
