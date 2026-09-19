package com.gbw.android.backup

import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.automaticProjectName
import com.gbw.android.project.sanitizeProjectName

internal object BackupLayout {
    const val VERSION = 2
    const val PROJECTS_DIR = "Projetos"
    const val LEGACY_PROJECTS_DIR = "projects"
    const val PROJECT_META_DIR = "Projeto"
    const val SOURCE_DIR = "Fonte"
    const val STEMS_DIR = "Separacao - Stems"
    const val EXPORTS_DIR = "Exports"
    const val REVISIONS_DIR = "Revisoes"
    const val PROJECT_ID_FILE = "project-id.json"
    const val CURRENT_FILE = "current.json"

    fun projectFolderName(project: ProjectManifest): String {
        val automatic = automaticProjectName(project.artist, project.song)
        val preferred = automatic.takeIf { it.isNotBlank() }
            ?: project.name.takeIf { it.isNotBlank() && it != "Projeto sem nome" }
        return safeSegment(preferred.orEmpty().ifBlank { "Projeto" })
    }

    fun artifactRemotePath(relativePath: String, sha256: String): String {
        val rel = relativePath.replace('\\', '/').trimStart('/')
        val parts = rel.split('/').filter { it.isNotBlank() }
        require(parts.isNotEmpty())
        val prefix: String
        val rest: List<String>
        when (parts.first()) {
            "source" -> { prefix = SOURCE_DIR; rest = parts.drop(1) }
            "stems" -> { prefix = STEMS_DIR; rest = parts.drop(1) }
            "exports" -> {
                prefix = EXPORTS_DIR
                val payload = if (parts.size >= 3) parts.drop(2) else parts.drop(1)
                rest = payload.map { segment ->
                    when {
                        segment == "original" -> "Original"
                        segment.startsWith("pitch_") -> "Ajustado " + segment.removePrefix("pitch_")
                        segment == "export_manifest.json" -> "export_manifest.json"
                        else -> segment
                    }
                }
            }
            "project.json" -> { prefix = PROJECT_META_DIR; rest = listOf("Dados", "project.json") }
            else -> { prefix = PROJECT_META_DIR; rest = listOf("Dados") + parts }
        }
        val safeRest = rest.map(::safeSegment).toMutableList()
        require(safeRest.isNotEmpty())
        val last = safeRest.removeLast()
        safeRest += withHashSuffix(last, sha256)
        return (listOf(prefix) + safeRest).joinToString("/")
    }

    fun safeSegment(value: String): String {
        val cleaned = value
            .trim()
            .replace(Regex("[\\/:*?\"<>|]"), " - ")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return sanitizeProjectName(cleaned).take(100).ifBlank { "Item" }
    }

    private fun withHashSuffix(fileName: String, sha256: String): String {
        val hash = sha256.lowercase().take(12)
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && dot < fileName.lastIndex) {
            fileName.substring(0, dot) + "__" + hash + fileName.substring(dot)
        } else {
            fileName + "__" + hash
        }
    }
}
