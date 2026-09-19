package com.gbw.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbw.android.BuildConfig
import com.gbw.android.audio.AudioInspectionDispatcher
import com.gbw.android.background.JobStore
import com.gbw.android.background.MediaProcessingService
import com.gbw.android.background.WorkerExitDiagnostics
import com.gbw.android.backup.BackupScheduler
import com.gbw.android.backup.BackupSettingsStore
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.QualityStatus
import com.gbw.android.domain.RankedSourceCandidate
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchLinks
import com.gbw.android.separation.DemucsRuntimeMonitor
import com.gbw.android.separation.DemucsThreadPolicy
import com.gbw.android.separation.SeparationResultFiles
import com.gbw.android.separation.SeparationResultStore
import com.gbw.android.separation.SeparationStemExporter
import com.gbw.android.separation.StemPreviewPlayer
import com.gbw.android.separation.ValidatedSeparationResult
import com.gbw.android.source.SourceSearchCoordinator
import com.gbw.android.source.PreparedSourceStore
import com.gbw.android.project.LegacyProjectMigrator
import com.gbw.android.project.ProjectJobLinkStore
import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.ProjectRepository
import com.gbw.android.project.automaticProjectName
import com.gbw.android.project.normalizeProjectText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val SEPARATION_STARTED_MESSAGE = "Separação iniciada em segundo plano."

