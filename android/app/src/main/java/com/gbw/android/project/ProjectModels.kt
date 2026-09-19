package com.gbw.android.project

import java.text.Normalizer
import java.util.UUID

data class DurableArtifact(
    val relativePath: String,
    val size: Long,
    val sha256: String,
    val modifiedAtEpochMs: Long,
)

data class ManagedSource(
    val originalRelativePath: String,
    val preparedRelativePath: String? = null,
    val provenanceUri: String? = null,
    val sourceUrl: String? = null,
    val title: String = "",
    val formatId: String = "",
    val durationSeconds: Double = 0.0,
)

data class SeparationState(
    val engine: String = "htdemucs_6s",
    val jobId: String,
    val completedAtEpochMs: Long,
    val frames: Long,
    val elapsedMillis: Long,
    val runtimeIdentity: String,
    val blasThreads: Int,
    val stems: Map<String, String>,
)

data class PitchState(
    val semitones: Int = 0,
    val vocalFormants: Boolean = true,
)

data class ExportArtifact(
    val role: String,
    val variant: String,
    val relativePath: String,
    val format: String,
    val sampleRate: Int,
    val channels: Int,
    val durationFrames: Long,
    val size: Long,
    val sha256: String,
)

data class ExportState(
    val exportId: String,
    val revision: Long,
    val pitchSemitones: Int,
    val outputFormat: String,
    val artifacts: List<ExportArtifact>,
    val manifestRelativePath: String,
)

data class ProjectManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val projectId: String,
    val name: String,
    val artist: String = "",
    val song: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val workflowStage: String = "SOURCE",
    val source: ManagedSource? = null,
    val separation: SeparationState? = null,
    val pitch: PitchState = PitchState(),
    val export: ExportState? = null,
    val inventory: List<DurableArtifact> = emptyList(),
    val lastSyncedRevisionId: String? = null,
) {
    init {
        require(isCanonicalProjectId(projectId)) { "projectId inválido" }
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) { "Schema de projeto não suportado: $schemaVersion" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun new(
            name: String,
            artist: String = "",
            song: String = "",
            nowEpochMs: Long = System.currentTimeMillis(),
        ): ProjectManifest {
            val normalizedArtist = normalizeProjectText(artist)
            val normalizedSong = normalizeProjectText(song)
            val automaticName = automaticProjectName(normalizedArtist, normalizedSong)
            return ProjectManifest(
                projectId = UUID.randomUUID().toString(),
                name = sanitizeProjectName(automaticName.ifBlank { name }),
                artist = normalizedArtist,
                song = normalizedSong,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            )
        }
    }
}

fun isCanonicalProjectId(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

fun normalizeProjectText(value: String): String {
    val collapsed = value.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isBlank()) return ""
    val lower = collapsed.lowercase()
    val out = StringBuilder(lower.length)
    var capitalizeNext = true
    lower.forEach { ch ->
        if (ch.isLetter()) {
            if (capitalizeNext) out.append(ch.titlecase()) else out.append(ch)
            capitalizeNext = false
        } else {
            out.append(ch)
            capitalizeNext = !ch.isDigit()
        }
    }
    return out.toString()
}

fun automaticProjectName(artist: String, song: String): String =
    listOf(normalizeProjectText(artist), normalizeProjectText(song))
        .filter { it.isNotBlank() }
        .joinToString(" - ")

fun foldProjectSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .trim()
        .replace(Regex("\\s+"), " ")

fun projectMatchesSearch(project: ProjectManifest, query: String): Boolean {
    val terms = foldProjectSearchText(query).split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = foldProjectSearchText(
        listOf(project.artist, project.song, project.name).joinToString(" ")
    )
    return terms.all(haystack::contains)
}

fun nextProjectCopySong(song: String, existingSongs: Collection<String>): String {
    val base = normalizeProjectText(song).ifBlank { "Projeto" }
    val occupied = existingSongs.map(::foldProjectSearchText).toSet()
    var index = 1
    while (true) {
        val suffix = if (index == 1) " (Cópia)" else " (Cópia $index)"
        val candidate = base + suffix
        if (foldProjectSearchText(candidate) !in occupied) return candidate
        index += 1
    }
}

fun projectWorkflowStageLabel(stage: String): String = when (stage.uppercase()) {
    "SOURCE" -> "Fonte"
    "SEPARATION" -> "Separação"
    "TUNING" -> "Afinação (legado)"
    "EXPORT" -> "Exportação"
    else -> stage.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun sanitizeProjectName(value: String): String {
    val cleaned = value.trim().replace(Regex("[\u0000-\u001f]"), " ").replace(Regex("\\s+"), " ")
    return cleaned.take(120).ifBlank { "Projeto sem nome" }
}

fun normalizeRelativePath(value: String): String {
    val normalized = value.replace('\\', '/').trimStart('/')
    require(normalized.isNotBlank()) { "Caminho relativo vazio" }
    val parts = normalized.split('/').filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "Caminho relativo vazio" }
    require(parts.none { it == "." || it == ".." }) { "Path traversal bloqueado: $value" }
    require(parts.none { ':' in it }) { "Caminho relativo inválido: $value" }
    return parts.joinToString("/")
}

fun ProjectManifest.canonicalRevisionState(): String = buildString {
    append("schema=").append(schemaVersion).append('\n')
    append("projectId=").append(projectId).append('\n')
    append("name=").append(name).append('\n')
    append("artist=").append(artist).append('\n')
    append("song=").append(song).append('\n')
    append("updatedAt=").append(updatedAtEpochMs).append('\n')
    append("stage=").append(workflowStage).append('\n')
    append("pitch=").append(pitch.semitones).append(':').append(pitch.vocalFormants).append('\n')
    source?.let {
        append("source=").append(it.originalRelativePath).append('|')
            .append(it.preparedRelativePath.orEmpty()).append('|')
            .append(it.sourceUrl.orEmpty()).append('|')
            .append(it.title).append('|').append(it.formatId).append('|')
            .append(it.durationSeconds).append('\n')
    }
    separation?.let {
        append("sep=").append(it.engine).append('|').append(it.jobId).append('|')
            .append(it.completedAtEpochMs).append('|').append(it.frames).append('|')
            .append(it.runtimeIdentity).append('|').append(it.blasThreads).append('\n')
        it.stems.toSortedMap().forEach { (role, path) -> append("stem=").append(role).append('|').append(path).append('\n') }
    }
    export?.let {
        append("export=").append(it.exportId).append('|').append(it.revision).append('|')
            .append(it.pitchSemitones).append('|').append(it.outputFormat).append('|')
            .append(it.manifestRelativePath).append('\n')
        it.artifacts.sortedWith(compareBy<ExportArtifact> { a -> a.variant }.thenBy { a -> a.role }).forEach { a ->
            append("exportArtifact=").append(a.variant).append('|').append(a.role).append('|')
                .append(a.relativePath).append('|').append(a.sha256).append('|').append(a.size).append('\n')
        }
    }
    inventory.sortedBy { it.relativePath }.forEach { a ->
        append("file=").append(a.relativePath).append('|').append(a.size).append('|').append(a.sha256).append('\n')
    }
}
