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
import android.util.Log
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
import com.gbw.android.separation.DemucsRuntimeMonitor
import com.gbw.android.separation.SeparationResultStore
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
    private lateinit var separationResults: SeparationResultStore

    override fun onCreate() {
        super.onCreate()
        store = JobStore(this)
        separationResults = SeparationResultStore(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        WorkerExitDiagnostics.markPhase(
            this,
            "service:" + (action?.substringAfterLast('.') ?: "unknown"),
        )
        return try {
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
            if (
                action == ACTION_FILE_PITCH ||
                action == ACTION_DEMUCS_QUICK ||
                action == ACTION_BSROFORMER_HIGH_QUALITY ||
                action == ACTION_BSROFORMER_IMPORT
            ) {
                START_REDELIVER_INTENT
            } else {
                START_NOT_STICKY
            }
        } catch (error: Exception) {
            handleStartFailure(action, error)
            START_NOT_STICKY
        }
    }

    private fun handleStartFailure(action: String?, error: Exception) {
        Log.e(TAG, "Falha ao iniciar Foreground Service para action=$action", error)
        val message = ForegroundServiceTypePolicy.userFacingFailure(error)
        val current = store.load()
        if (current != null && (current.state == "RUNNING" || current.state == "CANCELLING")) {
            store.save(current.copy(state = "ERROR", message = message))
        } else {
            store.save(
                PersistedJob(
                    id = UUID.randomUUID().toString(),
                    type = action ?: "service-start",
                    label = "Execução em segundo plano",
                    state = "ERROR",
                    progress = 0,
                    startedAt = System.currentTimeMillis(),
                    message = message,
                )
            )
        }
        releaseWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
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
                separationResults.saveQuick(persisted.id, result)
                val elapsedSeconds = result.elapsedMillis / 1_000L
                val peakMiB = result.peakObservedPssKb / 1_024L
                val medianSeconds = result.medianChunkMillis / 1_000.0
                val maxSeconds = result.maxChunkMillis / 1_000.0
                val thermal = DemucsRuntimeMonitor.thermalLabel(result.maxThermalStatus)
                val message =
                    "Concluído • 6 stems • 44,1 kHz • ${elapsedSeconds}s • " +
                        "PSS observado ${peakMiB} MiB • ${result.chunkCount} trechos • " +
                        "mediana ${"%.1f".format(medianSeconds)}s • máx ${"%.1f".format(maxSeconds)}s • " +
                        "térmico $thermal"
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
                separationResults.saveHighQuality(persisted.id, result)
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
        notifySafely(success)
    }

    private fun finishError(base: PersistedJob, message: String) {
        val failed = base.copy(state = "ERROR", message = message)
        store.save(failed)
        notifySafely(failed)
    }

    private fun finishCancelledIfRunning(message: String) {
        val current = store.load()
        if (current?.state == "RUNNING" || current?.state == "CANCELLING") {
            val cancelled = current.copy(state = "CANCELLED", message = message)
            store.save(cancelled)
            notifySafely(cancelled)
        }
    }

    private fun finishForegroundJob() {
        releaseWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun updateJob(base: PersistedJob, progress: Int, message: String) {
        val next = base.copy(
            state = "RUNNING",
            progress = progress.coerceIn(0, 100),
            message = message,
        )
        store.save(next)
        notifySafely(next)
    }

    private fun startSelfTest() {
        if (activeJob?.isActive == true) return
        WorkerExitDiagnostics.markPhase(this, "self-test")
        val persisted = PersistedJob(
            id = UUID.randomUUID().toString(),
            type = "background-self-test",
            label = "Teste de execução em segundo plano",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = "Iniciando validação do serviço…",
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()
        activeJob = scope.launch {
            try {
                for (p in 1..100) {
                    if (!isActive) return@launch
                    delay(120)
                    val next = persisted.copy(
                        progress = p,
                        message = "Validando persistência, notificação e wake lock…",
                    )
                    store.save(next)
                    notifySafely(next)
                }
                finishSuccess(persisted, "Teste concluído com sucesso.")
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning("Teste de segundo plano cancelado.")
            } catch (error: Exception) {
                val message =
                    "Falha no teste de segundo plano (" + error::class.java.simpleName + "): " +
                        (error.message ?: "sem detalhe adicional")
                store.save(persisted.copy(state = "ERROR", message = message))
            } finally {
                activeJob = null
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
            notifySafely(cancelling)
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
        runCatching {
            if (current?.isHeld == true) current.release()
        }
        wakeLock = null
    }

    private fun startAsForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ForegroundServiceTypePolicy.typeForSdk(Build.VERSION.SDK_INT),
        )
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

    private fun notifySafely(job: PersistedJob) {
        runCatching {
            notificationManager().notify(NOTIFICATION_ID, notification(job))
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

        private const val TAG = "GBW-MediaProcessing"
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
