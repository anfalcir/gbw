package com.gbw.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gbw.android.background.JobHistoryEntry
import com.gbw.android.background.JobHistoryStore
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
internal fun LogsScreen() {
    val context = LocalContext.current
    val historyStore = remember(context) { JobHistoryStore(context) }
    val projectRepository = remember(context) { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<JobHistoryEntry>()) }
    var projects by remember { mutableStateOf<Map<String, ProjectManifest>>(emptyMap()) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        message = "Log copiado."
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            entries = withContext(Dispatchers.IO) { historyStore.list() }
            projects = withContext(Dispatchers.IO) {
                projectRepository.list().associateBy { it.projectId }
            }
            delay(1_000)
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Logs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Histórico das tarefas executadas pelo GBW. Erros e interrupções ficam registrados para diagnóstico.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    copyToClipboard(
                        "GBW logs",
                        entries.joinToString("\n\n") { formatLogEntry(it, projects[it.projectId]) },
                    )
                },
                enabled = entries.isNotEmpty(),
            ) {
                Text("Copiar tudo")
            }
            TextButton(
                onClick = { confirmClear = true },
                enabled = entries.isNotEmpty(),
            ) {
                Text("Limpar histórico")
            }
        }

        if (entries.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nenhum log registrado", fontWeight = FontWeight.SemiBold)
                    Text(
                        "As próximas tarefas de fonte, separação, exportação e diagnóstico aparecerão aqui.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        entries.forEach { entry ->
            val project = entry.projectId?.let(projects::get)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(entry.label.ifBlank { entry.type }, fontWeight = FontWeight.SemiBold)
                        Text(
                            jobStateLabel(entry.state),
                            color = if (entry.state == "ERROR" || entry.state == "INTERRUPTED") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                            .format(Date(entry.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Projeto: " + projectLabel(entry.projectId, project),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(entry.message.ifBlank { "Sem detalhes adicionais." })
                    Text(
                        "Job: " + entry.jobId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(
                                "GBW log",
                                formatLogEntry(entry, project),
                            )
                        },
                    ) {
                        Text("Copiar")
                    }
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Os registros de tarefas serão removidos deste dispositivo.") },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { historyStore.clear() }
                            entries = emptyList()
                            confirmClear = false
                            message = "Histórico limpo."
                        }
                    }
                ) {
                    Text("Limpar")
                }
            },
        )
    }
}

private fun projectLabel(projectId: String?, project: ProjectManifest?): String =
    when {
        project != null -> project.name
        projectId != null -> projectId
        else -> "sem projeto"
    }

private fun jobStateLabel(state: String): String = when (state) {
    "RUNNING" -> "Em andamento"
    "CANCELLING" -> "Cancelando"
    "SUCCESS" -> "Concluído"
    "CANCELLED" -> "Cancelado"
    "ERROR" -> "Erro"
    "INTERRUPTED" -> "Interrompido"
    else -> state
}

private fun formatLogEntry(entry: JobHistoryEntry, project: ProjectManifest?): String =
    buildString {
        append(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(Date(entry.updatedAt))
        )
        append(" | ").append(entry.label.ifBlank { entry.type })
        append(" | ").append(jobStateLabel(entry.state))
        append("\nProjeto: ").append(projectLabel(entry.projectId, project))
        append("\nJob: ").append(entry.jobId)
        append("\n").append(entry.message.ifBlank { "Sem detalhes adicionais." })
    }
