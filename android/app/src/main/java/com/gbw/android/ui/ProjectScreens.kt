package com.gbw.android.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gbw.android.backup.BackupConflict
import com.gbw.android.backup.BackupConflictStore
import com.gbw.android.backup.BackupDestination
import com.gbw.android.backup.BackupDirtyStore
import com.gbw.android.backup.BackupScheduler
import com.gbw.android.backup.BackupSettings
import com.gbw.android.backup.BackupSettingsStore
import com.gbw.android.backup.ProjectBackupCoordinator
import com.gbw.android.backup.SafBackupRemoteStore
import com.gbw.android.background.JobStore
import com.gbw.android.background.MediaProcessingService
import com.gbw.android.domain.OutputFormat
import com.gbw.android.export.ProjectExportCopier
import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.ProjectJobLinkStore
import com.gbw.android.project.foldProjectSearchText
import com.gbw.android.project.projectMatchesSearch
import com.gbw.android.project.projectWorkflowStageLabel
import com.gbw.android.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun ProjectsScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { ProjectRepository(context) }
    val dirtyStore = remember(context) { BackupDirtyStore(context) }
    val coordinator = remember(context) { ProjectBackupCoordinator(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(repo.list()) }
    var activeId by remember { mutableStateOf(repo.active()?.projectId) }
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProjectManifest?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectManifest?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    ToastMessage(message, long = true)

    fun refresh() {
        projects = repo.list()
        activeId = repo.active()?.projectId
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withContext(Dispatchers.IO) { refresh() }
            delay(1500)
        }
    }

    val filtered = remember(projects, query) {
        projects
            .filter { projectMatchesSearch(it, query) }
            .sortedWith(
                compareBy<ProjectManifest>(
                    { foldProjectSearchText(it.artist) },
                    { foldProjectSearchText(it.song) },
                    { foldProjectSearchText(it.name) },
                )
            )
    }
    val groups = remember(filtered) {
        filtered.groupBy { it.artist.ifBlank { "Sem artista" } }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Projetos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Organize suas músicas e retome cada projeto de onde parou.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Pesquisar por artista ou música") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (query.isBlank()) {
                "${projects.size} projeto(s)"
            } else {
                "${filtered.size} de ${projects.size} projeto(s)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (projects.isEmpty()) {
            WorkflowEmptyState(
                title = "Nenhum projeto criado",
                message = "Comece pela Fonte para criar seu primeiro projeto.",
                actionLabel = "Ir para Fonte",
                onAction = onCreate,
            )
        } else if (filtered.isEmpty()) {
            WorkflowEmptyState(
                title = "Nenhum resultado",
                message = "Nenhum artista ou música corresponde à pesquisa.",
                actionLabel = "Limpar pesquisa",
                onAction = { query = "" },
            )
        }

        groups.forEach { (artist, artistProjects) ->
            Text(
                "$artist • ${artistProjects.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            artistProjects.forEach { project ->
                val dirty = dirtyStore.get(project.projectId) != null
                val isActive = project.projectId == activeId
                val backupLabel = when {
                    dirty -> "Backup pendente"
                    project.lastSyncedRevisionId != null -> "Backup sincronizado"
                    else -> "Somente local"
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            project.song.ifBlank { project.name },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Etapa: ${projectWorkflowStageLabel(project.workflowStage)} • $backupLabel",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Alterado: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(project.updatedAtEpochMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isActive) {
                            Text(
                                "Projeto ativo",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        val p = withContext(Dispatchers.IO) {
                                            if (isActive) repo.load(project.projectId) else repo.setActive(project.projectId)
                                        }
                                        val uri = withContext(Dispatchers.IO) {
                                            repo.projectSourceUri(p.projectId)?.toString().orEmpty()
                                        }
                                        onOpen(uri)
                                    } catch (e: Exception) {
                                        message = e.message
                                    }
                                }
                            }) { Text(if (isActive) "Continuar" else "Abrir") }
                            if (project.artist.isBlank() || project.song.isBlank()) {
                                OutlinedButton(onClick = {
                                    renameTarget = project
                                    renameText = project.name
                                }) { Text("Renomear") }
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { repo.duplicate(project.projectId) }
                                    }.onSuccess { copy ->
                                        message = "Cópia criada: ${copy.name}"
                                    }.onFailure { message = it.message }
                                    refresh()
                                }
                            }) { Text("Duplicar") }
                            TextButton(onClick = { deleteTarget = project }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renomear projeto") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nome") },
                    singleLine = true,
                )
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { repo.rename(target.projectId, renameText) }
                            .onFailure { message = it.message }
                        renameTarget = null
                        refresh()
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Salvar") }
            },
        )
    }

    deleteTarget?.let { target ->
        val hasRemote = BackupSettingsStore(context).load().treeUri != null
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir projeto") },
            text = { Text("Excluir somente deste dispositivo não apaga o backup remoto.") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { coordinator.deleteLocalOnly(target.projectId) }
                                .onFailure { message = it.message }
                            deleteTarget = null
                            refresh()
                        }
                    }) { Text("Só dispositivo") }
                    if (hasRemote) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { coordinator.deleteLocalAndRemote(target.projectId) }
                                    .onFailure { message = it.message }
                                deleteTarget = null
                                refresh()
                            }
                        }) { Text("Dispositivo + backup") }
                    }
                }
            },
        )
    }
}