private enum class AppPage(val title: String, val group: String) {
    SOURCE("1. Fonte", "PROCESSO"),
    SEPARATION("2. Separação", "PROCESSO"),
    EXPORT("3. Exportação", "PROCESSO"),
    PROJECTS("Projetos", "GERENCIAMENTO"),
    LOGS("Logs", "GERENCIAMENTO"),
    SETTINGS("Configurações", "APLICATIVO"),
    SYSTEM("Sistema", "APLICATIVO"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GbwApp() {
    GbwTheme {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            var page by rememberSaveable { mutableStateOf(AppPage.SOURCE.name) }
            var sourceUri by rememberSaveable { mutableStateOf("") }
            var projectSession by rememberSaveable { mutableStateOf(0) }
            var activeProject by remember { mutableStateOf<ProjectManifest?>(null) }
            var mediaJobBusy by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val projectRepository = remember(context) { ProjectRepository(context) }
            val shellJobStore = remember(context) { JobStore(context) }
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    LegacyProjectMigrator(context).migrateIfNeeded()
                    projectRepository.normalizeStoredMetadata()
                }
                BackupScheduler.applySettings(context, BackupSettingsStore(context).load())
                val initialProject = withContext(Dispatchers.IO) { projectRepository.active() }
                activeProject = initialProject
                sourceUri = withContext(Dispatchers.IO) {
                    initialProject?.let { projectRepository.projectSourceUri(it.projectId)?.toString() }.orEmpty()
                }
                while (isActive) {
                    val latestProject = withContext(Dispatchers.IO) { projectRepository.active() }
                    if (activeProject?.projectId != latestProject?.projectId) {
                        sourceUri = withContext(Dispatchers.IO) {
                            latestProject?.let { projectRepository.projectSourceUri(it.projectId)?.toString() }.orEmpty()
                        }
                        projectSession += 1
                    }
                    activeProject = latestProject
                    val currentJob = withContext(Dispatchers.IO) { shellJobStore.loadReconciled() }
                    mediaJobBusy = currentJob?.state == "RUNNING" || currentJob?.state == "CANCELLING"
                    delay(750)
                }
            }

            val current = AppPage.valueOf(page)
            val configuration = LocalConfiguration.current
            val wide = configuration.screenWidthDp >= 840
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val closeProject: () -> Unit = {
                if (!mediaJobBusy) {
                    scope.launch {
                        withContext(Dispatchers.IO) { projectRepository.closeActive() }
                        sourceUri = ""
                        activeProject = null
                        projectSession += 1
                        page = AppPage.SOURCE.name
                        snackbarHostState.showSnackbar("Projeto fechado.")
                    }
                }
            }
            val contextChanged: () -> Unit = { projectSession += 1 }

            if (wide) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { scaffoldPadding ->
                    Row(Modifier.fillMaxSize().padding(scaffoldPadding)) {
                        SideBar(
                            current = current,
                            activeProject = activeProject,
                            projectBusy = mediaJobBusy,
                            onSelect = { page = it.name },
                            onCloseProject = closeProject,
                            modifier = Modifier.width(260.dp).fillMaxHeight(),
                        )
                        Divider(Modifier.fillMaxHeight().width(1.dp))
                        PageContent(
                            page = current,
                            sourceUri = sourceUri,
                            onSourceUriChange = { sourceUri = it },
                            onNavigate = { page = it.name },
                            projectSession = projectSession,
                            onProjectContextChanged = contextChanged,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            SideBar(
                                current = current,
                                activeProject = activeProject,
                                projectBusy = mediaJobBusy,
                                onSelect = {
                                    page = it.name
                                    scope.launch { drawerState.close() }
                                },
                                onCloseProject = {
                                    closeProject()
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.width(300.dp).fillMaxHeight(),
                            )
                        }
                    },
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        activeProject?.let { current.title + " • " + it.name }
                                            ?: current.title
                                    )
                                },
                                navigationIcon = {
                                    TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰") }
                                }
                            )
                        }
                    ) { padding ->
                        PageContent(
                            page = current,
                            sourceUri = sourceUri,
                            onSourceUriChange = { sourceUri = it },
                            onNavigate = { page = it.name },
                            projectSession = projectSession,
                            onProjectContextChanged = contextChanged,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBar(
    current: AppPage,
    activeProject: ProjectManifest?,
    projectBusy: Boolean,
    onSelect: (AppPage) -> Unit,
    onCloseProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(modifier.padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GBW", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (activeProject == null) {
            Text(
                "Nenhum projeto aberto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Comece pela Fonte ou abra um projeto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "PROJETO ABERTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                activeProject.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
                onClick = onCloseProject,
                enabled = !projectBusy,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(if (projectBusy) "Tarefa em andamento" else "Fechar projeto")
            }
        }
        Spacer(Modifier.height(8.dp))
        var lastGroup = ""
        AppPage.entries.forEach { page ->
            if (page.group != lastGroup) {
                Spacer(Modifier.height(8.dp))
                Text(page.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                lastGroup = page.group
            }
            if (page == current) {
                Button(onClick = { onSelect(page) }, modifier = Modifier.fillMaxWidth()) { Text(page.title) }
            } else {
                TextButton(onClick = { onSelect(page) }, modifier = Modifier.fillMaxWidth()) { Text(page.title) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Baseline: Linux v5.23", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PageContent(
    page: AppPage,
    sourceUri: String,
    onSourceUriChange: (String) -> Unit,
    onNavigate: (AppPage) -> Unit,
    projectSession: Int,
    onProjectContextChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxWidth().widthIn(max = 1120.dp)) {
            when (page) {
                AppPage.SOURCE -> SourceScreen(
                    selectedUriText = sourceUri,
                    onSelectedUri = onSourceUriChange,
                    onContinue = { onNavigate(AppPage.SEPARATION) },
                    projectSession = projectSession,
                    onProjectContextChanged = onProjectContextChanged,
                )
                AppPage.SEPARATION -> SeparationScreen(
                    projectSession = projectSession,
                    onGoToSource = { onNavigate(AppPage.SOURCE) },
                )
                AppPage.EXPORT -> ProjectExportScreen(
                    onGoToSource = { onNavigate(AppPage.SOURCE) },
                    onGoToSeparation = { onNavigate(AppPage.SEPARATION) },
                )
                AppPage.PROJECTS -> ProjectsScreen(
                    onOpen = { uri ->
                        onSourceUriChange(uri)
                        onProjectContextChanged()
                        onNavigate(AppPage.SOURCE)
                    },
                    onCreate = { onNavigate(AppPage.SOURCE) },
                )
                AppPage.LOGS -> PlaceholderScreen("Logs", "Logs de jobs e processamento serão persistidos por operação.")
                AppPage.SETTINGS -> BackupSettingsScreen()
                AppPage.SYSTEM -> SystemScreen()
            }
        }
    }
}

@Composable
private fun SourceScreen(
    selectedUriText: String,
    onSelectedUri: (String) -> Unit,
    onContinue: () -> Unit,
    projectSession: Int,
    onProjectContextChanged: () -> Unit,
) {
    val context = LocalContext.current
    var inspection by remember { mutableStateOf<AudioInspection?>(null) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    val onlineScope = rememberCoroutineScope()
    val onlineCoordinator = remember(context) { SourceSearchCoordinator(context) }
    val searchState: SourceSearchViewModel = viewModel()
    val sourceJobStore = remember(context) { JobStore(context) }
    val preparedSourceStore = remember(context) { PreparedSourceStore(context) }
    val projectRepository = remember(context) { ProjectRepository(context) }
    val projectLinks = remember(context) { ProjectJobLinkStore(context) }
    var sourceJobState by remember { mutableStateOf(sourceJobStore.loadReconciled()) }
    var sourceJobProjectId by remember { mutableStateOf<String?>(null) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    var incorporatingLocal by remember { mutableStateOf(false) }
    var consumedPreparedJobId by rememberSaveable { mutableStateOf("") }
    var handledSourceTerminalJobId by rememberSaveable { mutableStateOf("") }
    var autoContinuePrepared by rememberSaveable { mutableStateOf(false) }
    val selectedDisplayName = remember(selectedUriText) {
        selectedUriText.takeIf { it.isNotBlank() }?.let { audioDisplayName(context, it) }
    }

    LaunchedEffect(projectSession) {
        val active = withContext(Dispatchers.IO) { projectRepository.active() }
        activeProjectId = active?.projectId
        if (active == null) {
            searchState.resetSession()
        } else {
            searchState.syncIdentity(active.artist, active.song)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !incorporatingLocal) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            incorporatingLocal = true
            onlineScope.launch {
                try {
                    val displayName = audioDisplayName(context, uri.toString())
                    val project = withContext(Dispatchers.IO) {
                        projectRepository.adoptLocalSource(
                            uri = uri,
                            displayNameHint = displayName,
                            name = searchState.song.ifBlank { displayName ?: "Novo projeto" },
                            artist = searchState.artist,
                            song = searchState.song,
                        )
                    }
                    val managed = withContext(Dispatchers.IO) {
                        requireNotNull(projectRepository.projectSourceUri(project.projectId)).toString()
                    }
                    activeProjectId = project.projectId
                    onSelectedUri(managed)
                    onProjectContextChanged()
                    searchState.updateMessage("Fonte local incorporada ao projeto.")
                } catch (error: Exception) {
                    searchState.updateMessage(error.message ?: "Falha ao incorporar a fonte local.")
                } finally {
                    incorporatingLocal = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val job = withContext(Dispatchers.IO) { sourceJobStore.loadReconciled() }
            sourceJobState = job
            val linkedProjectId = withContext(Dispatchers.IO) {
                job?.let { projectLinks.projectId(it.id) }
            }
            sourceJobProjectId = linkedProjectId
            activeProjectId = withContext(Dispatchers.IO) { projectRepository.active()?.projectId }

            if (
                job?.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                job.state == "SUCCESS" &&
                job.id != consumedPreparedJobId
            ) {
                val prepared = withContext(Dispatchers.IO) { preparedSourceStore.load() }
                if (
                    prepared?.jobId == job.id &&
                    prepared.preparedFile().isFile &&
                    linkedProjectId != null
                ) {
                    try {
                        val project = withContext(Dispatchers.IO) {
                            val existing = projectRepository.load(linkedProjectId)
                            if (
                                existing.source?.preparedRelativePath == null ||
                                existing.source.sourceUrl != prepared.sourceUrl
                            ) {
                                projectRepository.adoptPreparedSource(
                                    projectId = linkedProjectId,
                                    record = prepared,
                                    name = prepared.title,
                                    activate = false,
                                )
                            } else {
                                existing
                            }
                        }
                        consumedPreparedJobId = job.id
                        handledSourceTerminalJobId = job.id
                        withContext(Dispatchers.IO) { projectLinks.remove(job.id) }

                        val stillActive = withContext(Dispatchers.IO) {
                            projectRepository.active()?.projectId == project.projectId
                        }
                        if (stillActive) {
                            val managedUri = withContext(Dispatchers.IO) {
                                requireNotNull(projectRepository.projectSourceUri(project.projectId)).toString()
                            }
                            activeProjectId = project.projectId
                            autoContinuePrepared = true
                            onSelectedUri(managedUri)
                            onProjectContextChanged()
                            searchState.syncIdentity(project.artist, project.song)
                            searchState.updateMessage("Fonte online incorporada ao projeto e preparada com sucesso.")
                        }
                    } catch (error: Exception) {
                        handledSourceTerminalJobId = job.id
                        searchState.updateMessage(error.message ?: "Falha ao incorporar a fonte ao projeto.")
                    }
                } else {
                    consumedPreparedJobId = job.id
                    handledSourceTerminalJobId = job.id
                    linkedProjectId?.let {
                        withContext(Dispatchers.IO) { projectLinks.remove(job.id) }
                    }
                    if (linkedProjectId == activeProjectId || linkedProjectId == null) {
                        searchState.updateMessage(
                            "A preparação terminou, mas o resultado não possui vínculo íntegro com o projeto. Tente novamente."
                        )
                    }
                }
            } else if (
                job?.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                job.state !in setOf("RUNNING", "CANCELLING", "SUCCESS") &&
                job.id != handledSourceTerminalJobId
            ) {
                handledSourceTerminalJobId = job.id
                if (linkedProjectId == activeProjectId || linkedProjectId == null) {
                    searchState.updateMessage(job.message)
                }
                linkedProjectId?.let {
                    withContext(Dispatchers.IO) { projectLinks.remove(job.id) }
                }
            }
            delay(750)
        }
    }

    LaunchedEffect(selectedUriText) {
        inspection = null
        inspectionError = null
        if (selectedUriText.isBlank()) return@LaunchedEffect
        inspecting = true
        try {
            inspection = AudioInspectionDispatcher.inspect(context, Uri.parse(selectedUriText))
        } catch (error: Exception) {
            inspectionError = error.message ?: "Falha ao analisar o arquivo selecionado."
        } finally {
            inspecting = false
        }
    }

    LaunchedEffect(autoContinuePrepared, inspection, inspectionError) {
        if (autoContinuePrepared && inspection != null && inspectionError == null) {
            autoContinuePrepared = false
            onContinue()
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Fonte", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Escolha um arquivo local ou deixe o GBW pesquisar, baixar e preparar automaticamente a melhor fonte online antes da Separação.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Arquivo local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    enabled = !incorporatingLocal,
                ) {
                    Text(if (selectedUriText.isBlank()) "Selecionar áudio…" else "Trocar áudio…")
                }
                if (selectedUriText.isNotBlank()) {
                    Text(
                        selectedDisplayName ?: "Arquivo selecionado",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (inspecting) {
                    Text("Analisando formato e qualidade…", style = MaterialTheme.typography.bodySmall)
                }
                inspectionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                inspection?.let { QualityCard(it) }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pesquisa online de fontes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "O GBW usa o mesmo contrato do Linux 5.23: pesquisa, inspeciona e ranqueia fontes; " +
                        "depois baixa automaticamente o candidato selecionado e prepara o áudio para Separação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = searchState.artist,
                    onValueChange = searchState::updateArtist,
                    label = { Text("Artista") },
                    placeholder = { Text("Ex.: Wolves At The Gate") },
                    enabled = !searchState.searching,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = searchState.song,
                    onValueChange = searchState::updateSong,
                    label = { Text("Música") },
                    placeholder = { Text("Ex.: Enemy") },
                    enabled = !searchState.searching,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SimpleDropdown(
                    label = "Profundidade",
                    selected = if (searchState.depthName == SourceSearchDepth.MAXIMUM.name) "Máxima" else "Robusta",
                    values = listOf("Robusta", "Máxima"),
                    onSelect = {
                        searchState.setDepth(
                            if (it == "Máxima") SourceSearchDepth.MAXIMUM else SourceSearchDepth.ROBUST,
                        )
                    },
                )

                Button(
                    onClick = {
                        val artist = normalizeProjectText(searchState.artist)
                        val song = normalizeProjectText(searchState.song)
                        if (song.isBlank()) {
                            searchState.updateMessage("Informe o nome da música.")
                        } else {
                            searchState.syncIdentity(artist, song)
                            searchState.beginSearch()
                            val request = SourceSearchRequest(
                                artist = artist,
                                song = song,
                                depth = SourceSearchDepth.valueOf(searchState.depthName),
                            )
                            onlineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val currentProject = projectRepository.active()
                                            ?: projectRepository.create(
                                                name = automaticProjectName(artist, song),
                                                artist = artist,
                                                song = song,
                                            )
                                        projectRepository.updateMetadata(
                                            currentProject.projectId,
                                            artist,
                                            song,
                                        )
                                    }
                                    searchState.completeSearch(onlineCoordinator.search(request))
                                } catch (error: Exception) {
                                    searchState.failSearch(error)
                                }
                            }
                        }
                    },
                    enabled = !searchState.searching,
                ) {
                    Text(if (searchState.searching) "Pesquisando…" else "Encontrar fontes")
                }

                searchState.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (searchState.results.isEmpty() && !searchState.searching) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                searchState.results.forEachIndexed { index, candidate ->
                    OnlineSourceCandidateCard(
                        candidate = candidate,
                        recommended = index == 0 && !candidate.previewOnly,
                        selected = candidate.url == searchState.selectedUrl,
                        onSelect = { searchState.selectCandidate(candidate) },
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(candidate.url)),
                                )
                            }.onFailure { error ->
                                searchState.updateMessage(
                                    "Não foi possível abrir a fonte: " +
                                        (error.message ?: "nenhum aplicativo compatível."),
                                )
                            }
                        },
                    )
                }

                val selectedCandidate = searchState.selectedCandidate()
                val sourceJobRunning =
                    sourceJobState?.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                        sourceJobProjectId == activeProjectId &&
                        (sourceJobState?.state == "RUNNING" || sourceJobState?.state == "CANCELLING")
                if (selectedCandidate != null) {
                    Text(
                        if (selectedCandidate.automaticDownloadSupported) {
                            "✓ Fonte ativa: " + selectedCandidate.provider.publicLabel + " — " + selectedCandidate.title
                        } else {
                            "Fonte de catálogo: " + selectedCandidate.provider.publicLabel +
                                ". Este resultado serve como referência, mas não oferece aquisição automática."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedCandidate.automaticDownloadSupported) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Button(
                        onClick = {
                            val candidate = searchState.selectedCandidate() ?: return@Button
                            val project = projectRepository.active()
                                ?: projectRepository.create(
                                    name = searchState.song.ifBlank { candidate.title },
                                    artist = searchState.artist,
                                    song = searchState.song,
                                )
                            if (searchState.artist.isNotBlank() || searchState.song.isNotBlank()) {
                                projectRepository.updateMetadata(project.projectId, searchState.artist, searchState.song)
                            }
                            val jobId = UUID.randomUUID().toString()
                            projectLinks.link(jobId, project.projectId)
                            val intent = MediaProcessingService.sourcePrepareIntent(
                                context = context,
                                url = candidate.url,
                                provider = candidate.provider,
                                formatId = candidate.formatId,
                                expectedDurationSeconds = candidate.durationSeconds,
                                title = candidate.title,
                                jobId = jobId,
                            )
                            ContextCompat.startForegroundService(context, intent)
                            sourceJobState = sourceJobStore.loadReconciled()
                            searchState.updateMessage("Preparando fonte selecionada em segundo plano…")
                        },
                        enabled = selectedCandidate.automaticDownloadSupported &&
                            !selectedCandidate.previewOnly &&
                            !sourceJobRunning,
                    ) {
                        Text(if (sourceJobRunning) "Preparando…" else "Preparar fonte selecionada")
                    }
                }

                sourceJobState?.takeIf {
                    it.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                        sourceJobProjectId == activeProjectId &&
                        it.state in setOf("RUNNING", "CANCELLING")
                }?.let { job ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Preparação da fonte", fontWeight = FontWeight.SemiBold)
                            Text(job.state + " • " + job.progress + "%")
                            Text(job.message, style = MaterialTheme.typography.bodySmall)
                            if (job.state == "RUNNING") {
                                OutlinedButton(
                                    onClick = {
                                        ContextCompat.startForegroundService(
                                            context,
                                            Intent(context, MediaProcessingService::class.java)
                                                .setAction(MediaProcessingService.ACTION_CANCEL),
                                        )
                                    },
                                ) {
                                    Text("Cancelar preparação")
                                }
                            }
                        }
                    }
                }

                val broadRequest = SourceSearchRequest(
                    artist = searchState.artist.trim(),
                    song = searchState.song.trim(),
                    depth = SourceSearchDepth.valueOf(searchState.depthName),
                )
                val broadLinks = remember(searchState.artist, searchState.song, searchState.depthName) {
                    SourceSearchLinks.forRequest(broadRequest)
                }
                if (broadLinks.isNotEmpty()) {
                    Divider()
                    Text("Busca ampla", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Se os providers diretos não encontrarem a faixa, abra a pesquisa equivalente em outros serviços.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        broadLinks.forEach { link ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(link.url)),
                                        )
                                    }.onFailure { error ->
                                        searchState.updateMessage(
                                            "Não foi possível abrir ${link.label}: " +
                                                (error.message ?: "nenhum aplicativo compatível."),
                                        )
                                    }
                                },
                            ) {
                                Text(link.label)
                            }
                        }
                    }
                }

                Divider()
                Text("URL manual", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = searchState.manualUrl,
                    onValueChange = searchState::updateManualUrl,
                    label = { Text("Link da fonte") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val manualUri = remember(searchState.manualUrl) {
                    searchState.manualUrl.trim()
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                }
                OutlinedButton(
                    onClick = {
                        val uri = manualUri
                        if (uri == null) {
                            searchState.updateMessage("Informe uma URL válida iniciando com http:// ou https://.")
                        } else {
                            val project = projectRepository.active()
                                ?: projectRepository.create(
                                    name = searchState.song.ifBlank { "Fonte por URL" },
                                    artist = searchState.artist,
                                    song = searchState.song,
                                )
                            if (searchState.artist.isNotBlank() || searchState.song.isNotBlank()) {
                                projectRepository.updateMetadata(project.projectId, searchState.artist, searchState.song)
                            }
                            val jobId = UUID.randomUUID().toString()
                            projectLinks.link(jobId, project.projectId)
                            val intent = MediaProcessingService.sourcePrepareIntent(
                                context = context,
                                url = uri.toString(),
                                provider = com.gbw.android.domain.SourceProvider.OTHER,
                                formatId = "",
                                expectedDurationSeconds = 0.0,
                                title = searchState.song.trim(),
                                jobId = jobId,
                            )
                            ContextCompat.startForegroundService(context, intent)
                            searchState.updateMessage("Preparando URL manual em segundo plano…")
                        }
                    },
                    enabled = manualUri != null &&
                        !(sourceJobState?.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                            sourceJobState?.state == "RUNNING"),
                ) {
                    Text("Usar URL e preparar")
                }
            }
        }

        Button(
            onClick = {
                if (incorporatingLocal) return@Button
                incorporatingLocal = true
                onlineScope.launch {
                    try {
                        val selected = Uri.parse(selectedUriText)
                        val active = withContext(Dispatchers.IO) { projectRepository.active() }
                        val alreadyManaged = active?.let {
                            withContext(Dispatchers.IO) { projectRepository.projectSourceUri(it.projectId)?.toString() }
                        } == selectedUriText
                        val project = if (alreadyManaged) {
                            requireNotNull(active)
                        } else {
                            withContext(Dispatchers.IO) {
                                projectRepository.adoptLocalSource(
                                    uri = selected,
                                    displayNameHint = selectedDisplayName,
                                    name = searchState.song.ifBlank { selectedDisplayName ?: "Novo projeto" },
                                    artist = searchState.artist,
                                    song = searchState.song,
                                )
                            }
                        }
                        val managed = withContext(Dispatchers.IO) {
                            requireNotNull(projectRepository.projectSourceUri(project.projectId)).toString()
                        }
                        onSelectedUri(managed)
                        onContinue()
                    } catch (error: Exception) {
                        searchState.updateMessage(error.message ?: "Falha ao incorporar a fonte local ao projeto.")
                    } finally {
                        incorporatingLocal = false
                    }
                }
            },
            enabled = selectedUriText.isNotBlank() && inspection != null && inspectionError == null && !inspecting && !incorporatingLocal,
        ) {
            Text(if (incorporatingLocal) "Incorporando fonte…" else "Continuar para Separação")
        }
    }
}

