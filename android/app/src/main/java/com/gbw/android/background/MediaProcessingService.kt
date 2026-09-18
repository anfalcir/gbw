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
import com.gbw.android.separation.BsRoformerModelManager
import com.gbw.android.separation.BsRoformerSeparator
import com.gbw.android.separation.DemucsNative
import com.gbw.android.separation.DemucsSeparator
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
            ACTION_FILE_PITCH -> startFilePitch(requireNotNull(intent), flags)
            ACTION_DEMUCS_QUICK -> startDemucsQuick(requireNotNull(intent), flags)
            ACTION_BSROFORMER_HIGH_QUALITY ->
                startBsRoformerHighQuality(requireNotNull(intent), flags)
            ACTION_BSROFORMER_IMPORT ->
                startBsRoformerImport(requireNotNull(intent), flags)
        }
        return if (
            action == ACTION_FILE_PITCH ||
            action == ACTION_DEMUCS_QUICK ||
            action == ACTION_BSROFORMER_HIGH_QUALITY ||
            action == ACTION_BSROFORMER_IMPORT
        ) {
            START_REDELIVER_INTENT
        } else {
            START_NOT_STICKY
        }
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
            saveInvalid("file-pitch", "Pitch de Arquivo", "Parâmetros inválidos para iniciar o processamento.")
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
                finishSuccess(persisted, message)
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning("Processamento cancelado com cleanup concluído.")
            } catch (error: Exception) {
                finishError(persisted, error.message ?: "Falha inesperada no Pitch de Arquivo.")
            } finally {
                finishForegroundJob()
            }
        }
    }

    private fun startDemucsQuick(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val input = intent.getStringExtra(EXTRA_INPUT_URI)?.let(Uri::parse)
        if (input == null) {
            saveInvalid("separation-quick", "Separação Rápida", "Arquivo de entrada inválido para Demucs.")
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = "separation-quick",
            label = "Separação Rápida",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando a unidade de separação após reinício do processo…"
            } else {
                "Preparando htdemucs_6s…"
            },
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()

        activeJob = scope.launch {
            try {
                val result = DemucsSeparator.separate(
                    context = this@MediaProcessingService,
                    inputUri = input,
                    jobId = persisted.id,
                ) { progress, message -> updateJob(persisted, progress, message) }
                val elapsedSeconds = result.elapsedMillis / 1_000L
                val peakMiB = result.peakObservedPssKb / 1_024L
                val message = "Concluído • 6 stems • 44,1 kHz • ${elapsedSeconds}s • PSS observado ${peakMiB} MiB"
                finishSuccess(persisted, message)
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning("Separação cancelada; stems parciais removidos.")
            } catch (error: Exception) {
                finishError(persisted, error.message ?: "Falha inesperada na Separação Rápida.")
            } finally {
                finishForegroundJob()
            }
        }
    }

    private fun startBsRoformerHighQuality(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val input = intent.getStringExtra(EXTRA_INPUT_URI)?.let(Uri::parse)
        if (input == null) {
            saveInvalid(
                "separation-high-quality",
                "Alta qualidade",
                "Arquivo de entrada inválido para BS-RoFormer.",
            )
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = "separation-high-quality",
            label = "Alta qualidade",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando BS-RoFormer-SW após reinício do processo…"
            } else {
                "Verificando PTE BS-RoFormer-SW…"
            },
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()

        activeJob = scope.launch {
            try {
                val result = BsRoformerSeparator.separate(
                    context = this@MediaProcessingService,
                    inputUri = input,
                    jobId = persisted.id,
                ) { progress, message -> updateJob(persisted, progress, message) }
                val elapsedSeconds = result.elapsedMillis / 1_000L
                val peakMiB = result.peakObservedPssKb / 1_024L
                finishSuccess(
                    persisted,
                    "Concluído • 6 stems • BS-RoFormer-SW/XNNPACK • " +
                        "${elapsedSeconds}s • PSS observado ${peakMiB} MiB",
                )
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning(
                    "Alta qualidade cancelada; stems parciais removidos."
                )
            } catch (error: Exception) {
                finishError(
                    persisted,
                    error.message ?: "Falha inesperada na Alta qualidade.",
                )
            } finally {
                finishForegroundJob()
            }
        }
    }

    private fun startBsRoformerImport(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val input = intent.getStringExtra(EXTRA_INPUT_URI)?.let(Uri::parse)
        if (input == null) {
            saveInvalid(
                "bsroformer-model-import",
                "Modelo BS-RoFormer",
                "PTE selecionado é inválido.",
            )
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = "bsroformer-model-import",
            label = "Modelo BS-RoFormer",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando importação/verificação do PTE…"
            } else {
                "Importando PTE autoritativo…"
            },
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()

        activeJob = scope.launch {
            try {
                BsRoformerModelManager.installFromUri(
                    context = this@MediaProcessingService,
                    sourceUri = input,
                ) { progress, message -> updateJob(persisted, progress, message) }
                finishSuccess(
                    persisted,
                    "PTE BS-RoFormer-SW instalado e SHA-256 validado.",
                )
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning(
                    "Importação do PTE cancelada; arquivo parcial removido."
                )
            } catch (error: Exception) {
                finishError(
                    persisted,
                    error.message ?: "Falha ao importar PTE BS-RoFormer-SW.",
                )
            } finally {
                finishForegroundJob()
            }
        }
    }

    private fun saveInvalid(type: String, label: String, message: String) {
        store.save(
            PersistedJob(
                id = UUID.randomUUID().toString(),
                type = type,
                label = label,
                state = "ERROR",
                progress = 0,
                startedAt = System.currentTimeMillis(),
                message = message,
            )
        )
        stopSelf()
    }

    private fun finishSuccess(base: PersistedJob, message: String) {
        val success = base.copy(state = "SUCCESS", progress = 100, message = message)
        store.save(success)
        notificationManager().notify(NOTIFICATION_ID, notification(success))
    }

    private fun finishError(base: PersistedJob, message: String) {
        val failed = base.copy(state = "ERROR", message = message)
        store.save(failed)
        notificationManager().notify(NOTIFICATION_ID, notification(failed))
    }

    private fun finishCancelledIfRunning(message: String) {
        val current = store.load()
        if (current?.state == "RUNNING" || current?.state == "CANCELLING") {
            val cancelled = current.copy(state = "CANCELLED", message = message)
            store.save(cancelled)
            notificationManager().notify(NOTIFICATION_ID, notification(cancelled))
        }
    }

    private fun finishForegroundJob() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
                finishForegroundJob()
            }
        }
    }

    private fun cancelCurrent(message: String) {
        val current = store.load()
        if (current?.type == "separation-quick") DemucsNative.cancel()
        if (current != null && current.state == "RUNNING") {
            val cancelling = current.copy(
                state = "CANCELLING",
                message = "$message; finalizando o trecho atual e limpando temporários…",
            )
            store.save(cancelling)
            notificationManager().notify(NOTIFICATION_ID, notification(cancelling))
        }

        val job = activeJob
        if (job != null) {
            // ExecuTorch forward is not interruptible mid-call. Cancellation
            // is cooperative at the next checked boundary; keep the FGS alive
            // so finally can close the model and remove partial outputs.
            job.cancel(CancellationException(message))
        } else {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        if (current?.type == "separation-quick") DemucsNative.cancel()
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
            .setOngoing(job.state == "RUNNING" || job.state == "CANCELLING")
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
        if (::store.isInitialized && store.load()?.type == "separation-quick") DemucsNative.cancel()
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
        const val ACTION_DEMUCS_QUICK = "com.gbw.android.action.DEMUCS_QUICK"
        const val ACTION_BSROFORMER_HIGH_QUALITY =
            "com.gbw.android.action.BSROFORMER_HIGH_QUALITY"
        const val ACTION_BSROFORMER_IMPORT =
            "com.gbw.android.action.BSROFORMER_IMPORT"

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

        fun demucsQuickIntent(
            context: Context,
            inputUri: Uri,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_DEMUCS_QUICK)
            .putExtra(EXTRA_INPUT_URI, inputUri.toString())
            .putExtra(EXTRA_JOB_ID, jobId)

        fun bsRoformerHighQualityIntent(
            context: Context,
            inputUri: Uri,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_BSROFORMER_HIGH_QUALITY)
            .putExtra(EXTRA_INPUT_URI, inputUri.toString())
            .putExtra(EXTRA_JOB_ID, jobId)

        fun bsRoformerImportIntent(
            context: Context,
            modelUri: Uri,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_BSROFORMER_IMPORT)
            .putExtra(EXTRA_INPUT_URI, modelUri.toString())
            .putExtra(EXTRA_JOB_ID, jobId)
    }
}
