package com.gbw.android.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.gbw.android.MainActivity
import com.gbw.android.R
import com.gbw.android.audio.FilePitchRenderRequest
import com.gbw.android.audio.FilePitchRenderer
import com.gbw.android.domain.AudioKind
import com.gbw.android.domain.OutputFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MediaProcessingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var store: JobStore

    override fun onCreate() {
        super.onCreate()
        store = JobStore(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_CANCEL -> cancelCurrent("Cancelado pelo usuário")
            ACTION_SELF_TEST -> startSelfTest()
            ACTION_FILE_PITCH -> startFilePitch(intent, flags)
        }
        return if (action == ACTION_FILE_PITCH) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun startFilePitch(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val input = intent.getStringExtra(EXTRA_INPUT_URI)?.let(Uri::parse)
        val output = intent.getStringExtra(EXTRA_OUTPUT_URI)?.let(Uri::parse)
        val semitones = intent.getIntExtra(EXTRA_SEMITONES, Int.MIN_VALUE)
        val audioKind = intent.getStringExtra(EXTRA_AUDIO_KIND)?.let {
            runCatching { AudioKind.valueOf(it) }.getOrNull()
        }
        val outputFormat = intent.getStringExtra(EXTRA_OUTPUT_FORMAT)?.let {
            runCatching { OutputFormat.valueOf(it) }.getOrNull()
        }
        if (input == null || output == null || semitones == Int.MIN_VALUE || audioKind == null || outputFormat == null) {
            val invalid = PersistedJob(
                id = UUID.randomUUID().toString(),
                type = "file-pitch",
                label = "Pitch de Arquivo",
                state = "ERROR",
                progress = 0,
                startedAt = System.currentTimeMillis(),
                message = "Parâmetros inválidos para iniciar o processamento.",
            )
            store.save(invalid)
            stopSelf()
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = "file-pitch",
            label = "Pitch de Arquivo",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando processamento após reinício do processo…"
            } else {
                "Iniciando processamento…"
            },
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()
        val request = FilePitchRenderRequest(
            inputUri = input,
            outputUri = output,
            semitones = semitones,
            audioKind = audioKind,
            outputFormat = outputFormat,
            cautionAccepted = intent.getBooleanExtra(EXTRA_CAUTION_ACCEPTED, false),
            jobId = persisted.id,
        )

        activeJob = scope.launch {
            try {
                val result = FilePitchRenderer.render(this@MediaProcessingService, request) { progress, message ->
                    updateJob(persisted, progress, message)
                }
                val message = "Concluído • ${result.sampleRate} Hz • ${result.channels} canal(is) • ${result.rubberBandIdentity}"
                val success = persisted.copy(state = "SUCCESS", progress = 100, message = message)
                store.save(success)
                notificationManager().notify(NOTIFICATION_ID, notification(success))
            } catch (cancelled: CancellationException) {
                val current = store.load()
                if (current?.state == "RUNNING") {
                    store.save(current.copy(state = "CANCELLED", message = "Processamento cancelado com cleanup concluído."))
                }
            } catch (error: Exception) {
                val failed = persisted.copy(
                    state = "ERROR",
                    message = error.message ?: "Falha inesperada no Pitch de Arquivo.",
                )
                store.save(failed)
                notificationManager().notify(NOTIFICATION_ID, notification(failed))
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateJob(base: PersistedJob, progress: Int, message: String) {
        val next = base.copy(
            state = "RUNNING",
            progress = progress.coerceIn(0, 100),
            message = message,
        )
        store.save(next)
        notificationManager().notify(NOTIFICATION_ID, notification(next))
    }

    private fun startSelfTest() {
        if (activeJob?.isActive == true) return
        val persisted = PersistedJob(
            id = UUID.randomUUID().toString(),
            type = "background-self-test",
            label = "Teste de execução em segundo plano",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = "Iniciando…",
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()
        activeJob = scope.launch {
            try {
                for (p in 1..100) {
                    if (!isActive) return@launch
                    delay(120)
                    val next = persisted.copy(progress = p, message = "Validando persistência e notificação…")
                    store.save(next)
                    notificationManager().notify(NOTIFICATION_ID, notification(next))
                }
                store.save(persisted.copy(state = "SUCCESS", progress = 100, message = "Teste concluído"))
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelCurrent(message: String) {
        val current = store.load()
        if (current != null && current.state == "RUNNING") {
            store.save(current.copy(state = "CANCELLED", message = message))
        }
        activeJob?.cancel(CancellationException(message))
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val manager = getSystemService(PowerManager::class.java)
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GBW:media-processing").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        val current = wakeLock
        if (current?.isHeld == true) current.release()
        wakeLock = null
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val current = store.load()
        if (current != null) {
            store.save(current.copy(state = "INTERRUPTED", message = "Limite de processamento em segundo plano atingido."))
        }
        activeJob?.cancel(CancellationException("Foreground service timeout"))
        releaseWakeLock()
        stopSelf(startId)
    }

    private fun notification(job: PersistedJob): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelIntent = Intent(this, MediaProcessingService::class.java).setAction(ACTION_CANCEL)
        val cancelPending = PendingIntent.getService(this, 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gbw)
            .setContentTitle("GBW — ${job.label}")
            .setContentText(job.message)
            .setProgress(100, job.progress.coerceIn(0, 100), false)
            .setOnlyAlertOnce(true)
            .setOngoing(job.state == "RUNNING")
            .setContentIntent(openPending)
            .apply {
                if (job.state == "RUNNING") addAction(0, "Cancelar", cancelPending)
            }
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Processamento de áudio", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Tarefas longas do Guitar Backing Wizard"
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        activeJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SELF_TEST = "com.gbw.android.action.BACKGROUND_SELF_TEST"
        const val ACTION_CANCEL = "com.gbw.android.action.CANCEL_MEDIA_JOB"
        const val ACTION_FILE_PITCH = "com.gbw.android.action.FILE_PITCH"

        private const val EXTRA_INPUT_URI = "input_uri"
        private const val EXTRA_OUTPUT_URI = "output_uri"
        private const val EXTRA_SEMITONES = "semitones"
        private const val EXTRA_AUDIO_KIND = "audio_kind"
        private const val EXTRA_OUTPUT_FORMAT = "output_format"
        private const val EXTRA_CAUTION_ACCEPTED = "caution_accepted"
        private const val EXTRA_JOB_ID = "job_id"

        private const val CHANNEL_ID = "gbw_media_processing"
        private const val NOTIFICATION_ID = 2301
        private const val MAX_WAKE_LOCK_MS = 6L * 60L * 60L * 1000L

        fun filePitchIntent(
            context: Context,
            inputUri: Uri,
            outputUri: Uri,
            semitones: Int,
            audioKind: AudioKind,
            outputFormat: OutputFormat,
            cautionAccepted: Boolean,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_FILE_PITCH)
            .putExtra(EXTRA_INPUT_URI, inputUri.toString())
            .putExtra(EXTRA_OUTPUT_URI, outputUri.toString())
            .putExtra(EXTRA_SEMITONES, semitones)
            .putExtra(EXTRA_AUDIO_KIND, audioKind.name)
            .putExtra(EXTRA_OUTPUT_FORMAT, outputFormat.name)
            .putExtra(EXTRA_CAUTION_ACCEPTED, cautionAccepted)
            .putExtra(EXTRA_JOB_ID, jobId)
    }
}
