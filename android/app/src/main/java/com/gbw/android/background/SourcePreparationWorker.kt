package com.gbw.android.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gbw.android.domain.SourceProvider
import com.gbw.android.project.ProjectJobLinkStore
import com.gbw.android.project.ProjectRepository
import com.gbw.android.source.OnlineSourcePrepareRequest
import com.gbw.android.source.OnlineSourcePreparer
import com.gbw.android.source.PreparedSourceStore
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Persistent online-source preparation owned by WorkManager.
 *
 * Source preparation is deliberately NOT promoted to a foreground dataSync
 * service. Android 15+ applies a rolling foreground-service quota that can
 * reject a perfectly valid user request even when no task is currently
 * visible. A normal WorkManager job survives page navigation/process
 * recreation and avoids consuming that foreground-service quota.
 */
internal class SourcePreparationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val jobs = JobStore(applicationContext)
    private val preparedSources = PreparedSourceStore(applicationContext)
    private val projectLinks = ProjectJobLinkStore(applicationContext)
    private val projects = ProjectRepository(applicationContext)

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val url = inputData.getString(KEY_URL).orEmpty()
        val provider = inputData.getString(KEY_PROVIDER)?.let {
            runCatching { SourceProvider.valueOf(it) }.getOrNull()
        }
        if (jobId.isBlank() || url.isBlank() || provider == null) {
            saveTerminal(
                jobId = jobId.ifBlank { id.toString() },
                state = "ERROR",
                message = "Não foi possível iniciar a preparação desta fonte.",
            )
            return Result.failure()
        }

        val startedAt =
            jobs.load()?.takeIf { it.id == jobId }?.startedAt ?: System.currentTimeMillis()
        val base = PersistedJob(
            id = jobId,
            type = MediaProcessingService.SOURCE_PREPARE_TYPE,
            label = "Preparar fonte",
            state = "RUNNING",
            progress = 0,
            startedAt = startedAt,
            message = "Preparando a fonte…",
        )
        jobs.save(base)

        return try {
            val request = OnlineSourcePrepareRequest(
                url = url,
                provider = provider,
                formatId = inputData.getString(KEY_FORMAT_ID).orEmpty(),
                expectedDurationSeconds = inputData.getDouble(KEY_DURATION, 0.0),
                title = inputData.getString(KEY_TITLE).orEmpty(),
            )
            val result = OnlineSourcePreparer.prepare(
                context = applicationContext,
                request = request,
                jobId = jobId,
            ) { progress, message ->
                jobs.save(
                    base.copy(
                        state = "RUNNING",
                        progress = progress.coerceIn(0, 100),
                        message = message,
                    )
                )
            }
            preparedSources.save(result)
            projectLinks.projectId(jobId)?.let { projectId ->
                val record = requireNotNull(preparedSources.load()) {
                    "A fonte preparada não pôde ser vinculada ao projeto."
                }
                projects.adoptPreparedSource(
                    projectId = projectId,
                    record = record,
                    name = result.title,
                    activate = false,
                )
            }
            jobs.save(
                base.copy(
                    state = "SUCCESS",
                    progress = 100,
                    message = "Fonte pronta para separação.",
                )
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            saveTerminal(jobId, "CANCELLED", "Preparação cancelada.")
            throw cancelled
        } catch (error: Exception) {
            saveTerminal(
                jobId,
                "ERROR",
                error.message ?: "Não foi possível preparar a fonte.",
            )
            Result.failure()
        }
    }

    override fun onStopped() {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        if (jobId.isNotBlank()) {
            OnlineSourcePreparer.cancel(jobId)
            val current = jobs.load()
            if (current?.id == jobId && current.state in setOf("RUNNING", "CANCELLING")) {
                jobs.save(
                    if (current.state == "CANCELLING") {
                        current.copy(
                            state = "CANCELLED",
                            message = "Preparação cancelada.",
                        )
                    } else {
                        current.copy(
                            state = "INTERRUPTED",
                            message = "A preparação foi interrompida pelo Android. Tente novamente.",
                        )
                    }
                )
            }
        }
        super.onStopped()
    }

    private fun saveTerminal(jobId: String, state: String, message: String) {
        val current = jobs.load()
        jobs.save(
            if (current?.id == jobId) {
                current.copy(state = state, message = message)
            } else {
                PersistedJob(
                    id = jobId,
                    type = MediaProcessingService.SOURCE_PREPARE_TYPE,
                    label = "Preparar fonte",
                    state = state,
                    progress = 0,
                    startedAt = System.currentTimeMillis(),
                    message = message,
                )
            }
        )
    }

    companion object {
        private const val UNIQUE_NAME = "gbw-source-preparation"
        private const val TAG_PREFIX = "gbw-source-preparation:"

        private const val KEY_JOB_ID = "job_id"
        private const val KEY_URL = "url"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_FORMAT_ID = "format_id"
        private const val KEY_DURATION = "duration"
        private const val KEY_TITLE = "title"

        fun enqueue(
            context: Context,
            jobId: String,
            request: OnlineSourcePrepareRequest,
        ) {
            require(jobId.isNotBlank())
            val appContext = context.applicationContext
            val store = JobStore(appContext)
            val current = store.loadReconciled()
            check(current == null || current.state !in setOf("RUNNING", "CANCELLING") || current.id == jobId) {
                "Já existe um processamento em andamento. Aguarde a conclusão ou cancele-o antes de iniciar outro."
            }
            store.save(
                PersistedJob(
                    id = jobId,
                    type = MediaProcessingService.SOURCE_PREPARE_TYPE,
                    label = "Preparar fonte",
                    state = "RUNNING",
                    progress = 0,
                    startedAt = System.currentTimeMillis(),
                    message = "Preparação agendada. Você pode continuar usando o aplicativo.",
                )
            )

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val work =
                OneTimeWorkRequestBuilder<SourcePreparationWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_JOB_ID to jobId,
                            KEY_URL to request.url,
                            KEY_PROVIDER to request.provider.name,
                            KEY_FORMAT_ID to request.formatId,
                            KEY_DURATION to request.expectedDurationSeconds,
                            KEY_TITLE to request.title,
                        )
                    )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        20,
                        TimeUnit.SECONDS,
                    )
                    .addTag(TAG_PREFIX + jobId)
                    .build()
            WorkManager.getInstance(appContext)
                .beginUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
                .enqueue()
        }

        fun cancel(context: Context, jobId: String) {
            if (jobId.isBlank()) return
            val appContext = context.applicationContext
            val store = JobStore(appContext)
            val current = store.load()
            if (current?.id == jobId && current.state == "RUNNING") {
                store.save(
                    current.copy(
                        state = "CANCELLING",
                        message = "Cancelando preparação…",
                    )
                )
            }
            OnlineSourcePreparer.cancel(jobId)
            WorkManager.getInstance(appContext).cancelAllWorkByTag(TAG_PREFIX + jobId)
        }
    }
}