@Composable
private fun OnlineSourceCandidateCard(
    candidate: RankedSourceCandidate,
    recommended: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (recommended) {
                Text(
                    "Recomendado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(candidate.title, fontWeight = FontWeight.SemiBold)
            Text(
                candidate.provider.publicLabel +
                    (candidate.uploader.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildString {
                    append(candidate.quality)
                    if (candidate.durationSeconds > 0) {
                        append(" • ")
                        append(com.gbw.android.domain.SourceSearchRules.durationLabel(candidate.durationSeconds))
                    }
                    append(" • score ")
                    append(candidate.score)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                candidate.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.previewOnly) {
                Text(
                    "Trecho curto: não recomendado como fonte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (selected) {
                Text(
                    "✓ Selecionada",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSelect,
                    enabled = !candidate.previewOnly && candidate.automaticDownloadSupported && !selected,
                ) {
                    Text(
                        when {
                            selected -> "Selecionada"
                            candidate.automaticDownloadSupported -> "Selecionar"
                            else -> "Somente catálogo"
                        }
                    )
                }
                OutlinedButton(onClick = onOpen) {
                    Text("Abrir fonte")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, text: String) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text)
    }
}

@Composable
private fun SeparationScreen(
    projectSession: Int,
    onGoToSource: () -> Unit,
) {
    val context = LocalContext.current
    val jobStore = remember(context) { JobStore(context) }
    val resultStore = remember(context) { SeparationResultStore(context) }
    val projectRepository = remember(context) { ProjectRepository(context) }
    val projectLinks = remember(context) { ProjectJobLinkStore(context) }
    val previewPlayer = remember { StemPreviewPlayer() }
    val scope = rememberCoroutineScope()
    var activeProject by remember { mutableStateOf<ProjectManifest?>(null) }
    var sourceUri by remember { mutableStateOf("") }
    var availableResult by remember { mutableStateOf<ValidatedSeparationResult?>(null) }
    var playingStem by remember { mutableStateOf<String?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var jobState by remember { mutableStateOf(jobStore.loadReconciled()) }
    var jobProjectId by remember { mutableStateOf<String?>(null) }
    var observedProjectId by remember { mutableStateOf<String?>(null) }
    var startingSeparation by remember { mutableStateOf(false) }
    val selectedDisplayName = remember(sourceUri) {
        sourceUri.takeIf { it.isNotBlank() }?.let { audioDisplayName(context, it) }
    }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    val exportPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            val result = availableResult
            if (treeUri != null && result != null && !exportBusy) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                exportBusy = true
                exportMessage = "Exportando os 6 stems…"
                scope.launch {
                    try {
                        val summary = SeparationStemExporter.exportAll(
                            context = context,
                            result = result,
                            treeUri = treeUri,
                        ) { completed, total, stem ->
                            exportMessage = "Exportando $completed/$total • ${stemPublicLabel(stem)}…"
                        }
                        exportMessage =
                            "Exportação concluída: ${summary.fileNames.size} WAVs • " +
                                formatStemBytes(summary.totalBytes) + "."
                    } catch (error: Exception) {
                        exportMessage = error.message ?: "Falha ao exportar os stems."
                    } finally {
                        exportBusy = false
                    }
                }
            }
        }

    LaunchedEffect(resultMessage) {
        if (resultMessage == SEPARATION_STARTED_MESSAGE) {
            delay(3_500)
            if (resultMessage == SEPARATION_STARTED_MESSAGE) resultMessage = null
        }
    }

    LaunchedEffect(projectSession) {
        while (isActive) {
            val freshJob = withContext(Dispatchers.IO) { jobStore.loadReconciled() }
            jobState = freshJob
            val linkedProjectId = withContext(Dispatchers.IO) {
                freshJob?.let { projectLinks.projectId(it.id) }
            }
            jobProjectId = linkedProjectId

            if (
                freshJob != null &&
                SeparationResultFiles.isDemucsType(freshJob.type) &&
                freshJob.state !in setOf("RUNNING", "CANCELLING") &&
                linkedProjectId != null
            ) {
                if (freshJob.state == "SUCCESS") {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val target = projectRepository.load(linkedProjectId)
                            if (target.separation?.jobId != freshJob.id) {
                                val record = resultStore.load()?.takeIf { it.jobId == freshJob.id }
                                    ?: error("Resultado Demucs concluído não foi encontrado.")
                                val validated = SeparationResultFiles.validate(context.filesDir, record)
                                    ?: error("Resultado Demucs concluído falhou na validação.")
                                projectRepository.publishSeparation(linkedProjectId, validated)
                            }
                        }
                    }.onFailure { error ->
                        if (projectRepository.active()?.projectId == linkedProjectId) {
                            resultMessage = error.message ?: "Falha ao publicar stems no projeto."
                        }
                    }
                }
                val activeId = withContext(Dispatchers.IO) { projectRepository.active()?.projectId }
                if (activeId == linkedProjectId) {
                    resultMessage = when (freshJob.state) {
                        "SUCCESS" -> "Separação concluída. Os seis stems pertencem a este projeto."
                        "CANCELLED" -> "Separação cancelada. Nenhum stem parcial foi mantido."
                        else -> freshJob.message
                    }
                }
                withContext(Dispatchers.IO) { projectLinks.remove(freshJob.id) }
            }

            val latestProject = withContext(Dispatchers.IO) { projectRepository.active() }
            if (observedProjectId != latestProject?.projectId) {
                observedProjectId = latestProject?.projectId
                previewPlayer.stop()
                playingStem = null
                resultMessage = null
                exportMessage = null
            }
            activeProject = latestProject
            sourceUri = withContext(Dispatchers.IO) {
                latestProject?.let { projectRepository.projectSourceUri(it.projectId)?.toString() }.orEmpty()
            }
            availableResult = withContext(Dispatchers.IO) {
                latestProject?.let { projectRepository.projectSeparationResult(it.projectId) }
            }
            delay(500)
        }
    }

    val p = activeProject
    val separationJob = jobState?.takeIf {
        p != null &&
            SeparationResultFiles.isDemucsType(it.type) &&
            jobProjectId == p.projectId
    }
    val appJobBusy =
        jobState?.state == "RUNNING" || jobState?.state == "CANCELLING"

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Separação",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "O GBW Android usa exclusivamente Demucs htdemucs_6s para gerar seis stems.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (p == null) {
            WorkflowEmptyState(
                title = "Nenhum projeto aberto",
                message = "A Separação só mostra conteúdo do projeto ativo.",
                actionLabel = "Ir para Fonte",
                onAction = onGoToSource,
            )
            return@Column
        }
        if (sourceUri.isBlank()) {
            WorkflowEmptyState(
                title = "Fonte necessária",
                message = "Este projeto ainda não possui uma fonte preparada.",
                actionLabel = "Ir para Fonte",
                onAction = onGoToSource,
            )
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Fonte do projeto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(selectedDisplayName ?: p.source?.title.orEmpty().ifBlank { "Arquivo do projeto" })
                OutlinedButton(onClick = onGoToSource, enabled = !appJobBusy) {
                    Text("Alterar na Fonte")
                }
                Text(
                    "No primeiro uso, o GBW baixa aproximadamente 54,9 MB do htdemucs_6s " +
                        "e valida tamanho + SHA-256 antes de processar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = {
                if (startingSeparation) return@Button
                startingSeparation = true
                scope.launch {
                    var jobId: String? = null
                    try {
                        val prepared = withContext(Dispatchers.IO) {
                            val current = requireNotNull(projectRepository.active()) {
                                "Nenhum projeto ativo."
                            }
                            require(current.projectId == p.projectId) {
                                "O projeto ativo mudou. Tente novamente."
                            }
                            val managedUri = requireNotNull(
                                projectRepository.projectSourceUri(current.projectId)
                            ) { "A fonte do projeto não está disponível." }
                            val newJobId = UUID.randomUUID().toString()
                            projectLinks.link(newJobId, current.projectId)
                            Pair(managedUri, newJobId)
                        }
                        jobId = prepared.second
                        ContextCompat.startForegroundService(
                            context,
                            MediaProcessingService.demucsIntent(context, prepared.first, prepared.second),
                        )
                        resultMessage = SEPARATION_STARTED_MESSAGE
                    } catch (error: Exception) {
                        jobId?.let { withContext(Dispatchers.IO) { projectLinks.remove(it) } }
                        resultMessage = error.message ?: "Falha ao iniciar a separação."
                    } finally {
                        startingSeparation = false
                    }
                }
            },
            enabled = !appJobBusy && !startingSeparation,
        ) {
            Text(if (startingSeparation) "Preparando…" else "Separar")
        }

        separationJob?.takeIf {
            it.state == "RUNNING" || it.state == "CANCELLING"
        }?.let { job ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Separação", fontWeight = FontWeight.SemiBold)
                    Text("${job.state} • ${job.progress}%")
                    Text(job.message)
                    if (job.state == "RUNNING") {
                        OutlinedButton(
                            onClick = {
                                context.startService(
                                    Intent(context, MediaProcessingService::class.java)
                                        .setAction(MediaProcessingService.ACTION_CANCEL)
                                )
                            }
                        ) {
                            Text("Cancelar separação")
                        }
                    }
                }
            }
        }

        availableResult?.let { result ->
            SeparationResultsCard(
                result = result,
                appJobBusy = appJobBusy,
                playingStem = playingStem,
                exportBusy = exportBusy,
                exportMessage = exportMessage,
                onTogglePreview = { stem ->
                    if (playingStem == stem.name) {
                        previewPlayer.stop()
                        playingStem = null
                    } else if (!appJobBusy) {
                        previewPlayer.play(
                            file = stem.file,
                            onFinished = {
                                if (playingStem == stem.name) playingStem = null
                            },
                            onError = { message ->
                                if (playingStem == stem.name) playingStem = null
                                exportMessage = message
                            },
                        )
                        playingStem = stem.name
                    }
                },
                onExport = { exportPicker.launch(null) },
            )
        } ?: Text(
            "Nenhum stem gerado para este projeto ainda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        resultMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SeparationResultsCard(
    result: ValidatedSeparationResult,
    appJobBusy: Boolean,
    playingStem: String?,
    exportBusy: Boolean,
    exportMessage: String?,
    onTogglePreview: (com.gbw.android.separation.SeparationStem) -> Unit,
    onExport: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Stems disponíveis", fontWeight = FontWeight.SemiBold)
            val durationSeconds = result.record.frames / 44_100.0
            Text(
                "Demucs htdemucs_6s • 6 WAV float32 • 44,1 kHz • ${"%.1f".format(durationSeconds)} s • " +
                    formatStemBytes(result.totalBytes),
                style = MaterialTheme.typography.bodySmall,
            )
            if (result.record.chunkCount > 0) {
                Text(
                    "Motor Android otimizado • BLAS ${result.record.blasThreads} threads • ${result.record.chunkCount} trechos • " +
                        "mediana ${"%.1f".format(result.record.medianChunkMillis / 1_000.0)} s • " +
                        "máx ${"%.1f".format(result.record.maxChunkMillis / 1_000.0)} s • " +
                        "térmico ${DemucsRuntimeMonitor.thermalLabel(result.record.maxThermalStatus)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.stems.forEach { stem ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stemPublicLabel(stem.name), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${stem.file.name} • ${formatStemBytes(stem.file.length())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { onTogglePreview(stem) },
                        enabled = !appJobBusy && !exportBusy,
                    ) {
                        Text(if (playingStem == stem.name) "Parar" else "Ouvir")
                    }
                }
            }
            Button(onClick = onExport, enabled = !appJobBusy && !exportBusy) {
                Text(if (exportBusy) "Exportando…" else "Exportar os 6 stems…")
            }
            Text(
                "A exportação usa o seletor de pastas do Android e cria seis WAVs " +
                    "GBW_Demucs_<job>_<stem>.wav. Os arquivos internos continuam preservados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private fun stemPublicLabel(name: String): String = when (name) {
    "drums" -> "Bateria"
    "bass" -> "Baixo"
    "other" -> "Outros"
    "vocals" -> "Vocais"
    "guitar" -> "Guitarra"
    "piano" -> "Piano"
    else -> name
}

