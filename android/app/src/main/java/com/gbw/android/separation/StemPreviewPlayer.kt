package com.gbw.android.separation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.gbw.android.audio.FloatWavReader
import java.io.File

internal class StemPreviewPlayer {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null

    fun play(file: File, onFinished: () -> Unit, onError: (String) -> Unit) {
        stop()
        val token = synchronized(lock) { generation += 1L; generation }
        val thread = Thread({
            var localTrack: AudioTrack? = null
            try {
                FloatWavReader(file).use { reader ->
                    val info = reader.info
                    require(info.channels == 2)
                    require(info.sampleRate == 44_100)
                    val minBytes = AudioTrack.getMinBufferSize(
                        info.sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_FLOAT,
                    )
                    check(minBytes > 0)
                    localTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(info.sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(maxOf(minBytes, 64 * 1024))
                        .build()
                    synchronized(lock) {
                        if (generation != token) throw InterruptedException("Prévia substituída")
                        audioTrack = localTrack
                    }
                    localTrack?.play()
                    val framesPerRead = maxOf(1_024, minBytes / (info.channels * 4))
                    while (isCurrent(token)) {
                        val block = reader.readBlock(framesPerRead)
                        if (block.isEmpty()) break
                        var offset = 0
                        while (offset < block.size && isCurrent(token)) {
                            val written = localTrack?.write(
                                block, offset, block.size - offset, AudioTrack.WRITE_BLOCKING
                            ) ?: AudioTrack.ERROR_INVALID_OPERATION
                            check(written > 0)
                            offset += written
                        }
                    }
                }
                if (isCurrent(token)) mainHandler.post(onFinished)
            } catch (error: Exception) {
                if (isCurrent(token)) mainHandler.post {
                    onError(error.message ?: "Falha inesperada na prévia do stem")
                }
            } finally {
                synchronized(lock) { if (generation == token) audioTrack = null }
                runCatching { localTrack?.stop() }
                runCatching { localTrack?.release() }
            }
        }, "gbw-stem-preview")
        synchronized(lock) { worker = thread }
        thread.start()
    }

    fun stop() {
        val track: AudioTrack?
        val thread: Thread?
        synchronized(lock) {
            generation += 1L
            track = audioTrack
            audioTrack = null
            thread = worker
            worker = null
        }
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        thread?.interrupt()
    }

    fun release() = stop()

    private fun isCurrent(token: Long): Boolean = synchronized(lock) { generation == token }
}
