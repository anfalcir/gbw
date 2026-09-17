package com.gbw.android.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gbw.android.audio.AudioInspectionDispatcher
import com.gbw.android.background.JobStore
import com.gbw.android.background.MediaProcessingService
import com.gbw.android.domain.AudioInspection
import com.gbw.android.domain.AudioKind
import com.gbw.android.domain.FilePitchRules
import com.gbw.android.domain.OutputFormat
import com.gbw.android.domain.QualityStatus
import com.gbw.android.domain.SeparationMode
import com.gbw.android.domain.Tunings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var page by rememberSaveable { mutableStateOf(AppPage.FILE_PITCH.name) }
            val current = AppPage.valueOf(page)
            val configuration = LocalConfiguration.current
            val wide = configuration.screenWidthDp >= 840
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SideBar(current, onSelect = { page = it.name }, Modifier.width(290.dp).fillMaxHeight())
                    Divider(Modifier.fillMaxHeight().width(1.dp))
                    PageContent(current, Modifier.weight(1f))
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
                    ) { padding -> PageContent(current, Modifier.padding(padding)) }
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
        Text("Android 6.0.0-alpha1", style = MaterialTheme.typography.bodySmall)
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
private fun PageContent(page: AppPage, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(20.dp)) {
        when (page) {
            AppPage.SOURCE -> PlaceholderScreen("Fonte", "Aquisição local/online será conectada após o gate de download Android.")
            AppPage.SEPARATION -> SeparationScreen()
            AppPage.TUNING -> PlaceholderScreen("Afinação & Pitch", "Regras de afinação da v5.23 já foram portadas para o domínio Android.")
            AppPage.EXPORT -> PlaceholderScreen("Exportação", "A estrutura de exportação será ligada aos engines de áudio após FFmpeg/R3.")
            AppPage.PROJECTS -> PlaceholderScreen("Projetos", "Persistência cross-platform/SAF entra no M3, preservando a v5.23 como referência.")
            AppPage.LOGS -> PlaceholderScreen("Logs", "Logs de jobs e processamento serão persistidos por operação.")
            AppPage.FILE_PITCH -> FilePitchScreen()
            AppPage.SETTINGS -> PlaceholderScreen("Configurações", "Separação Rápida é o padrão Android; demais preferências seguirão a v5.23.")
            AppPage.SYSTEM -> SystemScreen()
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
private fun SeparationScreen() {
    var mode by rememberSaveable { mutableStateOf(SeparationMode.androidDefault.name) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Separação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("No Android, a opção rápida é o padrão para reduzir tempo, RAM, temperatura e consumo de bateria.")
        SeparationMode.entries.forEach { item ->
            OutlinedCard(onClick = { mode = item.name }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == item.name, onClick = { mode = item.name })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(item.publicLabel, fontWeight = FontWeight.SemiBold)
                        Text(when (item) {
                            SeparationMode.QUICK -> "Demucs htdemucs_6s • uso diário recomendado"
                            SeparationMode.HIGH_QUALITY -> "BS-RoFormer-SW • mais pesado e demorado"
                            SeparationMode.COMPARE -> "Executa as duas opções para comparação"
                        })
                    }
                }
            }
        }
    }
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
    var jobState by remember { mutableStateOf(jobStore.load()) }

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
            jobState = jobStore.load()
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
    var jobState by remember { mutableStateOf(JobStore(context).load()) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Sistema", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Checkpoint Android 6.0.0-alpha1")
        StatusLine("Domínio v5.23", "Portado")
        StatusLine("Separação padrão", "Rápida / Demucs")
        StatusLine("Inspeção WAV", "Nativa")
        StatusLine("Foreground mediaProcessing", "Implementado")
        StatusLine("FFmpeg", "Inspeção + preparação/encode do Pitch")
        StatusLine("Rubber Band R3", "v4.0.0 via NDK/JNI • arm64-v8a")
        StatusLine("Pitch de Arquivo", "Pipeline R3 integrado ao serviço")
        StatusLine("Demucs htdemucs_6s", "Próximo gate")
        OutlinedButton(onClick = {
            val intent = Intent(context, MediaProcessingService::class.java).setAction(MediaProcessingService.ACTION_SELF_TEST)
            ContextCompat.startForegroundService(context, intent)
            jobState = JobStore(context).load()
        }) { Text("Testar execução em segundo plano") }
        jobState?.let { Text("Último job: ${it.state} • ${it.progress}% • ${it.message}") }
    }
}

@Composable
private fun StatusLine(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