@Composable
internal fun ProjectExportScreen(
    onGoToSource: () -> Unit,
    onGoToSeparation: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { ProjectRepository(context) }
    val jobStore = remember(context) { JobStore(context) }
    val projectLinks = remember(context) { ProjectJobLinkStore(context) }
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<ProjectManifest?>(null) }
    var job by remember { mutableStateOf(jobStore.loadReconciled()) }
    var jobProjectId by remember { mutableStateOf<String?>(null) }
    var observedProjectId by remember { mutableStateOf<String?>(null) }
    var formatName by rememberSaveable { mutableStateOf(OutputFormat.FLAC_24.name) }
    var copyBusy by remember { mutableStateOf(false) }
    var startingExport by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    ToastMessage(message, long = true)
    var handledTerminalJobId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            val freshProject = withContext(Dispatchers.IO) { repo.active() }
            if (observedProjectId != freshProject?.projectId) {
                observedProjectId = freshProject?.projectId
                handledTerminalJobId = ""
                message = null
            }
            project = freshProject

            val freshJob = withContext(Dispatchers.IO) { jobStore.loadReconciled() }
            job = freshJob
            val linkedProjectId = withContext(Dispatchers.IO) {
                freshJob?.let { projectLinks.projectId(it.id) }
            }
            jobProjectId = linkedProjectId
            if (
                freshJob?.type == MediaProcessingService.PROJECT_EXPORT_TYPE &&
                freshJob.state !in setOf("RUNNING", "CANCELLING") &&
                freshJob.id != handledTerminalJobId &&
                linkedProjectId == freshProject?.projectId
            ) {
                handledTerminalJobId = freshJob.id
                message = when (freshJob.state) {
                    "SUCCESS" -> "Exportação concluída. Backing + guitar estão prontos."
                    "CANCELLED" -> "Exportação cancelada. Nenhum arquivo parcial foi mantido."
                    else -> freshJob.message
                }
                withContext(Dispatchers.IO) { projectLinks.remove(freshJob.id) }
                project = withContext(Dispatchers.IO) { repo.active() }
            }
            delay(750)
        }
    }

    val copyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = project
        if (uri != null && p != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            copyBusy = true
            scope.launch {
                try {
                    val count = ProjectExportCopier.copyCurrent(context, p.projectId, uri) { done, total, name ->
                        message = "Copiando " + done + "/" + total + " • " + name
                    }
                    message = count.toString() + " arquivo(s) copiado(s)."
                } catch (e: Exception) {
                    message = e.message ?: "Falha ao copiar os exports."
                } finally {
                    copyBusy = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Exportação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val p = project
        if (p == null) {
            WorkflowEmptyState(
                title = "Nenhum projeto aberto",
                message = "Abra um projeto ou comece pela Fonte antes de exportar.",
                actionLabel = "Ir para Fonte",
                onAction = onGoToSource,
            )
            return@Column
        }
        if (p.separation == null) {
            WorkflowEmptyState(
                title = "Separação necessária",
                message = "Este projeto ainda não possui a separação em seis faixas.",
                actionLabel = if (p.source == null) "Ir para Fonte" else "Ir para Separação",
                onAction = if (p.source == null) onGoToSource else onGoToSeparation,
            )
            return@Column
        }

        Text(
            "Exporte a backing track e a guitarra separadamente, mantendo a música no tom original e os níveis coerentes entre os arquivos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                CompactDropdown(
                    "Formato",
                    OutputFormat.valueOf(formatName).label,
                    OutputFormat.entries.map { it.label },
                ) { label -> formatName = OutputFormat.entries.first { it.label == label }.name }
            }
        }

        val exportJob = job?.takeIf {
            it.type == MediaProcessingService.PROJECT_EXPORT_TYPE && jobProjectId == p.projectId
        }
        val busy = exportJob?.state == "RUNNING" || exportJob?.state == "CANCELLING"
        Button(
            onClick = {
                if (startingExport) return@Button
                startingExport = true
                scope.launch {
                    val jobId = UUID.randomUUID().toString()
                    try {
                        withContext(Dispatchers.IO) { projectLinks.link(jobId, p.projectId) }
                        ContextCompat.startForegroundService(
                            context,
                            MediaProcessingService.projectExportIntent(
                                context,
                                p.projectId,
                                OutputFormat.valueOf(formatName),
                                jobId,
                            ),
                        )
                        message = "Exportação iniciada. Você pode continuar usando o aplicativo."
                    } catch (error: Exception) {
                        withContext(Dispatchers.IO) { projectLinks.remove(jobId) }
                        message = error.message ?: "Falha ao iniciar a exportação."
                    } finally {
                        startingExport = false
                    }
                }
            },
            enabled = !busy && !startingExport,
        ) { Text(if (busy || startingExport) "Exportando…" else "Gerar backing + guitar") }

        exportJob?.takeIf { it.state == "RUNNING" || it.state == "CANCELLING" }?.let { current ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.state + " • " + current.progress + "%")
                    Text(current.message)
                    if (current.state == "RUNNING") {
                        OutlinedButton(onClick = {
                            context.startService(
                                Intent(context, MediaProcessingService::class.java)
                                    .setAction(MediaProcessingService.ACTION_CANCEL)
                            )
                        }) { Text("Cancelar exportação") }
                    }
                }
            }
        }

        p.export?.let { export ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Último export", fontWeight = FontWeight.SemiBold)
                    export.artifacts.forEach { artifact ->
                        Text(artifact.role + " • " + artifact.format)
                    }
                    Button(onClick = { copyPicker.launch(null) }, enabled = !copyBusy) {
                        Text(if (copyBusy) "Copiando…" else "Copiar export para pasta…")
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun WorkflowEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
internal fun BackupSettingsScreen() {
    val context = LocalContext.current
    val store = remember(context) { BackupSettingsStore(context) }
    val conflictStore = remember(context) { BackupConflictStore(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(store.load()) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    ToastMessage(message, long = true)
    var conflicts by remember { mutableStateOf(conflictStore.load()) }
    var dirtyCount by remember { mutableStateOf(0) }
    var requestedSyncAt by rememberSaveable { mutableStateOf(0L) }

    fun persist(next: BackupSettings) {
        settings = next
        store.save(next)
        runCatching { BackupScheduler.applySettings(context, next) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val fresh = store.load()
            settings = fresh
            conflicts = conflictStore.load()
            dirtyCount = withContext(Dispatchers.IO) {
                BackupDirtyStore(context).list().size
            }
            if (requestedSyncAt > 0L && fresh.lastRunEpochMs >= requestedSyncAt) {
                message = if (fresh.lastError.isNullOrBlank()) {
                    "Sincronização/backup concluído."
                } else {
                    "Falha no backup: " + fresh.lastError
                }
                requestedSyncAt = 0L
            }
            delay(1000)
        }
    }

    LaunchedEffect(settings.treeUri) {
        val rawUri = settings.treeUri ?: return@LaunchedEffect
        val descriptor = runCatching {
            BackupDestination.describe(context, android.net.Uri.parse(rawUri))
        }.getOrNull() ?: return@LaunchedEffect
        if (settings.destinationLabel != descriptor.label) {
            val next = settings.copy(destinationLabel = descriptor.label)
            store.save(next)
            settings = next
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !working) {
            working = true
            message = "Validando a pasta escolhida…"
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    val descriptor = BackupDestination.describe(context, uri)
                    withContext(Dispatchers.IO) {
                        val remote = SafBackupRemoteStore(context, uri)
                        remote.probeDestination()
                        remote.initializeRoot()
                    }
                    val next = settings.copy(
                        treeUri = uri.toString(),
                        destinationLabel = descriptor.label,
                        lastError = null,
                    )
                    persist(next)
                    requestedSyncAt = System.currentTimeMillis()
                    BackupScheduler.enqueueInitialSync(context)
                    message =
                        "Destino conectado: ${descriptor.label}. " +
                        "A sincronização inicial continuará automaticamente."
                } catch (e: Exception) {
                    message = e.message ?: "A pasta não pôde ser validada."
                } finally {
                    working = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Escolha uma pasta no dispositivo ou no Google Drive. O GBW organiza e mantém seus projetos atualizados automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    "Destino: " + (settings.destinationLabel ?: "não conectado"),
                    fontWeight = FontWeight.SemiBold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { folderPicker.launch(null) }, enabled = !working) {
                        Text(
                            when {
                                working -> "Validando…"
                                settings.treeUri == null -> "Escolher pasta…"
                                else -> "Trocar pasta…"
                            }
                        )
                    }
                    if (settings.treeUri != null) {
                        OutlinedButton(
                            onClick = {
                                store.disconnect()
                                runCatching { BackupScheduler.cancelAutomatic(context) }
                                conflictStore.clear()
                                settings = store.load()
                                conflicts = emptyList()
                                requestedSyncAt = 0L
                                message = "Destino desconectado. O conteúdo remoto foi preservado."
                            },
                            enabled = !working,
                        ) { Text("Desconectar") }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            val next = settings.copy(
                                enabled = enabled,
                                intervalMinutes = if (enabled && settings.intervalMinutes == 0L) {
                                    1440L
                                } else {
                                    settings.intervalMinutes
                                },
                            )
                            persist(next)
                        },
                        enabled = settings.treeUri != null && !working,
                    )
                    Text("Backup automático")
                }

                CompactDropdown(
                    "Período",
                    intervalLabel(if (settings.enabled) settings.intervalMinutes else 0L),
                    listOf(
                        "Manual somente",
                        "15 minutos",
                        "1 hora",
                        "6 horas",
                        "12 horas",
                        "24 horas",
                    ),
                ) { label ->
                    val minutes = intervalValue(label)
                    val next = settings.copy(
                        enabled = minutes > 0 && settings.treeUri != null,
                        intervalMinutes = if (minutes > 0) minutes else settings.intervalMinutes,
                    )
                    persist(next)
                }

                Button(
                    onClick = {
                        runCatching {
                            requestedSyncAt = System.currentTimeMillis()
                            BackupScheduler.enqueueManual(context)
                        }.onSuccess { message = "Backup solicitado. O GBW continuará automaticamente." }
                            .onFailure { message = it.message }
                    },
                    enabled = settings.treeUri != null && !working,
                ) { Text("Backup agora") }

                Text("Pendências locais: " + dirtyCount)
                if (settings.lastRunEpochMs > 0) {
                    Text("Última execução: " + formatTime(settings.lastRunEpochMs))
                }
                if (settings.lastSuccessEpochMs > 0) {
                    Text("Último sucesso: " + formatTime(settings.lastSuccessEpochMs))
                }
                settings.lastError?.let {
                    Text("Último erro: $it", color = MaterialTheme.colorScheme.error)
                }

                Text(
                    "Os arquivos são organizados automaticamente por artista e música no destino escolhido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        conflicts.forEach { conflict ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conflito: " + conflict.name, fontWeight = FontWeight.SemiBold)
                    Text("Local e Drive mudaram desde a última sincronização. Nada foi sobrescrito.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    ProjectBackupCoordinator(context).resolveKeepLocal(conflict.projectId)
                                    conflictStore.remove(conflict.projectId)
                                    conflicts = conflictStore.load()
                                    message = "Versão local mantida."
                                } catch (e: Exception) {
                                    message = e.message
                                }
                            }
                        }) { Text("Manter local") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    ProjectBackupCoordinator(context).resolveUseRemote(conflict.projectId)
                                    conflictStore.remove(conflict.projectId)
                                    conflicts = conflictStore.load()
                                    message = "Versão do Drive restaurada."
                                } catch (e: Exception) {
                                    message = e.message
                                }
                            }
                        }) { Text("Usar Drive") }
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun CompactDropdown(
    label: String,
    selected: String,
    values: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

private fun intervalLabel(minutes: Long): String = when (minutes) {
    15L -> "15 minutos"
    60L -> "1 hora"
    360L -> "6 horas"
    720L -> "12 horas"
    1440L -> "24 horas"
    else -> "Manual somente"
}

private fun intervalValue(label: String): Long = when (label) {
    "15 minutos" -> 15L
    "1 hora" -> 60L
    "6 horas" -> 360L
    "12 horas" -> 720L
    "24 horas" -> 1440L
    else -> 0L
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
