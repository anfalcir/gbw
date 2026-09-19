package com.gbw.android.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.gbw.android.project.ProjectHashing
import com.gbw.android.project.ProjectJson
import com.gbw.android.project.ProjectManifest
import com.gbw.android.project.ProjectSnapshot
import com.gbw.android.project.isCanonicalProjectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class RemoteFile(
    val relativePath: String,
    val remotePath: String,
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
    val projectDirUri: Uri,
    val layoutVersion: Int,
    val files: List<RemoteFile>,
)

internal class SafBackupRemoteStore(
    context: Context,
    private val treeUri: Uri,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val dirCache = mutableMapOf<String, Uri>()
    private val projectDirCache = mutableMapOf<String, Uri>()

    private val treeRoot: Uri by lazy {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }

    suspend fun probeDestination() = withContext(Dispatchers.IO) {
        val payload = ("GBW-PROBE-" + UUID.randomUUID()).toByteArray(Charsets.UTF_8)
        val name = ".gbw-probe-" + UUID.randomUUID() + ".bin"
        val uri = requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                treeRoot,
                "application/octet-stream",
                name,
            )
        ) { "A pasta escolhida não permite criar arquivos." }
        try {
            resolver.openOutputStream(uri, "w")!!.use {
                it.write(payload)
                it.flush()
            }
            val read = resolver.openInputStream(uri)!!.use { it.readBytes() }
            require(read.contentEquals(payload)) {
                "A pasta não preservou corretamente os bytes do teste de escrita."
            }
        } finally {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
    }

    fun initializeRoot() {
        val candidates = listChildren(treeRoot)
            .filter { !it.isDirectory && it.name.startsWith("GBW_ROOT") }
        val marker = findChild(treeRoot, ROOT_MARKER)
            ?: candidates.firstOrNull { child ->
                runCatching {
                    val json = JSONObject(readText(child.uri))
                    json.optString("producer") == "GBW" &&
                        json.optString("kind") == "gbw-backup-root"
                }.getOrDefault(false)
            }?.uri
        if (marker == null) {
            writeTextNew(
                treeRoot,
                ROOT_MARKER,
                "application/json",
                JSONObject()
                    .put("schemaVersion", BackupLayout.VERSION)
                    .put("producer", "GBW")
                    .put("kind", "gbw-backup-root")
                    .toString(2),
            )
        } else {
            val json = JSONObject(readText(marker))
            require(
                json.optString("producer") == "GBW" &&
                    json.optString("kind") == "gbw-backup-root"
            ) { "A pasta contém um marker incompatível com o GBW." }
            candidates.filter { it.uri != marker }.forEach { extra ->
                val isGbwMarker = runCatching {
                    val other = JSONObject(readText(extra.uri))
                    other.optString("producer") == "GBW" &&
                        other.optString("kind") == "gbw-backup-root"
                }.getOrDefault(false)
                if (isGbwMarker) runCatching {
                    DocumentsContract.deleteDocument(resolver, extra.uri)
                }
            }
        }
        ensureDir(treeRoot, BackupLayout.PROJECTS_DIR)
    }

    suspend fun uploadSnapshot(
        snapshot: ProjectSnapshot,
        onProgress: (Long, Long, String) -> Unit = { _, _, _ -> },
    ): RemoteRevision = withContext(Dispatchers.IO) {
        initializeRoot()
        findV2Revision(snapshot.project.projectId, snapshot.revisionId)?.let {
            return@withContext it
        }

        val projectDir = ensureV2ProjectDir(snapshot.project)
        val totalBytes = snapshot.files.sumOf { it.size }.coerceAtLeast(1L)
        var completedBytes = 0L
        val remoteFiles = mutableListOf<RemoteFile>()

        snapshot.files.forEach { artifact ->
            currentCoroutineContext().ensureActive()
            val local = File(snapshot.root, artifact.relativePath)
            require(local.isFile && local.length() == artifact.size) {
                "Snapshot local incompleto: ${artifact.relativePath}"
            }
            val remotePath = BackupLayout.artifactRemotePath(
                artifact.relativePath,
                artifact.sha256,
            )
            var remoteUri = findPath(projectDir, remotePath)
            if (remoteUri == null || !verifyDirect(remoteUri, artifact.size, artifact.sha256)) {
                if (remoteUri != null) {
                    runCatching { DocumentsContract.deleteDocument(resolver, remoteUri) }
                }
                remoteUri = createFileAtPath(projectDir, remotePath, mimeFor(local))
                resolver.openOutputStream(remoteUri, "w")!!.use { output ->
                    FileInputStream(local).buffered(COPY_BUFFER).use { input ->
                        val buffer = ByteArray(COPY_BUFFER)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            completedBytes += n
                            onProgress(completedBytes, totalBytes, artifact.relativePath)
                        }
                        output.flush()
                    }
                }
                require(verifyDirect(remoteUri, artifact.size, artifact.sha256)) {
                    "Falha de integridade após upload: ${artifact.relativePath}"
                }
            } else {
                completedBytes += artifact.size
                onProgress(completedBytes, totalBytes, artifact.relativePath)
            }
            remoteFiles += RemoteFile(
                relativePath = artifact.relativePath,
                remotePath = remotePath,
                size = artifact.size,
                sha256 = artifact.sha256,
            )
        }

        val metaDir = ensureDir(projectDir, BackupLayout.PROJECT_META_DIR)
        val revisionsDir = ensureDir(metaDir, BackupLayout.REVISIONS_DIR)
        val revisionName = REVISION_PREFIX + snapshot.revisionId
        findChild(revisionsDir, revisionName)?.let {
            runCatching { DocumentsContract.deleteDocument(resolver, it) }
        }
        val revisionDir = requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                revisionsDir,
                DocumentsContract.Document.MIME_TYPE_DIR,
                revisionName,
            )
        )
        cacheDir(revisionsDir, revisionName, revisionDir)

        val manifestText = remoteManifest(
            snapshot.project,
            snapshot.revisionId,
            remoteFiles,
            BackupLayout.VERSION,
        )
        val manifestUri = writeTextNew(
            revisionDir,
            MANIFEST,
            "application/json",
            manifestText,
        )
        val manifestHash = ProjectHashing.sha256(manifestText)
        val commitText = JSONObject()
            .put("schemaVersion", BackupLayout.VERSION)
            .put("layoutVersion", BackupLayout.VERSION)
            .put("projectId", snapshot.project.projectId)
            .put("revisionId", snapshot.revisionId)
            .put("manifestSha256", manifestHash)
            .put("committedAtEpochMs", System.currentTimeMillis())
            .toString(2)
        val commitUri = writeTextNew(
            revisionDir,
            COMMIT,
            "application/json",
            commitText,
        )

        require(ProjectHashing.sha256(readText(manifestUri)) == manifestHash) {
            "Manifest remoto não confirmou integridade."
        }
        val commit = JSONObject(readText(commitUri))
        require(
            commit.getString("projectId") == snapshot.project.projectId &&
                commit.getString("revisionId") == snapshot.revisionId &&
                commit.getString("manifestSha256") == manifestHash
        ) { "Commit remoto não confirmou a revisão esperada." }

        replaceText(
            metaDir,
            BackupLayout.CURRENT_FILE,
            JSONObject()
                .put("projectId", snapshot.project.projectId)
                .put("revisionId", snapshot.revisionId)
                .put("updatedAtEpochMs", snapshot.project.updatedAtEpochMs)
                .toString(2),
        )

        val committed = RemoteRevision(
            projectId = snapshot.project.projectId,
            revisionId = snapshot.revisionId,
            updatedAtEpochMs = snapshot.project.updatedAtEpochMs,
            projectName = snapshot.project.name,
            manifestUri = manifestUri,
            revisionDirUri = revisionDir,
            projectDirUri = projectDir,
            layoutVersion = BackupLayout.VERSION,
            files = remoteFiles,
        )

        cleanupV2(projectDir, revisionsDir, committed)
        deleteOtherV2FoldersForProject(snapshot.project.projectId, keep = projectDir)
        deleteLegacyProject(snapshot.project.projectId)
        cleanupLegacyRootIfEmpty()
        committed
    }

    suspend fun targetedLookup(
        projectId: String,
        revisionId: String,
        attempts: Int = 5,
    ): RemoteRevision? = withContext(Dispatchers.IO) {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            findV2Revision(projectId, revisionId)?.let { return@withContext it }
            findLegacyRevision(projectId, revisionId)?.let { return@withContext it }
            if (attempt + 1 < attempts) delay(SETTLING_DELAY_MS)
        }
        null
    }

    suspend fun scanValid(): List<RemoteRevision> = withContext(Dispatchers.IO) {
        initializeRoot()
        val v2 = scanV2()
        val legacy = scanLegacy()
        (legacy + v2)
            .groupBy { it.projectId }
            .mapNotNull { (_, revisions) ->
                revisions.maxWithOrNull(
                    compareBy<RemoteRevision> { it.updatedAtEpochMs }
                        .thenBy { it.layoutVersion }
                )
            }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    suspend fun restoreRevision(
        remote: RemoteRevision,
        stagingRoot: File,
    ) = withContext(Dispatchers.IO) {
        stagingRoot.deleteRecursively()
        require(stagingRoot.mkdirs())
        val projectDir = if (remote.layoutVersion >= BackupLayout.VERSION) {
            findV2ProjectDirById(remote.projectId)?.uri
        } else {
            findLegacyProjectDir(remote.projectId)
        } ?: error("Pasta remota do projeto não foi encontrada.")

        try {
            remote.files.forEach { rf ->
                currentCoroutineContext().ensureActive()
                val remoteUri = if (remote.layoutVersion >= BackupLayout.VERSION) {
                    findPath(projectDir, rf.remotePath)
                } else {
                    val blobs = findChild(projectDir, LEGACY_BLOBS_DIR)
                    blobs?.let { findChild(it, rf.remotePath) }
                } ?: error("Arquivo remoto ausente: ${rf.relativePath}")

                val target = File(stagingRoot, rf.relativePath)
                target.parentFile?.mkdirs()
                resolver.openInputStream(remoteUri)!!.buffered(COPY_BUFFER).use { input ->
                    FileOutputStream(target).buffered(COPY_BUFFER).use { output ->
                        val buffer = ByteArray(COPY_BUFFER)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                        output.flush()
                    }
                }
                require(
                    target.length() == rf.size &&
                        ProjectHashing.sha256(target) == rf.sha256
                ) { "Integridade do restore falhou: ${rf.relativePath}" }
            }
            val projectFile = File(stagingRoot, "project.json")
            require(projectFile.isFile) { "Backup não contém project.json." }
            val project = ProjectJson.decode(projectFile.readText(Charsets.UTF_8))
            require(project.projectId == remote.projectId) {
                "Identidade restaurada não corresponde ao projeto remoto."
            }
        } catch (error: Exception) {
            stagingRoot.deleteRecursively()
            throw error
        }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        findV2ProjectDirById(projectId)?.uri?.let {
            require(DocumentsContract.deleteDocument(resolver, it)) {
                "O provider não confirmou a exclusão remota."
            }
        }
        deleteLegacyProject(projectId)
        cleanupLegacyRootIfEmpty()
        projectDirCache.remove(projectId)
    }

    private fun scanV2(): List<RemoteRevision> {
        val projects = findChild(treeRoot, BackupLayout.PROJECTS_DIR) ?: return emptyList()
        return listChildren(projects)
            .filter { it.isDirectory }
            .mapNotNull { child ->
                val projectId = projectIdFromV2Dir(child.uri) ?: return@mapNotNull null
                projectDirCache[projectId] = child.uri
                val meta = findChild(child.uri, BackupLayout.PROJECT_META_DIR)
                    ?: return@mapNotNull null
                val revisions = findChild(meta, BackupLayout.REVISIONS_DIR)
                    ?: return@mapNotNull null
                listChildren(revisions)
                    .filter { it.isDirectory && it.name.startsWith(REVISION_PREFIX) }
                    .mapNotNull {
                        validateV2Revision(
                            projectId,
                            it.name.removePrefix(REVISION_PREFIX),
                            child.uri,
                            it.uri,
                        )
                    }
                    .maxByOrNull { it.updatedAtEpochMs }
            }
    }

    private fun findV2Revision(
        projectId: String,
        revisionId: String,
    ): RemoteRevision? {
        val projectDir = findV2ProjectDirById(projectId)?.uri ?: return null
        val meta = findChild(projectDir, BackupLayout.PROJECT_META_DIR) ?: return null
        val revisions = findChild(meta, BackupLayout.REVISIONS_DIR) ?: return null
        val revisionDir = findChild(revisions, REVISION_PREFIX + revisionId) ?: return null
        return validateV2Revision(projectId, revisionId, projectDir, revisionDir)
    }

    private fun validateV2Revision(
        projectId: String,
        revisionId: String,
        projectDir: Uri,
        revisionDir: Uri,
    ): RemoteRevision? = runCatching {
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
        require(manifest.optInt("layoutVersion", 1) >= BackupLayout.VERSION)
        val files = parseV2Files(manifest)
        require(files.any { it.relativePath == "project.json" })
        files.forEach { rf ->
            val uri = requireNotNull(findPath(projectDir, rf.remotePath))
            require(verifyDirect(uri, rf.size, rf.sha256))
        }
        RemoteRevision(
            projectId = projectId,
            revisionId = revisionId,
            updatedAtEpochMs = manifest.getLong("updatedAtEpochMs"),
            projectName = manifest.optString("projectName", "Projeto"),
            manifestUri = manifestUri,
            revisionDirUri = revisionDir,
            projectDirUri = projectDir,
            layoutVersion = BackupLayout.VERSION,
            files = files,
        )
    }.getOrNull()

    private fun scanLegacy(): List<RemoteRevision> {
        val projects = findChild(treeRoot, BackupLayout.LEGACY_PROJECTS_DIR)
            ?: return emptyList()
        return listChildren(projects)
            .filter { it.isDirectory && isCanonicalProjectId(it.name) }
            .mapNotNull { project ->
                val currentId = findChild(project.uri, LEGACY_CURRENT)?.let {
                    runCatching {
                        JSONObject(readText(it)).optString("revisionId")
                    }.getOrNull()
                }.orEmpty()
                if (currentId.isNotBlank()) {
                    validateLegacyRevision(project.name, currentId, project.uri)
                } else {
                    val revisions = findChild(project.uri, LEGACY_REVISIONS_DIR)
                        ?: return@mapNotNull null
                    listChildren(revisions)
                        .filter { it.isDirectory && it.name.startsWith(REVISION_PREFIX) }
                        .mapNotNull {
                            validateLegacyRevision(
                                project.name,
                                it.name.removePrefix(REVISION_PREFIX),
                                project.uri,
                                it.uri,
                            )
                        }
                        .maxByOrNull { it.updatedAtEpochMs }
                }
            }
    }

    private fun findLegacyRevision(
        projectId: String,
        revisionId: String,
    ): RemoteRevision? {
        val projectDir = findLegacyProjectDir(projectId) ?: return null
        return validateLegacyRevision(projectId, revisionId, projectDir)
    }

    private fun validateLegacyRevision(
        projectId: String,
        revisionId: String,
        projectDir: Uri,
        knownRevisionDir: Uri? = null,
    ): RemoteRevision? = runCatching {
        val revisions = requireNotNull(findChild(projectDir, LEGACY_REVISIONS_DIR))
        val revisionDir = knownRevisionDir
            ?: requireNotNull(findChild(revisions, REVISION_PREFIX + revisionId))
        val manifestUri = requireNotNull(findChild(revisionDir, MANIFEST))
        val commitUri = requireNotNull(findChild(revisionDir, COMMIT))
        val manifestText = readText(manifestUri)
        val manifestHash = ProjectHashing.sha256(manifestText)
        val commit = JSONObject(readText(commitUri))
        require(commit.getString("projectId") == projectId)
        require(commit.getString("revisionId") == revisionId)
        require(commit.getString("manifestSha256") == manifestHash)
        val manifest = JSONObject(manifestText)
        val files = parseLegacyFiles(manifest)
        val blobs = requireNotNull(findChild(projectDir, LEGACY_BLOBS_DIR))
        files.forEach { rf ->
            val uri = requireNotNull(findChild(blobs, rf.remotePath))
            require(verifyDirect(uri, rf.size, rf.sha256))
        }
        require(files.any { it.relativePath == "project.json" })
        RemoteRevision(
            projectId = projectId,
            revisionId = revisionId,
            updatedAtEpochMs = manifest.getLong("updatedAtEpochMs"),
            projectName = manifest.optString("projectName", "Projeto"),
            manifestUri = manifestUri,
            revisionDirUri = revisionDir,
            projectDirUri = projectDir,
            layoutVersion = 1,
            files = files,
        )
    }.getOrNull()

    private fun ensureV2ProjectDir(project: ProjectManifest): Uri {
        val projects = ensureDir(treeRoot, BackupLayout.PROJECTS_DIR)
        val desiredBase = BackupLayout.projectFolderName(project)
        val existing = findV2ProjectDirById(project.projectId)
        if (existing != null) {
            if (existing.name != desiredBase) {
                val collision = listChildren(projects).firstOrNull {
                    it.isDirectory && it.name == desiredBase && it.uri != existing.uri
                }
                val desired = if (collision == null) {
                    desiredBase
                } else {
                    "$desiredBase [${project.projectId.take(8)}]"
                }
                val renamed = runCatching {
                    DocumentsContract.renameDocument(resolver, existing.uri, desired)
                }.getOrNull()
                if (renamed != null) {
                    projectDirCache[project.projectId] = renamed
                    dirCache.clear()
                    ensureProjectMarker(renamed, project)
                    return renamed
                }
            }
            ensureProjectMarker(existing.uri, project)
            return existing.uri
        }

        val collision = findChild(projects, desiredBase)
        val desired = if (collision == null) {
            desiredBase
        } else {
            "$desiredBase [${project.projectId.take(8)}]"
        }
        val dir = requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                projects,
                DocumentsContract.Document.MIME_TYPE_DIR,
                desired,
            )
        )
        cacheDir(projects, desired, dir)
        projectDirCache[project.projectId] = dir
        ensureProjectMarker(dir, project)
        return dir
    }

    private fun ensureProjectMarker(projectDir: Uri, project: ProjectManifest) {
        val meta = ensureDir(projectDir, BackupLayout.PROJECT_META_DIR)
        val payload = JSONObject()
            .put("schemaVersion", BackupLayout.VERSION)
            .put("layoutVersion", BackupLayout.VERSION)
            .put("projectId", project.projectId)
            .put("projectName", project.name)
            .put("artist", project.artist)
            .put("song", project.song)
            .toString(2)
        replaceText(meta, BackupLayout.PROJECT_ID_FILE, payload)
    }

    private data class V2ProjectDir(val uri: Uri, val name: String)

    private fun findV2ProjectDirById(projectId: String): V2ProjectDir? {
        val projects = findChild(treeRoot, BackupLayout.PROJECTS_DIR) ?: return null
        projectDirCache[projectId]?.let { cached ->
            listChildren(projects).firstOrNull { it.uri == cached }?.let {
                return V2ProjectDir(it.uri, it.name)
            }
        }
        listChildren(projects).filter { it.isDirectory }.forEach { child ->
            val id = projectIdFromV2Dir(child.uri)
            if (id == projectId) {
                projectDirCache[projectId] = child.uri
                return V2ProjectDir(child.uri, child.name)
            }
        }
        return null
    }

    private fun projectIdFromV2Dir(projectDir: Uri): String? = runCatching {
        val meta = requireNotNull(findChild(projectDir, BackupLayout.PROJECT_META_DIR))
        val marker = requireNotNull(findChild(meta, BackupLayout.PROJECT_ID_FILE))
        JSONObject(readText(marker))
            .getString("projectId")
            .takeIf(::isCanonicalProjectId)
    }.getOrNull()

    private fun deleteOtherV2FoldersForProject(projectId: String, keep: Uri) {
        val projects = findChild(treeRoot, BackupLayout.PROJECTS_DIR) ?: return
        listChildren(projects)
            .filter { it.isDirectory && it.uri != keep }
            .filter { projectIdFromV2Dir(it.uri) == projectId }
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.uri) } }
    }

    private fun remoteManifest(
        project: ProjectManifest,
        revisionId: String,
        files: List<RemoteFile>,
        layoutVersion: Int,
    ): String {
        val array = JSONArray()
        files.sortedBy { it.relativePath }.forEach { f ->
            array.put(
                JSONObject()
                    .put("relativePath", f.relativePath)
                    .put("remotePath", f.remotePath)
                    .put("size", f.size)
                    .put("sha256", f.sha256)
            )
        }
        return JSONObject()
            .put("schemaVersion", 2)
            .put("layoutVersion", layoutVersion)
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

    private fun parseV2Files(manifest: JSONObject): List<RemoteFile> {
        val array = manifest.getJSONArray("files")
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    RemoteFile(
                        relativePath = j.getString("relativePath"),
                        remotePath = j.getString("remotePath"),
                        size = j.getLong("size"),
                        sha256 = j.getString("sha256"),
                    )
                )
            }
        }
    }

    private fun parseLegacyFiles(manifest: JSONObject): List<RemoteFile> {
        val array = manifest.getJSONArray("files")
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    RemoteFile(
                        relativePath = j.getString("relativePath"),
                        remotePath = j.getString("blobName"),
                        size = j.getLong("size"),
                        sha256 = j.getString("sha256"),
                    )
                )
            }
        }
    }

    private fun cleanupV2(
        projectDir: Uri,
        revisionsDir: Uri,
        current: RemoteRevision,
    ) {
        listChildren(revisionsDir)
            .filter { it.isDirectory && it.uri != current.revisionDirUri }
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.uri) } }

        val keep = current.files.map { it.remotePath }.toSet()
        listOf(
            BackupLayout.SOURCE_DIR,
            BackupLayout.STEMS_DIR,
            BackupLayout.EXPORTS_DIR,
        ).forEach { top ->
            findChild(projectDir, top)?.let { cleanArtifactTree(it, top, keep) }
        }

        val meta = findChild(projectDir, BackupLayout.PROJECT_META_DIR)
        val data = meta?.let { findChild(it, "Dados") }
        if (data != null) {
            cleanArtifactTree(data, "${BackupLayout.PROJECT_META_DIR}/Dados", keep)
        }
    }

    private fun cleanArtifactTree(
        directory: Uri,
        prefix: String,
        keep: Set<String>,
    ): Boolean {
        val children = listChildren(directory)
        children.forEach { child ->
            val path = "$prefix/${child.name}"
            if (child.isDirectory) {
                val empty = cleanArtifactTree(child.uri, path, keep)
                if (empty) runCatching { DocumentsContract.deleteDocument(resolver, child.uri) }
            } else if (path !in keep) {
                runCatching { DocumentsContract.deleteDocument(resolver, child.uri) }
            }
        }
        return listChildren(directory).isEmpty()
    }

    private fun findLegacyProjectDir(projectId: String): Uri? {
        val projects = findChild(treeRoot, BackupLayout.LEGACY_PROJECTS_DIR) ?: return null
        return findChild(projects, projectId)
    }

    private fun deleteLegacyProject(projectId: String) {
        findLegacyProjectDir(projectId)?.let {
            runCatching { DocumentsContract.deleteDocument(resolver, it) }
        }
    }

    private fun cleanupLegacyRootIfEmpty() {
        val root = findChild(treeRoot, BackupLayout.LEGACY_PROJECTS_DIR) ?: return
        if (listChildren(root).isEmpty()) {
            runCatching { DocumentsContract.deleteDocument(resolver, root) }
        }
    }

    private fun findPath(parent: Uri, relativePath: String): Uri? {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        var current = parent
        parts.forEach { part ->
            current = findChild(current, part) ?: return null
        }
        return current
    }

    private fun createFileAtPath(
        parent: Uri,
        relativePath: String,
        mime: String,
    ): Uri {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        require(parts.isNotEmpty())
        var directory = parent
        parts.dropLast(1).forEach { segment ->
            directory = ensureDir(directory, segment)
        }
        return requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                directory,
                mime,
                parts.last(),
            )
        )
    }

    private fun ensureDir(parent: Uri, name: String): Uri {
        val key = cacheKey(parent, name)
        dirCache[key]?.let { return it }
        val found = findChild(parent, name)
        if (found != null) {
            dirCache[key] = found
            return found
        }
        val created = requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        )
        dirCache[key] = created
        return created
    }

    private fun cacheDir(parent: Uri, name: String, uri: Uri) {
        dirCache[cacheKey(parent, name)] = uri
    }

    private fun cacheKey(parent: Uri, name: String) = parent.toString() + "|" + name

    private fun writeTextNew(
        parent: Uri,
        name: String,
        mime: String,
        text: String,
    ): Uri {
        val uri = requireNotNull(
            DocumentsContract.createDocument(resolver, parent, mime, name)
        )
        resolver.openOutputStream(uri, "w")!!.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        return uri
    }

    private fun replaceText(parent: Uri, name: String, text: String): Uri {
        findChild(parent, name)?.let {
            runCatching { DocumentsContract.deleteDocument(resolver, it) }
        }
        return writeTextNew(parent, name, "application/json", text)
    }

    private fun readText(uri: Uri): String =
        resolver.openInputStream(uri)!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private data class Child(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
    )

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
                    add(
                        Child(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            name = name,
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun verifyDirect(
        uri: Uri,
        expectedSize: Long,
        expectedSha: String,
    ): Boolean = runCatching {
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
        const val MANIFEST = "manifest.json"
        const val COMMIT = "commit.json"
        const val REVISION_PREFIX = "v_"

        const val LEGACY_BLOBS_DIR = "files"
        const val LEGACY_REVISIONS_DIR = "revisions"
        const val LEGACY_CURRENT = "current.json"

        const val COPY_BUFFER = 1024 * 1024
        const val SETTLING_DELAY_MS = 700L
    }
}
