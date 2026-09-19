package com.gbw.android.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.gbw.android.MainActivity
import com.gbw.android.R
import com.gbw.android.domain.OutputFormat
import com.gbw.android.export.ProjectExportRenderer
import com.gbw.android.project.ProjectJobLinkStore
import com.gbw.android.project.ProjectRepository
import com.gbw.android.separation.DemucsNative
import com.gbw.android.separation.DemucsSeparator
import com.gbw.android.separation.DemucsRuntimeMonitor
import com.gbw.android.separation.SeparationResultFiles
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
    private lateinit var projectLinks: ProjectJobLinkStore
    private lateinit var projects: ProjectRepository

    override fun onCreate() {
        super.onCreate()
        store = JobStore(this)
        separationResults = SeparationResultStore(this)
        projectLinks = ProjectJobLinkStore(this)
        projects = ProjectRepository(this)
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
                ACTION_DEMUCS -> startDemucs(requireNotNull(intent), flags)
                ACTION_PROJECT_EXPORT -> startProjectExport(requireNotNull(intent), flags)
            }
            if (
                action == ACTION_DEMUCS ||
                action == ACTION_PROJECT_EXPORT
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
        val sdkInt = Build.VERSION.SDK_INT
        val requestedType = ForegroundServiceTypePolicy.typeForSdk(sdkInt)
        val declaredType = declaredForegroundServiceType()
        val diagnostic = ForegroundServiceTypePolicy.diagnostic(
            error = error,
            sdkInt = sdkInt,
            requestedType = requestedType,
            declaredType = declaredType,
        )
        val current = store.load()
        if (current != null && (current.state == "RUNNING" || current.state == "CANCELLING")) {
            store.save(current.copy(state = "ERROR", message = message, diagnostic = diagnostic))
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
                    diagnostic = diagnostic,
                )
            )
        }
        releaseWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun startProjectExport(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        val outputFormat = intent.getStringExtra(EXTRA_OUTPUT_FORMAT)?.let {
            runCatching { OutputFormat.valueOf(it) }.getOrNull()
        }
        if (projectId.isBlank() || outputFormat == null) {
            saveInvalid(PROJECT_EXPORT_TYPE, "Exportação do projeto", "Parâmetros inválidos para iniciar a exportação.")
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = PROJECT_EXPORT_TYPE,
            label = "Exportação do projeto",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando exportação após reinício do processo…"
            } else {
                "Preparando backing + guitar…"
            },
        )
        store.save(persisted)
        startAsForeground(notification(persisted))
        acquireWakeLock()

        activeJob = scope.launch {
            try {
                val result = ProjectExportRenderer.render(
                    context = this@MediaProcessingService,
                    projectId = projectId,
                    outputFormat = outputFormat,
                ) { progress, message ->
                    updateJob(persisted, progress, message)
                }
                finishSuccess(
                    persisted,
                    "Concluído • ${result.state.artifacts.size} arquivo(s) • backing + guitar com ganho compartilhado.",
                )
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning("Exportação cancelada; arquivos parciais removidos.")
            } catch (error: Exception) {
                finishError(persisted, error.message ?: "Falha inesperada na exportação do projeto.")
            } finally {
                finishForegroundJob()
            }
        }
    }

    private fun startDemucs(intent: Intent, startFlags: Int) {
        if (activeJob?.isActive == true) return
        val input = intent.getStringExtra(EXTRA_INPUT_URI)?.let(Uri::parse)
        if (input == null) {
            saveInvalid(SeparationResultFiles.DEMUCS_TYPE, "Separação", "O arquivo de entrada não está disponível para separação.")
            return
        }

        val persisted = PersistedJob(
            id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString(),
            type = SeparationResultFiles.DEMUCS_TYPE,
            label = "Separação",
            state = "RUNNING",
            progress = 0,
            startedAt = System.currentTimeMillis(),
            message = if ((startFlags and START_FLAG_REDELIVERY) != 0) {
                "Retomando a unidade de separação após reinício do processo…"
            } else {
                "Preparando o motor de separação…"
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
                separationResults.saveDemucs(persisted.id, result)
                projectLinks.projectId(persisted.id)?.let { projectId ->
                    val record = requireNotNull(separationResults.load()) {
                        "O resultado da separação não pôde ser recuperado."
                    }
                    val validated = requireNotNull(
                        SeparationResultFiles.validate(filesDir, record)
                    ) { "O resultado da separação não pôde ser validado." }
                    projects.publishSeparation(projectId, validated)
                }
                val elapsedSeconds = result.elapsedMillis / 1_000L
                val peakMiB = result.peakObservedPssKb / 1_024L
                val medianSeconds = result.medianChunkMillis / 1_000.0
                val maxSeconds = result.maxChunkMillis / 1_000.0
                val thermal = DemucsRuntimeMonitor.thermalLabel(result.maxThermalStatus)
                val message =
                    "Concluído • 6 stems • 44,1 kHz • BLAS ${result.blasThreads} threads • ${elapsedSeconds}s • " +
                        "PSS observado ${peakMiB} MiB • ${result.chunkCount} trechos • " +
                        "mediana ${"%.1f".format(medianSeconds)}s • máx ${"%.1f".format(maxSeconds)}s • " +
                        "térmico $thermal"
                finishSuccess(persisted, message)
            } catch (cancelled: CancellationException) {
                finishCancelledIfRunning("Separação cancelada; stems parciais removidos.")
            } catch (error: Exception) {
                finishError(persisted, error.message ?: "Falha inesperada na Separação.")
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
        if (current != null && SeparationResultFiles.isDemucsType(current.type)) DemucsNative.cancel()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startTypedForeground(notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startTypedForeground(notification: Notification) {
        // mediaProcessing was added after the current ServiceCompat type allow-list.
        // Use the platform API so the Android 15+ type reaches the framework unchanged.
        startForeground(
            NOTIFICATION_ID,
            notification,
            ForegroundServiceTypePolicy.typeForSdk(Build.VERSION.SDK_INT),
        )
    }

    private fun declaredForegroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            declaredForegroundServiceTypeApi29()
        } else {
            0
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun declaredForegroundServiceTypeApi29(): Int =
        runCatching {
            val component = ComponentName(this, MediaProcessingService::class.java)
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getServiceInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0),
                ).foregroundServiceType
            } else {
                packageManager.getServiceInfo(component, 0).foregroundServiceType
            }
        }.getOrDefault(-1)

    override fun onTimeout(startId: Int, fgsType: Int) {
        val current = store.load()
        if (current != null && SeparationResultFiles.isDemucsType(current.type)) DemucsNative.cancel()
        if (current != null) {
            store.save(
                current.copy(
                    state = "INTERRUPTED",
                    message = "Limite de processamento em segundo plano atingido.",
                    diagnostic = ForegroundServiceTypePolicy.timeoutDiagnostic(
                        sdkInt = Build.VERSION.SDK_INT,
                        callbackType = fgsType,
                        declaredType = declaredForegroundServiceType(),
                    ),
                )
            )
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
        if (::store.isInitialized && store.load()?.let { SeparationResultFiles.isDemucsType(it.type) } == true) DemucsNative.cancel()
        activeJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SELF_TEST = "com.gbw.android.action.BACKGROUND_SELF_TEST"
        const val ACTION_CANCEL = "com.gbw.android.action.CANCEL_MEDIA_JOB"
        const val ACTION_DEMUCS = "com.gbw.android.action.DEMUCS"
        const val ACTION_PROJECT_EXPORT = "com.gbw.android.action.PROJECT_EXPORT"

        private const val EXTRA_INPUT_URI = "input_uri"
        private const val EXTRA_OUTPUT_FORMAT = "output_format"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_PROJECT_ID = "project_id"

        const val SOURCE_PREPARE_TYPE = "source-prepare"
        const val PROJECT_EXPORT_TYPE = "project-export"

        private const val TAG = "GBW-MediaProcessing"
        private const val CHANNEL_ID = "gbw_media_processing"
        private const val NOTIFICATION_ID = 2301
        private const val MAX_WAKE_LOCK_MS = 6L * 60L * 60L * 1000L

        fun projectExportIntent(
            context: Context,
            projectId: String,
            outputFormat: OutputFormat,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_PROJECT_EXPORT)
            .putExtra(EXTRA_PROJECT_ID, projectId)
            .putExtra(EXTRA_OUTPUT_FORMAT, outputFormat.name)
            .putExtra(EXTRA_JOB_ID, jobId)

        fun demucsIntent(
            context: Context,
            inputUri: Uri,
            jobId: String = UUID.randomUUID().toString(),
        ): Intent = Intent(context, MediaProcessingService::class.java)
            .setAction(ACTION_DEMUCS)
            .putExtra(EXTRA_INPUT_URI, inputUri.toString())
            .putExtra(EXTRA_JOB_ID, jobId)

    }
}
