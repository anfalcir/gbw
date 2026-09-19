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
import androidx.compose.material3.Checkbox
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
import com.gbw.android.domain.FilePitchRules
import com.gbw.android.domain.OutputFormat
import com.gbw.android.domain.Tunings
import com.gbw.android.export.ProjectExportCopier
import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProjectsScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { ProjectRepository(context) }
    val dirtyStore = remember(context) { BackupDirtyStore(context) }
    val coordinator = remember(context) { ProjectBackupCoordinator(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(repo.list()) }
    var activeId by remember { mutableStateOf(repo.active()?.projectId) }
    var renameTarget by remember { mutableStateOf<ProjectManifest?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectManifest?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        projects = repo.list()
        activeId = repo.active()?.projectId
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            refresh()
            delay(1500)
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Projetos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Renomear mantém o mesmo projeto; duplicar cria uma identidade nova.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (projects.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text("Nenhum projeto criado ainda.", Modifier.padding(16.dp))
            }
        }
        projects.forEach { project ->
            val dirty = dirtyStore.get(project.projectId) != null
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(project.name, fontWeight = FontWeight.SemiBold)
                    val meta = listOf(project.artist, project.song).filter { it.isNotBlank() }.joinToString(" — ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Alterado: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(project.updatedAtEpochMs)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when {
                            dirty -> "Backup: pendente"
                            project.lastSyncedRevisionId != null -> "Backup: sincronizado"
                            else -> "Backup: somente local"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (project.projectId == activeId) {
                        Text("Ativo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val p = withContext(Dispatchers.IO) { repo.setActive(project.projectId) }
                                    val uri = withContext(Dispatchers.IO) {
                                        repo.projectSourceUri(p.projectId)?.toString().orEmpty()
                                    }
                                    onOpen(uri)
                                } catch (e: Exception) {
                                    message = e.message
                                }
                            }
                        }) { Text("Abrir") }
                        if (project.artist.isBlank() || project.song.isBlank()) {
                            OutlinedButton(onClick = {
                                renameTarget = project
                                renameText = project.name
                            }) { Text("Renomear") }
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { repo.duplicate(project.projectId) } }
                                    .onFailure { message = it.message }
                                refresh()
                            }
                        }) { Text("Duplicar") }
                        TextButton(onClick = { deleteTarget = project }) { Text("Excluir") }
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
internal fun ProjectTuningScreen() {
    val context = LocalContext.current
    val repo = remember(context) { ProjectRepository(context) }
    var project by remember { mutableStateOf(repo.active()) }
    var byTuning by rememberSaveable { mutableStateOf(true) }
    var sourceTuning by rememberSaveable { mutableStateOf("Drop D") }
    var targetTuning by rememberSaveable { mutableStateOf("Drop B") }
    var manualSemitones by rememberSaveable { mutableStateOf(project?.pitch?.semitones ?: 0) }
    var vocalFormants by rememberSaveable { mutableStateOf(project?.pitch?.vocalFormants ?: true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            project = repo.active()
            delay(1200)
        }
    }
    val semitones = if (byTuning) {
        FilePitchRules.semitonesFromTunings(sourceTuning, targetTuning)
    } else {
        manualSemitones
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Afinação & Pitch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val p = project
        if (p == null) {
            Text("Abra ou crie um projeto primeiro.")
            return@Column
        }
        Text("Projeto: " + p.name)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = byTuning, onCheckedChange = { byTuning = true })
                    Text("Por afinação")
                    Checkbox(checked = !byTuning, onCheckedChange = { byTuning = false })
                    Text("Por semitons")
                }
                if (byTuning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactDropdown("Atual", sourceTuning, Tunings.names) { sourceTuning = it }
                        CompactDropdown("Destino", targetTuning, Tunings.names) { targetTuning = it }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (manualSemitones > -12) manualSemitones-- }) { Text("−") }
                        Text(manualSemitones.toString() + " st", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (manualSemitones < 12) manualSemitones++ }) { Text("+") }
                    }
                }
                val resultText = semitones?.let { value ->
                    (if (value >= 0) "+" else "") + value + " st"
                } ?: "incompatível"
                Text("Resultado: " + resultText)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = vocalFormants, onCheckedChange = { vocalFormants = it })
                    Text("Preservar formantes nos vocais")
                }
                Text(
                    "Bateria permanece sem pitch; guitar/bass/other/piano recebem pitch; vocals respeita formantes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = {
                val value = semitones
                if (value == null) {
                    message = "Conversão de afinação inválida."
                } else {
                    runCatching {
                        project = repo.updatePitch(p.projectId, value, vocalFormants)
                        message = "Configuração salva."
                    }.onFailure { message = it.message }
                }
            },
            enabled = semitones != null && p.separation != null,
        ) { Text("Salvar configuração") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun ProjectExportScreen() {
    val context = LocalContext.current
    val repo = remember(context) { ProjectRepository(context) }
    val jobStore = remember(context) { JobStore(context) }
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf(repo.active()) }
    var job by remember { mutableStateOf(jobStore.loadReconciled()) }
    var formatName by rememberSaveable { mutableStateOf(OutputFormat.FLAC_24.name) }
    var includeOriginal by rememberSaveable { mutableStateOf(true) }
    var includePitched by rememberSaveable { mutableStateOf(true) }
    var copyBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var handledTerminalJobId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            project = repo.active()
            val freshJob = jobStore.loadReconciled()
            job = freshJob
            if (
                freshJob?.type == MediaProcessingService.PROJECT_EXPORT_TYPE &&
                freshJob.state !in setOf("RUNNING", "CANCELLING") &&
                freshJob.id != handledTerminalJobId
            ) {
                handledTerminalJobId = freshJob.id
                message = when (freshJob.state) {
                    "SUCCESS" -> "Exportação concluída. Backing + guitar estão prontos."
                    "CANCELLED" -> "Exportação cancelada. Nenhum arquivo parcial foi mantido."
                    else -> freshJob.message
                }
                project = repo.active()
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
            Text("Abra ou crie um projeto primeiro.")
            return@Column
        }
        Text(
            "Produto final: backing + guitar. O pico da recombinação define um único ganho compartilhado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text("Pitch: " + (if (p.pitch.semitones >= 0) "+" else "") + p.pitch.semitones + " st")
                CompactDropdown(
                    "Formato",
                    OutputFormat.valueOf(formatName).label,
                    OutputFormat.entries.map { it.label },
                ) { label -> formatName = OutputFormat.entries.first { it.label == label }.name }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeOriginal, onCheckedChange = { includeOriginal = it })
                    Text("Par ORIGINAL")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includePitched, onCheckedChange = { includePitched = it })
                    Text("Par AJUSTADO/PITCH")
                }
                if (p.pitch.semitones == 0 && includeOriginal && includePitched) {
                    Text("Pitch 0: nenhuma cópia ajustada redundante será criada.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val exportJob = job?.takeIf { it.type == MediaProcessingService.PROJECT_EXPORT_TYPE }
        val busy = exportJob?.state == "RUNNING" || exportJob?.state == "CANCELLING"
        Button(
            onClick = {
                ContextCompat.startForegroundService(
                    context,
                    MediaProcessingService.projectExportIntent(
                        context,
                        p.projectId,
                        includeOriginal,
                        includePitched,
                        OutputFormat.valueOf(formatName),
                    ),
                )
                message = "Exportação iniciada em segundo plano."
            },
            enabled = p.separation != null && !busy && (includeOriginal || includePitched),
        ) { Text(if (busy) "Exportando…" else "Gerar backing + guitar") }

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
                    export.artifacts.forEach { a ->
                        Text(a.variant + " • " + a.role + " • " + a.format)
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
internal fun BackupSettingsScreen() {
    val context = LocalContext.current
    val store = remember(context) { BackupSettingsStore(context) }
    val conflictStore = remember(context) { BackupConflictStore(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(store.load()) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
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
                        "A sincronização inicial continuará em segundo plano."
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
                    "O backup usa a pasta escolhida pelo Android/Google Drive, mas todas as leituras, " +
                        "gravações e reconciliações pesadas acontecem fora da interface.",
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
                        }.onSuccess { message = "Sincronização/backup solicitado em segundo plano." }
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
                    "No Drive: Projetos → Banda - Música → Fonte / Separacao - Stems / Exports / Projeto.",
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
