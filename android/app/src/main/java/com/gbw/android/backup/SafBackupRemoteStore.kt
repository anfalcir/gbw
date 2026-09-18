package com.gbw.android.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gbw.android.project.ProjectHashing
import com.gbw.android.project.ProjectJson
import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.ProjectSnapshot
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class RemoteFile(
    val relativePath: String,
    val blobName: String,
    val size: Long,
    val sha256: String,
)

internal data class RemoteRevision(
    val projectId: String,
    val revisionId: String,
    val updatedAtEpochMs: Long,
    val projectName: String,
    val manifestUri: Uri,
    val revisionDirUri: Uri,
    val files: List<RemoteFile>,
)

internal class SafBackupRemoteStore(
    context: Context,
    private val treeUri: Uri,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val treeRoot: Uri by lazy {
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    }

    suspend fun probeDestination() {
        val payload = ("GBW-PROBE-" + UUID.randomUUID()).toByteArray(Charsets.UTF_8)
        val name = ".gbw-probe-" + UUID.randomUUID() + ".bin"
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, treeRoot, "application/octet-stream", name)) {
            "A pasta escolhida não permite criar arquivos."
        }
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(payload); it.flush() }
            val read = resolver.openInputStream(uri)!!.use { it.readBytes() }
            require(read.contentEquals(payload)) { "A pasta não preservou corretamente os bytes do probe." }
        } finally {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
    }

    fun initializeRoot() {
        val marker = findChild(treeRoot, ROOT_MARKER)
        if (marker == null) {
            writeTextNew(treeRoot, ROOT_MARKER, "application/json",
                JSONObject().put("schemaVersion", 1).put("producer", "GBW").put("kind", "gbw-backup-root").toString(2))
        } else {
            val json = JSONObject(readText(marker))
            require(json.optString("producer") == "GBW" && json.optString("kind") == "gbw-backup-root") {
                "A pasta contém um marker incompatível com o GBW."
            }
        }
        ensureDir(treeRoot, PROJECTS_DIR)
    }

    suspend fun uploadSnapshot(
        snapshot: ProjectSnapshot,
        onProgress: (Long, Long, String) -> Unit = { _, _, _ -> },
    ): RemoteRevision {
        initializeRoot()
        targetedLookup(snapshot.project.projectId, snapshot.revisionId)?.let { return it }

        val projects = ensureDir(treeRoot, PROJECTS_DIR)
        val projectDir = ensureDir(projects, snapshot.project.projectId)
        val blobsDir = ensureDir(projectDir, BLOBS_DIR)
        val revisionsDir = ensureDir(projectDir, REVISIONS_DIR)
        val totalBytes = snapshot.files.sumOf { it.size }.coerceAtLeast(1L)
        var completedBytes = 0L
        val remoteFiles = mutableListOf<RemoteFile>()

        snapshot.files.forEach { artifact ->
            val local = File(snapshot.root, artifact.relativePath)
            require(local.isFile && local.length() == artifact.size)
            val blobName = blobName(artifact.relativePath, artifact.sha256)
            var blobUri = findChild(blobsDir, blobName)
            if (blobUri == null || !verifyDirect(blobUri, artifact.size, artifact.sha256)) {
                if (blobUri != null) runCatching { DocumentsContract.deleteDocument(resolver, blobUri) }
                blobUri = requireNotNull(DocumentsContract.createDocument(resolver, blobsDir, mimeFor(local), blobName))
                resolver.openOutputStream(blobUri, "w")!!.use { output ->
                    FileInputStream(local).buffered(COPY_BUFFER).use { input ->
                        val buffer = ByteArray(COPY_BUFFER)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            completedBytes += n
                            onProgress(completedBytes, totalBytes, artifact.relativePath)
                        }
                        output.flush()
                    }
                }
                require(verifyDirect(blobUri, artifact.size, artifact.sha256)) {
                    "Falha de integridade após upload: ${artifact.relativePath}"
                }
            } else {
                completedBytes += artifact.size
                onProgress(completedBytes, totalBytes, artifact.relativePath)
            }
            remoteFiles += RemoteFile(artifact.relativePath, blobName, artifact.size, artifact.sha256)
        }

        val revisionName = "v_" + snapshot.revisionId
        findChild(revisionsDir, revisionName)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        val revisionDir = requireNotNull(DocumentsContract.createDocument(
            resolver, revisionsDir, DocumentsContract.Document.MIME_TYPE_DIR, revisionName
        ))
        val manifestText = remoteManifest(snapshot.project, snapshot.revisionId, remoteFiles)
        val manifestUri = writeTextNew(revisionDir, MANIFEST, "application/json", manifestText)
        val manifestHash = ProjectHashing.sha256(manifestText)
        val commitText = JSONObject()
            .put("schemaVersion", 1)
            .put("projectId", snapshot.project.projectId)
            .put("revisionId", snapshot.revisionId)
            .put("manifestSha256", manifestHash)
            .put("committedAtEpochMs", System.currentTimeMillis())
            .toString(2)
        val commitUri = writeTextNew(revisionDir, COMMIT, "application/json", commitText)

        require(ProjectHashing.sha256(readText(manifestUri)) == manifestHash) { "Manifest remoto não confirmou integridade." }
        val commit = JSONObject(readText(commitUri))
        require(commit.getString("revisionId") == snapshot.revisionId && commit.getString("manifestSha256") == manifestHash)

        // Current pointer is advisory; commit marker + manifest remain authoritative.
        replaceText(projectDir, CURRENT, JSONObject()
            .put("projectId", snapshot.project.projectId)
            .put("revisionId", snapshot.revisionId)
            .put("updatedAtEpochMs", snapshot.project.updatedAtEpochMs)
            .toString(2))

        val committed = RemoteRevision(
            snapshot.project.projectId,
            snapshot.revisionId,
            snapshot.project.updatedAtEpochMs,
            snapshot.project.name,
            manifestUri,
            revisionDir,
            remoteFiles,
        )
        cleanupObsolete(projectDir, revisionsDir, blobsDir, committed)
        return committed
    }

    suspend fun targetedLookup(projectId: String, revisionId: String, attempts: Int = 5): RemoteRevision? {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            findRevision(projectId, revisionId)?.let { return it }
            if (attempt + 1 < attempts) delay(SETTLING_DELAY_MS)
        }
        return null
    }

    suspend fun scanValid(): List<RemoteRevision> {
        initializeRoot()
        val projects = ensureDir(treeRoot, PROJECTS_DIR)
        val result = mutableListOf<RemoteRevision>()
        listChildren(projects).filter { it.isDirectory }.forEach { child ->
            val projectId = child.name
            if (!com.gbw.android.project.isCanonicalProjectId(projectId)) return@forEach
            val projectDir = child.uri
            val currentId = findChild(projectDir, CURRENT)?.let {
                runCatching { JSONObject(readText(it)).optString("revisionId") }.getOrNull()
            }.orEmpty()
            val revision = if (currentId.isNotBlank()) {
                validateRevision(projectId, currentId)
            } else {
                val revisionsDir = findChild(projectDir, REVISIONS_DIR) ?: return@forEach
                listChildren(revisionsDir).filter { it.isDirectory && it.name.startsWith("v_") }
                    .mapNotNull { validateRevision(projectId, it.name.removePrefix("v_")) }
                    .maxByOrNull { it.updatedAtEpochMs }
            }
            if (revision != null) result += revision
        }
        return result
    }

    suspend fun restoreRevision(remote: RemoteRevision, stagingRoot: File) {
        stagingRoot.deleteRecursively()
        require(stagingRoot.mkdirs())
        val projectDir = requireNotNull(projectDir(remote.projectId))
        val blobsDir = requireNotNull(findChild(projectDir, BLOBS_DIR))
        try {
            remote.files.forEach { rf ->
                val blob = requireNotNull(findChild(blobsDir, rf.blobName)) { "Blob remoto ausente: ${rf.relativePath}" }
                val target = File(stagingRoot, rf.relativePath)
                target.parentFile?.mkdirs()
                resolver.openInputStream(blob)!!.use { input ->
                    FileOutputStream(target).buffered(COPY_BUFFER).use { output -> input.copyTo(output, COPY_BUFFER) }
                }
                require(target.length() == rf.size && ProjectHashing.sha256(target) == rf.sha256) {
                    "Integridade do restore falhou: ${rf.relativePath}"
                }
            }
            val project = ProjectJson.decode(File(stagingRoot, "project.json").readText(Charsets.UTF_8))
            require(project.projectId == remote.projectId)
        } catch (error: Exception) {
            stagingRoot.deleteRecursively()
            throw error
        }
    }

    fun deleteProject(projectId: String) {
        val dir = projectDir(projectId) ?: return
        require(DocumentsContract.deleteDocument(resolver, dir)) { "O provider não confirmou a exclusão remota." }
    }

    private fun findRevision(projectId: String, revisionId: String): RemoteRevision? {
        val projectDir = projectDir(projectId) ?: return null
        val revisions = findChild(projectDir, REVISIONS_DIR) ?: return null
        val revisionDir = findChild(revisions, "v_$revisionId") ?: return null
        return validateRevision(projectId, revisionId, revisionDir)
    }

    private fun validateRevision(projectId: String, revisionId: String, knownDir: Uri? = null): RemoteRevision? =
        runCatching {
            val revisionDir = knownDir ?: run {
                val projectDir = requireNotNull(projectDir(projectId))
                val revisions = requireNotNull(findChild(projectDir, REVISIONS_DIR))
                requireNotNull(findChild(revisions, "v_$revisionId"))
            }
            val manifestUri = requireNotNull(findChild(revisionDir, MANIFEST))
            val commitUri = requireNotNull(findChild(revisionDir, COMMIT))
            val manifestText = readText(manifestUri)
            val manifestHash = ProjectHashing.sha256(manifestText)
            val commit = JSONObject(readText(commitUri))
            require(commit.getString("projectId") == projectId)
            require(commit.getString("revisionId") == revisionId)
            require(commit.getString("manifestSha256") == manifestHash)
            val manifest = JSONObject(manifestText)
            require(manifest.getString("projectId") == projectId)
            require(manifest.getString("revisionId") == revisionId)
            val files = parseFiles(manifest)
            val projectDir = requireNotNull(projectDir(projectId))
            val blobs = requireNotNull(findChild(projectDir, BLOBS_DIR))
            files.forEach { rf ->
                val uri = requireNotNull(findChild(blobs, rf.blobName))
                require(verifyDirect(uri, rf.size, rf.sha256))
            }
            require(files.any { it.relativePath == "project.json" })
            RemoteRevision(
                projectId,
                revisionId,
                manifest.getLong("updatedAtEpochMs"),
                manifest.optString("projectName", "Projeto"),
                manifestUri,
                revisionDir,
                files,
            )
        }.getOrNull()

    private fun remoteManifest(project: ProjectManifest, revisionId: String, files: List<RemoteFile>): String {
        val array = JSONArray()
        files.sortedBy { it.relativePath }.forEach { f ->
            array.put(JSONObject()
                .put("relativePath", f.relativePath)
                .put("blobName", f.blobName)
                .put("size", f.size)
                .put("sha256", f.sha256))
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("producer", "GBW")
            .put("projectId", project.projectId)
            .put("projectName", project.name)
            .put("artist", project.artist)
            .put("song", project.song)
            .put("updatedAtEpochMs", project.updatedAtEpochMs)
            .put("revisionId", revisionId)
            .put("files", array)
            .toString(2)
    }

    private fun parseFiles(manifest: JSONObject): List<RemoteFile> {
        val a = manifest.getJSONArray("files")
        return buildList {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val relative = com.gbw.android.project.normalizeRelativePath(j.getString("relativePath"))
                add(RemoteFile(relative, j.getString("blobName"), j.getLong("size"), j.getString("sha256")))
            }
        }
    }

    private fun cleanupObsolete(projectDir: Uri, revisionsDir: Uri, blobsDir: Uri, current: RemoteRevision) {
        listChildren(revisionsDir).filter { it.isDirectory && it.uri != current.revisionDirUri }.forEach {
            runCatching { DocumentsContract.deleteDocument(resolver, it.uri) }
        }
        val keep = current.files.map { it.blobName }.toSet()
        listChildren(blobsDir).filter { !it.isDirectory && it.name !in keep }.forEach {
            runCatching { DocumentsContract.deleteDocument(resolver, it.uri) }
        }
        // Keep only current pointer and required directories under project root.
        listChildren(projectDir).filter {
            it.name !in setOf(BLOBS_DIR, REVISIONS_DIR, CURRENT)
        }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.uri) } }
    }

    private fun verifyDirect(uri: Uri, expectedSize: Long, expectedSha: String): Boolean = runCatching {
        var size = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)!!.buffered(COPY_BUFFER).use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                digest.update(buffer, 0, n)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        size == expectedSize && sha == expectedSha
    }.getOrDefault(false)

    private fun projectDir(projectId: String): Uri? {
        val projects = findChild(treeRoot, PROJECTS_DIR) ?: return null
        return findChild(projects, projectId)
    }

    private fun ensureDir(parent: Uri, name: String): Uri =
        findChild(parent, name)
            ?: requireNotNull(DocumentsContract.createDocument(
                resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name
            ))

    private fun writeTextNew(parent: Uri, name: String, mime: String, text: String): Uri {
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, parent, mime, name))
        resolver.openOutputStream(uri, "w")!!.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
        return uri
    }

    private fun replaceText(parent: Uri, name: String, text: String): Uri {
        findChild(parent, name)?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        return writeTextNew(parent, name, "application/json", text)
    }

    private fun readText(uri: Uri): String =
        resolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private data class Child(val uri: Uri, val name: String, val isDirectory: Boolean)

    private fun findChild(parent: Uri, name: String): Uri? =
        listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun listChildren(parent: Uri): List<Child> {
        val docId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return resolver.query(children, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    add(Child(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        name,
                        mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    ))
                }
            }
        } ?: emptyList()
    }

    private fun blobName(relativePath: String, sha: String): String {
        val base = relativePath.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._+-]"), "_").take(80)
        return sha.take(20) + "_" + base
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private companion object {
        const val ROOT_MARKER = "GBW_ROOT.json"
        const val PROJECTS_DIR = "projects"
        const val BLOBS_DIR = "files"
        const val REVISIONS_DIR = "revisions"
        const val CURRENT = "current.json"
        const val MANIFEST = "manifest.json"
        const val COMMIT = "commit.json"
        const val COPY_BUFFER = 1024 * 1024
        const val SETTLING_DELAY_MS = 700L
    }
}
