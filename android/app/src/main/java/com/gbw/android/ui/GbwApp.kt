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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.AudioKind
import com.gbw.android.domain.FilePitchRules
import com.gbw.android.domain.OutputFormat
import com.gbw.android.domain.QualityStatus
import com.gbw.android.domain.RankedSourceCandidate
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchRequest
import com.gbw.android.domain.SourceSearchLinks
import com.gbw.android.domain.Tunings
import com.gbw.android.separation.DemucsRuntimeMonitor
import com.gbw.android.separation.DemucsThreadPolicy
import com.gbw.android.separation.SeparationResultFiles
import com.gbw.android.separation.SeparationResultStore
import com.gbw.android.separation.SeparationStemExporter
import com.gbw.android.separation.StemPreviewPlayer
import com.gbw.android.separation.ValidatedSeparationResult
import com.gbw.android.source.SourceSearchCoordinator
import com.gbw.android.source.PreparedSourceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SEPARATION_STARTED_MESSAGE = "Separação iniciada em segundo plano."

private enum class AppPage(val title: String, val group: String) {
    SOURCE("1. Fonte", "PROCESSO"),
    SEPARATION("2. Separação", "PROCESSO"),
    TUNING("3. Afinação & Pitch", "PROCESSO"),
    EXPORT("4. Exportação", "PROCESSO"),
    PROJECTS("Projetos", "GERENCIAMENTO"),
    LOGS("Logs", "GERENCIAMENTO"),
    FILE_PITCH("Pitch de Arquivo", "FERRAMENTAS"),
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
            val current = AppPage.valueOf(page)
            val configuration = LocalConfiguration.current
            val wide = configuration.screenWidthDp >= 840
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SideBar(current, onSelect = { page = it.name }, Modifier.width(260.dp).fillMaxHeight())
                    Divider(Modifier.fillMaxHeight().width(1.dp))
                    PageContent(
                        page = current,
                        sourceUri = sourceUri,
                        onSourceUriChange = { sourceUri = it },
                        onNavigate = { page = it.name },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            SideBar(current, onSelect = {
                                page = it.name
                                scope.launch { drawerState.close() }
                            }, Modifier.width(300.dp).fillMaxHeight())
                        }
                    },
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(current.title) },
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
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBar(current: AppPage, onSelect: (AppPage) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(modifier.padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GBW", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
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
                )
                AppPage.SEPARATION -> SeparationScreen(
                    initialUriText = sourceUri,
                    onUriChanged = onSourceUriChange,
                )
                AppPage.TUNING -> PlaceholderScreen("Afinação & Pitch", "Regras de afinação da v5.23 já foram portadas para o domínio Android.")
                AppPage.EXPORT -> PlaceholderScreen("Exportação", "A estrutura de exportação será ligada aos engines de áudio após FFmpeg/R3.")
                AppPage.PROJECTS -> PlaceholderScreen("Projetos", "Persistência cross-platform/SAF entra no M3, preservando a v5.23 como referência.")
                AppPage.LOGS -> PlaceholderScreen("Logs", "Logs de jobs e processamento serão persistidos por operação.")
                AppPage.FILE_PITCH -> FilePitchScreen()
                AppPage.SETTINGS -> PlaceholderScreen("Configurações", "As preferências do fluxo Android permanecem alinhadas à v5.23 sem seleção de motor de separação.")
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
    var sourceJobState by remember { mutableStateOf(sourceJobStore.loadReconciled()) }
    var consumedPreparedJobId by rememberSaveable { mutableStateOf("") }
    var autoContinuePrepared by rememberSaveable { mutableStateOf(false) }
    val selectedDisplayName = remember(selectedUriText) {
        selectedUriText.takeIf { it.isNotBlank() }?.let { audioDisplayName(context, it) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            onSelectedUri(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            sourceJobState = sourceJobStore.loadReconciled()
            val job = sourceJobState
            if (
                job?.type == MediaProcessingService.SOURCE_PREPARE_TYPE &&
                job.state == "SUCCESS" &&
                job.id != consumedPreparedJobId
            ) {
                val prepared = preparedSourceStore.load()
                if (prepared?.jobId == job.id && prepared.preparedFile().isFile) {
                    consumedPreparedJobId = job.id
                    autoContinuePrepared = true
                    onSelectedUri(preparedSourceStore.contentUri(prepared).toString())
                    searchState.updateMessage("Fonte online baixada e preparada com sucesso.")
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
                Button(onClick = { picker.launch(arrayOf("audio/*")) }) {
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
                        val song = searchState.song.trim()
                        if (song.isBlank()) {
                            searchState.updateMessage("Informe o nome da música.")
                        } else {
                            searchState.beginSearch()
                            val request = SourceSearchRequest(
                                artist = searchState.artist.trim(),
                                song = song,
                                depth = SourceSearchDepth.valueOf(searchState.depthName),
                            )
                            onlineScope.launch {
                                try {
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
                            val intent = MediaProcessingService.sourcePrepareIntent(
                                context = context,
                                url = candidate.url,
                                provider = candidate.provider,
                                formatId = candidate.formatId,
                                expectedDurationSeconds = candidate.durationSeconds,
                                title = candidate.title,
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

                sourceJobState?.takeIf { it.type == MediaProcessingService.SOURCE_PREPARE_TYPE }?.let { job ->
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
                            val intent = MediaProcessingService.sourcePrepareIntent(
                                context = context,
                                url = uri.toString(),
                                provider = com.gbw.android.domain.SourceProvider.OTHER,
                                formatId = "",
                                expectedDurationSeconds = 0.0,
                                title = searchState.song.trim(),
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
            onClick = onContinue,
            enabled = selectedUriText.isNotBlank() && inspection != null && inspectionError == null && !inspecting,
        ) {
            Text("Continuar para Separação")
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
    initialUriText: String,
    onUriChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val jobStore = remember(context) { JobStore(context) }
    val resultStore = remember(context) { SeparationResultStore(context) }
    val previewPlayer = remember { StemPreviewPlayer() }
    val scope = rememberCoroutineScope()
    var availableResult by remember { mutableStateOf<ValidatedSeparationResult?>(null) }
    var playingStem by remember { mutableStateOf<String?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var uriText by rememberSaveable(initialUriText) { mutableStateOf(initialUriText) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var jobState by remember { mutableStateOf(jobStore.loadReconciled()) }
    val selectedDisplayName = remember(uriText) {
        uriText.takeIf { it.isNotBlank() }?.let { audioDisplayName(context, it) }
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            uriText = uri.toString()
            onUriChanged(uriText)
            resultMessage = null
        }
    }

    LaunchedEffect(resultMessage) {
        if (resultMessage == SEPARATION_STARTED_MESSAGE) {
            delay(3_500)
            if (resultMessage == SEPARATION_STARTED_MESSAGE) {
                resultMessage = null
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            jobState = jobStore.loadReconciled()
            val storedRecord = resultStore.load()
            if (storedRecord?.jobId != availableResult?.record?.jobId) {
                availableResult = storedRecord?.let {
                    SeparationResultFiles.validate(context.filesDir, it)
                }
            }
            if (availableResult == null) {
                availableResult = jobState
                    ?.takeIf {
                        it.state == "SUCCESS" && SeparationResultFiles.isDemucsType(it.type)
                    }
                    ?.let {
                        resultStore.recoverExisting(
                            jobId = it.id,
                            type = it.type,
                            startedAt = it.startedAt,
                            message = it.message,
                        )
                    }
            }
            delay(500)
        }
    }

    val separationJob = jobState?.takeIf { SeparationResultFiles.isDemucsType(it.type) }
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
            "O GBW Android usa exclusivamente Demucs htdemucs_6s para gerar seis stems. " +
                "O arquivo selecionado em Fonte é reaproveitado automaticamente.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Arquivo para separar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    enabled = !appJobBusy,
                ) {
                    Text(if (uriText.isBlank()) "Selecionar áudio…" else "Trocar áudio…")
                }
                if (uriText.isNotBlank()) {
                    Text(
                        selectedDisplayName ?: "Arquivo selecionado",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "No primeiro uso, o GBW baixa aproximadamente 54,9 MB do htdemucs_6s " +
                        "e valida tamanho + SHA-256 antes de processar.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "O áudio é preparado em float32 estéreo/44,1 kHz. A inferência usa um " +
                        "modelo por vez, com BLAS interno controlado e stems gravados por streaming.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = {
                val input = uriText.takeIf { it.isNotBlank() }?.let(Uri::parse)
                if (input == null) {
                    resultMessage = "Selecione um arquivo de áudio antes de iniciar."
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        MediaProcessingService.demucsIntent(context, input),
                    )
                    resultMessage = SEPARATION_STARTED_MESSAGE
                }
            },
            enabled = uriText.isNotBlank() && !appJobBusy,
        ) {
            Text("Separar")
        }

        separationJob?.let { job ->
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
        }

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
private fun FilePitchScreen() {
    val context = LocalContext.current
    val jobStore = remember(context) { JobStore(context) }
    var uriText by rememberSaveable { mutableStateOf("") }
    var inspection by remember { mutableStateOf<AudioInspection?>(null) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    var guidanceOpen by rememberSaveable { mutableStateOf(false) }
    var byTuning by rememberSaveable { mutableStateOf(true) }
    var sourceTuning by rememberSaveable { mutableStateOf("Drop D") }
    var targetTuning by rememberSaveable { mutableStateOf("Drop B") }
    var manualSemitones by rememberSaveable { mutableStateOf(-3) }
    var audioKind by rememberSaveable { mutableStateOf(AudioKind.INSTRUMENT_OR_MIX.name) }
    var outputFormat by rememberSaveable { mutableStateOf(OutputFormat.WAV_FLOAT32.name) }
    var showCaution by remember { mutableStateOf(false) }
    var cautionAcceptedForRun by rememberSaveable { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var jobState by remember { mutableStateOf(jobStore.loadReconciled()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            cautionAcceptedForRun = false
            uriText = uri.toString()
        }
    }

    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/*")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val inputUri = uriText.takeIf { it.isNotBlank() }?.let(Uri::parse)
            val currentSemitones = if (byTuning) FilePitchRules.semitonesFromTunings(sourceTuning, targetTuning) else manualSemitones
            if (inputUri == null || currentSemitones == null || !FilePitchRules.validateSemitones(currentSemitones)) {
                resultMessage = "Não foi possível iniciar: conversão/arquivo inválido."
                return@rememberLauncherForActivityResult
            }
            val intent = MediaProcessingService.filePitchIntent(
                context = context,
                inputUri = inputUri,
                outputUri = uri,
                semitones = currentSemitones,
                audioKind = AudioKind.valueOf(audioKind),
                outputFormat = OutputFormat.valueOf(outputFormat),
                cautionAccepted = cautionAcceptedForRun,
            )
            ContextCompat.startForegroundService(context, intent)
            resultMessage = "Processamento iniciado em segundo plano. Você pode sair desta tela."
        }
    }

    LaunchedEffect(uriText) {
        inspection = null
        inspectionError = null
        resultMessage = null
        if (uriText.isBlank()) return@LaunchedEffect
        inspecting = true
        try {
            inspection = AudioInspectionDispatcher.inspect(context, Uri.parse(uriText))
        } catch (e: Exception) {
            inspectionError = e.message ?: "Falha ao analisar o arquivo."
        } finally {
            inspecting = false
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            jobState = jobStore.loadReconciled()
            delay(500)
        }
    }

    val semitones = if (byTuning) FilePitchRules.semitonesFromTunings(sourceTuning, targetTuning) else manualSemitones
    val selectedFormat = OutputFormat.valueOf(outputFormat)
    val selectedInspection = inspection
    val suggestedOutputName = if (selectedInspection != null && semitones != null) {
        FilePitchRules.suggestedName(
            selectedInspection.displayName,
            if (byTuning) sourceTuning else null,
            if (byTuning) targetTuning else null,
            semitones,
            selectedFormat,
        )
    } else {
        "gbw_pitch.${selectedFormat.extension}"
    }
    val pitchJob = jobState?.takeIf { it.type == "file-pitch" }
    val jobRunning = pitchJob?.state == "RUNNING"
    val chooseOutput: (Boolean) -> Unit = { accepted ->
        cautionAcceptedForRun = accepted
        outputPicker.launch(suggestedOutputName)
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Pitch de Arquivo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Ferramenta independente: funciona mesmo sem projeto aberto.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Arquivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !jobRunning) { Text("Selecionar arquivo…") }
                if (inspecting) Text("Analisando formato e qualidade…")
                inspectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                inspection?.let { QualityCard(it) }
            }
        }

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                TextButton(onClick = { guidanceOpen = !guidanceOpen }) {
                    Text(if (guidanceOpen) "▾ Como exportar do seu DAW para o GBW?" else "▸ Como exportar do seu DAW para o GBW?")
                }
                if (guidanceOpen) {
                    Text(
                        "• Prefira WAV 32-bit float.\n" +
                            "• Mantenha a mesma taxa de amostragem da sessão.\n" +
                            "• Sessão em 44,1 kHz: mantenha 44,1 kHz. Sessão em 48 kHz: mantenha 48 kHz.\n" +
                            "• Se estiver criando a sessão, 48 kHz é uma ótima escolha.\n" +
                            "• Não faça upsampling apenas para usar o GBW.\n" +
                            "• Mono para guitarra mono; preserve estéreo quando houver efeitos estéreo.\n" +
                            "• Evite normalizar/limitar sem necessidade.\n" +
                            "• Evite MP3/AAC quando puder exportar lossless.\n" +
                            "• Para manter sincronismo, exporte desde o mesmo ponto inicial da backing/original."
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Conversão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = byTuning, onClick = { if (!jobRunning) byTuning = true }, enabled = !jobRunning)
                    Text("Por afinação")
                    Spacer(Modifier.width(20.dp))
                    RadioButton(selected = !byTuning, onClick = { if (!jobRunning) byTuning = false }, enabled = !jobRunning)
                    Text("Por semitons")
                }
                if (byTuning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SimpleDropdown("Atual", sourceTuning, Tunings.names, onSelect = { if (!jobRunning) sourceTuning = it }, modifier = Modifier.weight(1f))
                        SimpleDropdown("Destino", targetTuning, Tunings.names, onSelect = { if (!jobRunning) targetTuning = it }, modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = {
                        val old = sourceTuning
                        sourceTuning = targetTuning
                        targetTuning = old
                    }, enabled = !jobRunning) { Text("↕ Inverter") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (manualSemitones > -12) manualSemitones-- }, enabled = !jobRunning) { Text("−") }
                        Text("$manualSemitones semitons", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (manualSemitones < 12) manualSemitones++ }, enabled = !jobRunning) { Text("+") }
                    }
                }
                Text("Resultado: ${semitones?.let { if (it >= 0) "+$it semitons" else "$it semitons" } ?: "conversão global incompatível"}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo de áudio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = audioKind == AudioKind.INSTRUMENT_OR_MIX.name,
                        onClick = { if (!jobRunning) audioKind = AudioKind.INSTRUMENT_OR_MIX.name },
                        enabled = !jobRunning,
                    )
                    Text("Instrumento / Mix")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = audioKind == AudioKind.VOCAL.name,
                        onClick = { if (!jobRunning) audioKind = AudioKind.VOCAL.name },
                        enabled = !jobRunning,
                    )
                    Text("Vocal — preservar formantes")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Saída", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SimpleDropdown("Formato", selectedFormat.label, OutputFormat.entries.map { it.label }, onSelect = { label ->
                    if (!jobRunning) outputFormat = OutputFormat.entries.first { it.label == label }.name
                })
                Text("Nome sugerido: $suggestedOutputName")
                Text("O GBW renderiza e valida em armazenamento temporário antes de gravar no destino selecionado.", style = MaterialTheme.typography.bodySmall)
            }
        }

        val canApply = inspection != null && semitones != null && FilePitchRules.validateSemitones(semitones) && !jobRunning
        Button(onClick = {
            val info = inspection ?: return@Button
            if (info.status == QualityStatus.CAUTION) showCaution = true
            else chooseOutput(false)
        }, enabled = canApply) { Text("Aplicar pitch") }

        pitchJob?.let { job ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Processamento", fontWeight = FontWeight.SemiBold)
                    Text("${job.state} • ${job.progress}%")
                    Text(job.message)
                    if (job.state == "RUNNING") {
                        OutlinedButton(onClick = {
                            context.startService(
                                Intent(context, MediaProcessingService::class.java)
                                    .setAction(MediaProcessingService.ACTION_CANCEL)
                            )
                        }) { Text("Cancelar processamento") }
                    }
                }
            }
        }
        resultMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    if (showCaution) {
        val info = inspection
        AlertDialog(
            onDismissRequest = { showCaution = false },
            title = { Text("Verifique a qualidade do arquivo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O arquivo pode ser processado, mas encontramos condições que podem afetar o resultado:")
                    info?.issues?.forEach { issue ->
                        Text(issue.title, fontWeight = FontWeight.Bold)
                        Text(issue.detail)
                        Text("Ideal: ${issue.ideal}")
                        Text("Risco: ${issue.risk}")
                    }
                    Text("O GBW pode continuar usando este arquivo. A decisão permanece com você.")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCaution = false
                    picker.launch(arrayOf("audio/*"))
                }) { Text("Escolher outro arquivo") }
            },
            confirmButton = {
                Button(onClick = {
                    showCaution = false
                    chooseOutput(true)
                }) { Text("Continuar mesmo assim") }
            }
        )
    }
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
                Text("Motores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusLine("Inspeção WAV", "Nativa")
                StatusLine("FFmpeg", "Áudio / SAF")
                StatusLine("Rubber Band R3", "v4.0.0 / NDK-JNI")
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
