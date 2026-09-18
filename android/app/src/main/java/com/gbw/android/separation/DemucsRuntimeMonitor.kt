package com.gbw.android.separation

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class DemucsRuntimeMetrics(
    val peakPssKb: Long,
    val maxThermalStatus: Int,
)

internal class DemucsRuntimeMonitor(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val running = AtomicBoolean(false)
    private val peakPssKb = AtomicLong(Debug.getPss())
    private val maxThermalStatus = AtomicInteger(readThermalStatus())
    private var sampler: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        sampler = Thread({
            while (running.get()) {
                sampleNow()
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                }
            }
        }, "gbw-demucs-runtime-monitor").apply {
            isDaemon = true
            start()
        }
    }

    fun sampleNow() {
        val pss = Debug.getPss()
        peakPssKb.updateAndGet { previous -> maxOf(previous, pss) }
        val thermal = readThermalStatus()
        maxThermalStatus.updateAndGet { previous -> maxOf(previous, thermal) }
    }

    fun snapshot(): DemucsRuntimeMetrics {
        sampleNow()
        return DemucsRuntimeMetrics(peakPssKb.get(), maxThermalStatus.get())
    }

    fun stop(): DemucsRuntimeMetrics {
        running.set(false)
        sampler?.interrupt()
        runCatching { sampler?.join(1_500L) }
        sampler = null
        return snapshot()
    }

    private fun readThermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            THERMAL_UNAVAILABLE
        }

    companion object {
        const val THERMAL_UNAVAILABLE = -1
        private const val SAMPLE_INTERVAL_MS = 750L

        fun thermalLabel(status: Int): String = when (status) {
            THERMAL_UNAVAILABLE -> "indisponível"
            PowerManager.THERMAL_STATUS_NONE -> "normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderado"
            PowerManager.THERMAL_STATUS_SEVERE -> "severo"
            PowerManager.THERMAL_STATUS_CRITICAL -> "crítico"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergência"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "desligamento"
            else -> "status-$status"
        }
    }
}
