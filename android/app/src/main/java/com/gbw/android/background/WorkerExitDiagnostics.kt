package com.gbw.android.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import androidx.annotation.RequiresApi

internal object WorkerExitDiagnostics {
    private const val MEDIA_PROCESS_SUFFIX = ":media"
    private const val SUMMARY_LIMIT_BYTES = 120

    @Volatile
    private var lastQueryAt = 0L

    fun markPhase(context: Context, phase: String) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            val summary =
                "phase=${phase.take(56)}|pss=${Debug.getPss()}|avail=${memory.availMem / 1024L}"
            var bytes = summary.toByteArray(Charsets.UTF_8)
            if (bytes.size > SUMMARY_LIMIT_BYTES) {
                bytes = bytes.copyOf(SUMMARY_LIMIT_BYTES)
            }
            manager.setProcessStateSummary(bytes)
        }
    }

    fun reconcile(
        context: Context,
        store: JobStore,
        current: PersistedJob?,
    ): PersistedJob? {
        if (current == null) return null
        if (current.state != "RUNNING" && current.state != "CANCELLING") return current
        // Online source preparation is owned by WorkManager in the app process.
        // Historical exits of the isolated :media process must not invalidate it.
        if (current.type == MediaProcessingService.SOURCE_PREPARE_TYPE) return current
        if (Build.VERSION.SDK_INT < 30) return current

        val now = System.currentTimeMillis()
        if (now - lastQueryAt < 1_000L) return current
        lastQueryAt = now

        val exit = latestWorkerExit(context, current.startedAt) ?: return current
        val message = diagnosticMessage(exit)
        val next =
            current.copy(
                state = "ERROR",
                message = message,
            )
        store.save(next)
        return next
    }

    fun latestSummary(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return "Disponível a partir do Android 11"
        val exit = latestWorkerExit(context, 0L) ?: return null
        return diagnosticMessage(exit)
    }

    internal fun reasonLabel(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash nativo"
            ApplicationExitInfo.REASON_CRASH -> "crash Java/Kotlin"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "memória baixa"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_SIGNALED -> "processo sinalizado"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "encerrado pelo usuário/sistema"
            ApplicationExitInfo.REASON_USER_STOPPED -> "app interrompido pelo usuário"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "processo encerrado por atualização"
            ApplicationExitInfo.REASON_FREEZER -> "encerrado pelo freezer do Android"
            else -> "saída do processo (motivo $reason)"
        }

    @RequiresApi(30)
    private fun latestWorkerExit(context: Context, since: Long): ApplicationExitInfo? {
        val manager = context.getSystemService(ActivityManager::class.java)
        val expectedProcess = context.packageName + MEDIA_PROCESS_SUFFIX
        return manager
            .getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .asSequence()
            .filter { it.processName == expectedProcess }
            .filter { it.timestamp >= since }
            .maxByOrNull { it.timestamp }
    }

    @RequiresApi(30)
    private fun diagnosticMessage(exit: ApplicationExitInfo): String {
        val summary =
            exit.processStateSummary
                ?.let { String(it, Charsets.UTF_8) }
                ?.substringBefore(Char(0))
                ?.takeIf { it.isNotBlank() }
        val phase =
            summary
                ?.split('|')
                ?.firstOrNull { it.startsWith("phase=") }
                ?.removePrefix("phase=")

        val memoryParts = buildList {
            if (exit.pss > 0L) add("PSS ${exit.pss / 1024L} MiB")
            if (exit.rss > 0L) add("RSS ${exit.rss / 1024L} MiB")
        }
        val pieces = buildList {
            add("Worker de mídia encerrado: ${reasonLabel(exit.reason)}")
            phase?.let { add("fase $it") }
            if (exit.status != 0) add("status/sinal ${exit.status}")
            if (memoryParts.isNotEmpty()) add(memoryParts.joinToString(" / "))
            exit.description?.takeIf { it.isNotBlank() }?.let {
                add(it.replace('\n', ' ').take(160))
            }
        }
        return pieces.joinToString(" • ")
    }
}