private fun formatStemBytes(bytes: Long): String =
    if (bytes >= 1024L * 1024L) {
        "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        "%.1f KiB".format(bytes.toDouble() / 1024.0)
    }


@Composable
private fun QualityCard(info: AudioInspection) {
    val container = when (info.status) {
        QualityStatus.IDEAL -> MaterialTheme.colorScheme.primaryContainer
        QualityStatus.ADEQUATE -> MaterialTheme.colorScheme.secondaryContainer
        QualityStatus.CAUTION -> MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(info.status.label, fontWeight = FontWeight.Bold)
            Text(info.summary())
            info.notes.forEach { Text("• $it") }
            if (info.status == QualityStatus.CAUTION) Text("O processamento pedirá sua confirmação antes de começar.")
        }
    }
}

@Composable
private fun SimpleDropdown(
    label: String,
    selected: String,
    values: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(selected)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = {
                    onSelect(value)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun SystemScreen() {
    val context = LocalContext.current
    val jobStore = remember(context) { JobStore(context) }
    var jobState by remember { mutableStateOf(jobStore.loadReconciled()) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var workerExitSummary by remember {
        mutableStateOf(WorkerExitDiagnostics.latestSummary(context))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            jobState = jobStore.loadReconciled()
            workerExitSummary = WorkerExitDiagnostics.latestSummary(context)
            delay(500)
        }
    }

    val busy = jobState?.state == "RUNNING" || jobState?.state == "CANCELLING"

    Column(
        Modifier.fillMaxWidth().widthIn(max = 900.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sistema", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Diagnóstico do build e do runtime DSP.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusLine("Versão", BuildConfig.VERSION_NAME)
                StatusLine("Android", "${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}")
                StatusLine("Baseline funcional", "Linux v5.23")
                StatusLine("Separação", "Demucs htdemucs_6s")
                StatusLine("Worker DSP", ":media isolado")
                StatusLine("ABI inicial", "arm64-v8a")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Áudio e separação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusLine("Inspeção WAV", "Nativa")
                StatusLine("FFmpeg", "Áudio / SAF")
                StatusLine("Demucs htdemucs_6s", "Implementado")
                val blasThreads = DemucsThreadPolicy.resolve()
                val threadWord = if (blasThreads == 1) "thread" else "threads"
                StatusLine("BLAS", "OpenBLAS 0.3.34 • $blasThreads $threadWord padrão")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Execução em segundo plano",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Este teste valida Foreground Service, persistência do job, notificação e wake lock sem iniciar DSP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        launchError = null
                        runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, MediaProcessingService::class.java)
                                    .setAction(MediaProcessingService.ACTION_SELF_TEST),
                            )
                        }.onFailure { error ->
                            launchError =
                                "Não foi possível iniciar o teste: " +
                                    (error.message ?: error::class.java.simpleName)
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(if (busy) "Processamento em andamento" else "Testar execução em segundo plano")
                }

                jobState?.let { job ->
                    StatusLine("Último job", "${job.state} • ${job.progress}%")
                    Text(job.message, style = MaterialTheme.typography.bodySmall)
                }
                workerExitSummary?.let {
                    Text(
                        "Última saída do worker: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                launchError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(name: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(name, modifier = Modifier.weight(0.48f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.52f), fontWeight = FontWeight.SemiBold)
    }
}
