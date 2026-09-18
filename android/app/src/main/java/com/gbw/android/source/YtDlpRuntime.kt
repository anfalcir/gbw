package com.gbw.android.source

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal object YtDlpRuntime {
    private val initialized = AtomicBoolean(false)

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        if (initialized.get()) return@withContext
        synchronized(this@YtDlpRuntime) {
            if (!initialized.get()) {
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized.set(true)
            }
        }
    }

    suspend fun executeJson(
        context: Context,
        target: String,
        options: List<Pair<String, String?>> = emptyList(),
        processId: String,
    ): String = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val request = YoutubeDLRequest(target)
        request.addOption("--ignore-config")
        request.addOption("--no-warnings")
        options.forEach { (name, value) ->
            if (value == null) request.addOption(name) else request.addOption(name, value)
        }
        try {
            YoutubeDL.getInstance().execute(request, processId).out.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw IllegalStateException(
                "Falha no yt-dlp: " + (error.message ?: error::class.java.simpleName),
                error,
            )
        }
    }

    suspend fun execute(
        context: Context,
        target: String,
        options: List<Pair<String, String?>> = emptyList(),
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val request = YoutubeDLRequest(target)
        request.addOption("--ignore-config")
        request.addOption("--no-warnings")
        options.forEach { (name, value) ->
            if (value == null) request.addOption(name) else request.addOption(name, value)
        }
        try {
            YoutubeDL.getInstance().execute(
                request,
                processId,
            ) { progress, eta, line ->
                onProgress(progress, eta, line)
            }.out.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw IllegalStateException(
                "Falha no yt-dlp: " + (error.message ?: error::class.java.simpleName),
                error,
            )
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
}
