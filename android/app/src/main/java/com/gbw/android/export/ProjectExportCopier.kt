package com.gbw.android.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gbw.android.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

internal object ProjectExportCopier {
    suspend fun copyCurrent(
        context: Context,
        projectId: String,
        treeUri: Uri,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val repo = ProjectRepository(context)
        val project = repo.load(projectId)
        val export = requireNotNull(project.export) { "O projeto ainda não possui exportação." }
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val all = export.artifacts + com.gbw.android.project.ExportArtifact(
            role = "manifest", variant = "manifest", relativePath = export.manifestRelativePath,
            format = "json", sampleRate = 0, channels = 0, durationFrames = 0,
            size = repo.resolveProjectPath(projectId, export.manifestRelativePath).length(),
            sha256 = "",
        )
        val created = mutableListOf<Uri>()
        try {
            all.forEachIndexed { index, artifact ->
                val source = repo.resolveProjectPath(projectId, artifact.relativePath)
                val display = when (artifact.role) {
                    "backing" -> "backing." + source.extension
                    "guitar" -> "guitar." + source.extension
                    else -> "export_manifest.json"
                }
                val mime = when (source.extension.lowercase()) {
                    "flac" -> "audio/flac"
                    "wav" -> "audio/wav"
                    else -> "application/json"
                }
                val target = requireNotNull(DocumentsContract.createDocument(resolver, root, mime, display))
                created += target
                resolver.openOutputStream(target, "w")!!.use { output ->
                    FileInputStream(source).buffered(1024 * 1024).use { input -> input.copyTo(output, 1024 * 1024) }
                    output.flush()
                }
                onProgress(index + 1, all.size, display)
            }
            all.size
        } catch (error: Exception) {
            created.asReversed().forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            throw error
        }
    }
}
