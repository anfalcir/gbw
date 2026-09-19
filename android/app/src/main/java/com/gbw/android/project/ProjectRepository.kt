package com.gbw.android.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.gbw.android.backup.BackupDirtyStore
import com.gbw.android.backup.BackupScheduler
import com.gbw.android.separation.SeparationResultFiles
import com.gbw.android.separation.SeparationStem
import com.gbw.android.separation.StoredSeparationResult
import com.gbw.android.separation.ValidatedSeparationResult
import com.gbw.android.source.PreparedSourceRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class ProjectSnapshot(
    val project: ProjectManifest,
    val revisionId: String,
    val root: File,
    val files: List<DurableArtifact>,
)

internal class ProjectRepository(context: Context) {
    private val appContext = context.applicationContext
    private val projectsRoot = File(appContext.filesDir, "projects").apply { mkdirs() }
    private val stateRoot = File(appContext.filesDir, "state").apply { mkdirs() }
    private val locksRoot = File(stateRoot, "project-locks").apply { mkdirs() }
    private val activeFile = File(stateRoot, "active_project.txt")
    private val dirtyStore = BackupDirtyStore(appContext)

    fun list(): List<ProjectManifest> =
        projectsRoot.listFiles()
            ?.filter { it.isDirectory && isCanonicalProjectId(it.name) }
            ?.mapNotNull { runCatching { load(it.name) }.getOrNull() }
            ?.sortedWith(
                compareBy<ProjectManifest>(
                    { normalizeProjectText(it.artist).lowercase() },
                    { normalizeProjectText(it.song).lowercase() },
                    { it.name.lowercase() },
                )
            )
            ?: emptyList()

    fun active(): ProjectManifest? {
        val id = activeFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return id.takeIf(::isCanonicalProjectId)?.let { runCatching { load(it) }.getOrNull() }
    }

    fun normalizeStoredMetadata(): Int {
        var changed = 0
        list().forEach { project ->
            val artist = normalizeProjectText(project.artist)
            val song = normalizeProjectText(project.song)
            val automatic = automaticProjectName(artist, song)
            val desiredName = automatic.ifBlank { project.name }
            if (
                artist != project.artist ||
                song != project.song ||
                desiredName != project.name
            ) {
                mutate(project.projectId) {
                    it.copy(
                        artist = artist,
                        song = song,
                        name = sanitizeProjectName(desiredName),
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                }
                changed += 1
            }
        }
        return changed
    }

    fun setActive(projectId: String): ProjectManifest {
        val project = load(projectId)
        atomicWrite(activeFile, projectId.toByteArray(Charsets.UTF_8))
        return project
    }

    fun closeActive(): ProjectManifest? {
        val current = active()
        if (activeFile.exists() && !activeFile.delete()) {
            atomicWrite(activeFile, ByteArray(0))
            activeFile.delete()
        }
        return current
    }

    fun create(
        name: String,
        artist: String = "",
        song: String = "",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ProjectManifest {
        val project = ProjectManifest.new(name, artist, song, nowEpochMs)
        withProjectLock(project.projectId) {
            val root = projectRoot(project.projectId)
            require(!root.exists()) { "projectId já existe" }
            require(root.mkdirs()) { "Não foi possível criar a pasta do projeto." }
            listOf("source", "stems", "exports").forEach { File(root, it).mkdirs() }
            saveUnlocked(project, markDirty = true)
        }
        setActive(project.projectId)
        return load(project.projectId)
    }

    fun load(projectId: String): ProjectManifest {
        require(isCanonicalProjectId(projectId)) { "projectId inválido" }
        val file = File(projectRoot(projectId), "project.json")
        require(file.isFile) { "Projeto não encontrado." }
        val project = ProjectJson.decode(file.readText(Charsets.UTF_8))
        require(project.projectId == projectId) { "Identidade interna do projeto não corresponde à pasta." }
        return project
    }

    fun rename(projectId: String, newName: String): ProjectManifest =
        mutate(projectId) { it.copy(name = sanitizeProjectName(newName), updatedAtEpochMs = System.currentTimeMillis()) }

    fun updateMetadata(projectId: String, artist: String, song: String): ProjectManifest =
        mutate(projectId) {
            val normalizedArtist = normalizeProjectText(artist)
            val normalizedSong = normalizeProjectText(song)
            val automaticName = automaticProjectName(normalizedArtist, normalizedSong)
            it.copy(
                artist = normalizedArtist,
                song = normalizedSong,
                name = if (automaticName.isNotBlank()) sanitizeProjectName(automaticName) else it.name,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }

    fun updatePitch(projectId: String, semitones: Int, vocalFormants: Boolean): ProjectManifest {
        require(semitones in -12..12)
        return mutate(projectId) {
            it.copy(
                pitch = PitchState(semitones, vocalFormants),
                workflowStage = if (it.separation != null) "TUNING" else it.workflowStage,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    fun duplicate(projectId: String, name: String? = null): ProjectManifest {
        val original = load(projectId)
        val siblingSongs = list()
            .filter { foldProjectSearchText(it.artist) == foldProjectSearchText(original.artist) }
            .map { it.song }
        val copySong = nextProjectCopySong(original.song, siblingSongs)
        val duplicate = ProjectManifest.new(
            name = name ?: (original.name + " — cópia"),
            artist = original.artist,
            song = copySong,
        ).let { created ->
            if (name.isNullOrBlank()) created else created.copy(name = sanitizeProjectName(name))
        }
        withProjectLock(duplicate.projectId) {
            val dst = projectRoot(duplicate.projectId)
            require(dst.mkdirs())
            copyTree(projectRoot(projectId), dst, skipProjectJson = true)
            val duplicatedState = duplicate.copy(
                workflowStage = original.workflowStage,
                source = original.source,
                separation = original.separation,
                pitch = original.pitch,
                export = original.export,
            )
            val refreshed = duplicatedState.copy(
                inventory = buildInventory(duplicatedState, dst),
            )
            saveUnlocked(refreshed, markDirty = true)
            cleanupUnreferenced(dst, refreshed.inventory)
        }
        return load(duplicate.projectId)
    }

    fun deleteLocal(projectId: String) {
        withProjectLock(projectId) {
            val root = projectRoot(projectId)
            if (root.exists()) {
                val trash = File(appContext.cacheDir, "project-trash/${projectId}-${System.currentTimeMillis()}")
                trash.parentFile?.mkdirs()
                if (!root.renameTo(trash)) {
                    copyTree(root, trash, skipProjectJson = false)
                    root.deleteRecursively()
                }
                trash.deleteRecursively()
            }
            if (activeFile.isFile && activeFile.readText().trim() == projectId) activeFile.delete()
            dirtyStore.clear(projectId)
        }
    }

    fun adoptLocalSource(
        uri: Uri,
        displayNameHint: String?,
        name: String,
        artist: String = "",
        song: String = "",
    ): ProjectManifest {
        val project = active() ?: create(name, artist, song)
        return withProjectLock(project.projectId) {
            val current = load(project.projectId)
            val sourceDir = File(projectRoot(project.projectId), "source").apply { mkdirs() }
            val display = displayNameHint ?: queryDisplayName(uri) ?: "original"
            val ext = display.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            val relative = "source/original" + (ext?.let { ".$it" } ?: "")
            val target = resolveProjectPath(project.projectId, relative)
            copyUriAtomic(uri, target)
            val updated = current.copy(
                name = if (current.name == "Projeto sem nome") sanitizeProjectName(name) else current.name,
                artist = artist.ifBlank { current.artist },
                song = song.ifBlank { current.song },
                workflowStage = "SOURCE",
                source = ManagedSource(
                    originalRelativePath = relative,
                    provenanceUri = uri.toString(),
                    title = display,
                ),
                separation = null,
                export = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            val durable = updated.copy(
                inventory = buildInventory(updated, projectRoot(project.projectId)),
            )
            saveUnlocked(durable, markDirty = true)
            cleanupUnreferenced(projectRoot(project.projectId), durable.inventory)
            setActive(project.projectId)
            load(project.projectId)
        }
    }

    fun adoptPreparedSource(
        record: PreparedSourceRecord,
        name: String,
        artist: String = "",
        song: String = "",
    ): ProjectManifest {
        val project = active() ?: create(name, artist, song)
        return adoptPreparedSource(
            projectId = project.projectId,
            record = record,
            name = name,
            artist = artist,
            song = song,
            activate = true,
        )
    }

    fun adoptPreparedSource(
        projectId: String,
        record: PreparedSourceRecord,
        name: String = "",
        artist: String = "",
        song: String = "",
        activate: Boolean = false,
    ): ProjectManifest = withProjectLock(projectId) {
        val current = load(projectId)
        val native = File(record.nativePath)
        val prepared = File(record.preparedPath)
        require(native.isFile && prepared.isFile) { "Fonte preparada legada não está íntegra." }
        val nativeExt = native.extension.lowercase().ifBlank { "bin" }
        val nativeRel = "source/original.$nativeExt"
        val preparedRel = "source/prepared_44100_f32.wav"
        copyFileAtomic(native, resolveProjectPath(projectId, nativeRel))
        copyFileAtomic(prepared, resolveProjectPath(projectId, preparedRel))
        val updated = current.copy(
            name = if (current.name == "Projeto sem nome" && name.isNotBlank()) sanitizeProjectName(name) else current.name,
            artist = artist.ifBlank { current.artist },
            song = song.ifBlank { current.song },
            workflowStage = "SOURCE",
            source = ManagedSource(
                originalRelativePath = nativeRel,
                preparedRelativePath = preparedRel,
                sourceUrl = record.sourceUrl,
                title = record.title,
                formatId = record.formatId,
                durationSeconds = record.durationSeconds,
            ),
            separation = null,
            export = null,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val durable = updated.copy(
            inventory = buildInventory(updated, projectRoot(projectId)),
        )
        saveUnlocked(durable, markDirty = true)
        cleanupUnreferenced(projectRoot(projectId), durable.inventory)
        if (activate) setActive(projectId)
        load(projectId)
    }

    fun publishSeparation(projectId: String, result: ValidatedSeparationResult): ProjectManifest =
        withProjectLock(projectId) {
            val current = load(projectId)
            val stemsDir = File(projectRoot(projectId), "stems").apply { mkdirs() }
            val staging = File(appContext.cacheDir, "project-publish/$projectId/stems-${result.record.jobId}")
            staging.deleteRecursively()
            require(staging.mkdirs())
            try {
                result.stems.forEach { stem -> copyFileAtomic(stem.file, File(staging, "${stem.name}.wav")) }
                val published = mutableMapOf<String, String>()
                result.stems.forEach { stem ->
                    val relative = "stems/${stem.name}.wav"
                    val src = File(staging, "${stem.name}.wav")
                    val dst = File(stemsDir, "${stem.name}.wav")
                    copyFileAtomic(src, dst)
                    published[stem.name] = relative
                }
                val updated = current.copy(
                    workflowStage = "SEPARATION",
                    separation = SeparationState(
                        jobId = result.record.jobId,
                        completedAtEpochMs = result.record.completedAt,
                        frames = result.record.frames,
                        elapsedMillis = result.record.elapsedMillis,
                        runtimeIdentity = result.record.runtimeIdentity,
                        blasThreads = result.record.blasThreads,
                        stems = published,
                    ),
                    export = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                val durable = updated.copy(
                    inventory = buildInventory(updated, projectRoot(projectId)),
                )
                saveUnlocked(durable, markDirty = true)
                cleanupUnreferenced(projectRoot(projectId), durable.inventory)
                load(projectId)
            } finally {
                staging.deleteRecursively()
            }
        }

    fun publishExport(
        projectId: String,
        stagedExportDir: File,
        exportState: ExportState,
    ): ProjectManifest = withProjectLock(projectId) {
        val current = load(projectId)
        require(stagedExportDir.isDirectory)
        exportState.artifacts.forEach { artifact ->
            require(File(stagedExportDir, artifact.relativePath.substringAfter("exports/${exportState.exportId}/")).isFile) {
                "Artefato de exportação ausente no staging: ${artifact.relativePath}"
            }
        }
        val exportsRoot = File(projectRoot(projectId), "exports").apply { mkdirs() }
        val finalDir = File(exportsRoot, exportState.exportId)
        require(!finalDir.exists()) { "exportId já publicado" }
        if (!stagedExportDir.renameTo(finalDir)) {
            copyTree(stagedExportDir, finalDir, skipProjectJson = false)
            stagedExportDir.deleteRecursively()
        }
        val updated = current.copy(
            workflowStage = "EXPORT",
            export = exportState,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val durable = updated.copy(
            inventory = buildInventory(updated, projectRoot(projectId)),
        )
        saveUnlocked(durable, markDirty = true)
        cleanupUnreferenced(projectRoot(projectId), durable.inventory)
        load(projectId)
    }

    fun setLastSynced(projectId: String, revisionId: String): ProjectManifest =
        withProjectLock(projectId) {
            val current = load(projectId)
            val updated = current.copy(lastSyncedRevisionId = revisionId)
            saveUnlocked(updated, markDirty = false)
            load(projectId)
        }

    fun projectSourceUri(projectId: String): Uri? {
        val project = load(projectId)
        val source = project.source ?: return null
        val relative = source.preparedRelativePath ?: source.originalRelativePath
        val file = resolveProjectPath(projectId, relative)
        if (!file.isFile) return null
        return FileProvider.getUriForFile(appContext, appContext.packageName + ".files", file)
    }

    fun projectSeparationResult(projectId: String): ValidatedSeparationResult? {
        val state = load(projectId).separation ?: return null
        if (state.stems.keys != SeparationResultFiles.stemNames.toSet()) return null
        val stems = SeparationResultFiles.stemNames.map { name ->
            val relative = state.stems[name] ?: return null
            val file = resolveProjectPath(projectId, relative)
            if (!file.isFile || file.length() <= 44L) return null
            SeparationStem(name, file)
        }
        val record = StoredSeparationResult(
            jobId = state.jobId,
            type = SeparationResultFiles.DEMUCS_TYPE,
            completedAt = state.completedAtEpochMs,
            frames = state.frames,
            elapsedMillis = state.elapsedMillis,
            peakPssKb = 0L,
            runtimeIdentity = state.runtimeIdentity,
            blasThreads = state.blasThreads,
        )
        return ValidatedSeparationResult(
            record = record,
            stems = stems,
            totalBytes = stems.sumOf { it.file.length() },
        )
    }

    fun resolveProjectPath(projectId: String, relativePath: String): File {
        val rel = normalizeRelativePath(relativePath)
        val root = projectRoot(projectId).canonicalFile
        val target = File(root, rel).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "Path traversal bloqueado." }
        return target
    }

    fun createSnapshot(projectId: String): ProjectSnapshot = withProjectLock(projectId) {
        val project = load(projectId)
        val revisionId = ProjectHashing.revisionId(project)
        val root = File(appContext.cacheDir, "backup-snapshots/$projectId/${revisionId}-${UUID.randomUUID()}")
        require(root.mkdirs())
        val manifestFile = File(projectRoot(projectId), "project.json")
        val projectJsonArtifact = snapshotOne(manifestFile, File(root, "project.json"), "project.json")
        val artifacts = mutableListOf(projectJsonArtifact)
        project.inventory.forEach { item ->
            val src = resolveProjectPath(projectId, item.relativePath)
            require(src.isFile && src.length() == item.size) { "Artefato durável mudou durante snapshot: ${item.relativePath}" }
            val actualHash = if (src.lastModified() == item.modifiedAtEpochMs) item.sha256 else ProjectHashing.sha256(src)
            require(actualHash == item.sha256) { "Integridade local divergente: ${item.relativePath}" }
            artifacts += snapshotOne(src, File(root, item.relativePath), item.relativePath)
        }
        ProjectSnapshot(project, revisionId, root, artifacts)
    }

    fun installRestoredProject(stagingRoot: File, expectedProjectId: String, revisionId: String): ProjectManifest =
        withProjectLock(expectedProjectId) {
            require(!projectRoot(expectedProjectId).exists()) { "Projeto já existe localmente." }
            val manifest = ProjectJson.decode(File(stagingRoot, "project.json").readText(Charsets.UTF_8))
            require(manifest.projectId == expectedProjectId) { "projectId do backup não corresponde ao destino." }
            val finalRoot = projectRoot(expectedProjectId)
            if (!stagingRoot.renameTo(finalRoot)) {
                copyTree(stagingRoot, finalRoot, skipProjectJson = false)
                stagingRoot.deleteRecursively()
            }
            val loaded = load(expectedProjectId)
            val verified = loaded.copy(
                inventory = buildInventory(loaded, finalRoot),
                lastSyncedRevisionId = revisionId,
            )
            saveUnlocked(verified, markDirty = false)
            load(expectedProjectId)
        }

    fun replaceRestoredProject(stagingRoot: File, expectedProjectId: String, revisionId: String): ProjectManifest =
        withProjectLock(expectedProjectId) {
            val wasActive = active()?.projectId == expectedProjectId
            val manifest = ProjectJson.decode(File(stagingRoot, "project.json").readText(Charsets.UTF_8))
            require(manifest.projectId == expectedProjectId) { "projectId do backup não corresponde ao destino." }
            val currentRoot = projectRoot(expectedProjectId)
            val rollback = File(appContext.cacheDir, "project-rollback/$expectedProjectId-" + UUID.randomUUID())
            rollback.parentFile?.mkdirs()
            var movedCurrent = false
            try {
                if (currentRoot.exists()) {
                    if (!currentRoot.renameTo(rollback)) {
                        copyTree(currentRoot, rollback, skipProjectJson = false)
                        currentRoot.deleteRecursively()
                    }
                    movedCurrent = true
                }
                if (!stagingRoot.renameTo(currentRoot)) {
                    copyTree(stagingRoot, currentRoot, skipProjectJson = false)
                    stagingRoot.deleteRecursively()
                }
                val loaded = load(expectedProjectId)
                val verified = loaded.copy(
                    inventory = buildInventory(loaded, currentRoot),
                    lastSyncedRevisionId = revisionId,
                )
                saveUnlocked(verified, markDirty = false)
                rollback.deleteRecursively()
                if (wasActive) setActive(expectedProjectId)
                load(expectedProjectId)
            } catch (error: Exception) {
                currentRoot.deleteRecursively()
                if (movedCurrent && rollback.exists()) {
                    if (!rollback.renameTo(currentRoot)) copyTree(rollback, currentRoot, skipProjectJson = false)
                }
                throw error
            }
        }

    private fun mutate(projectId: String, transform: (ProjectManifest) -> ProjectManifest): ProjectManifest =
        withProjectLock(projectId) {
            val current = load(projectId)
            val next = transform(current)
            require(next.projectId == current.projectId) { "projectId é imutável" }
            val durable = next.copy(
                inventory = buildInventory(next, projectRoot(projectId)),
            )
            saveUnlocked(durable, markDirty = true)
            load(projectId)
        }

    private fun saveUnlocked(project: ProjectManifest, markDirty: Boolean) {
        require(isCanonicalProjectId(project.projectId))
        val root = projectRoot(project.projectId).apply { mkdirs() }
        val target = File(root, "project.json")
        atomicWrite(target, ProjectJson.encode(project).toByteArray(Charsets.UTF_8))
        if (markDirty) {
            dirtyStore.markDirty(project)
            runCatching { BackupScheduler.enqueueDirty(appContext) }
        }
    }

    private fun buildInventory(project: ProjectManifest, root: File): List<DurableArtifact> {
        val referenced = linkedSetOf<String>()
        project.source?.let { source ->
            referenced += normalizeRelativePath(source.originalRelativePath)
            source.preparedRelativePath?.let { referenced += normalizeRelativePath(it) }
        }
        project.separation?.stems?.values?.forEach {
            referenced += normalizeRelativePath(it)
        }
        project.export?.let { export ->
            export.artifacts.forEach { referenced += normalizeRelativePath(it.relativePath) }
            referenced += normalizeRelativePath(export.manifestRelativePath)
        }
        return referenced.map { relative ->
            val file = File(root, relative)
            require(file.isFile) { "Artefato durável ausente: $relative" }
            DurableArtifact(
                relativePath = relative,
                size = file.length(),
                sha256 = ProjectHashing.sha256(file),
                modifiedAtEpochMs = file.lastModified(),
            )
        }.sortedBy { it.relativePath }
    }

    private fun cleanupUnreferenced(root: File, inventory: List<DurableArtifact>) {
        val keep = inventory.map { normalizeRelativePath(it.relativePath) }.toSet()
        listOf("source", "stems", "exports").forEach { leaf ->
            val base = File(root, leaf)
            if (!base.isDirectory) return@forEach
            base.walkBottomUp().forEach { item ->
                if (item == base) return@forEach
                if (item.isFile) {
                    val relative = item.relativeTo(root).invariantSeparatorsPath
                    if (
                        relative !in keep &&
                        !item.name.endsWith(".tmp") &&
                        ".partial" !in item.name
                    ) {
                        runCatching { item.delete() }
                    }
                } else if (item.isDirectory && item.listFiles().isNullOrEmpty()) {
                    runCatching { item.delete() }
                }
            }
        }
    }

    private fun snapshotOne(src: File, dst: File, relativePath: String): DurableArtifact {
        dst.parentFile?.mkdirs()
        runCatching { Files.createLink(dst.toPath(), src.toPath()) }
            .getOrElse { Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return DurableArtifact(
            relativePath = normalizeRelativePath(relativePath),
            size = dst.length(),
            sha256 = ProjectHashing.sha256(dst),
            modifiedAtEpochMs = dst.lastModified(),
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun copyUriAtomic(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp." + UUID.randomUUID())
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).buffered().use { output -> input.copyTo(output, COPY_BUFFER) }
            } ?: error("Não foi possível abrir a fonte selecionada.")
            syncFile(temp)
            atomicReplace(temp, target)
        } finally { temp.delete() }
    }

    private fun copyFileAtomic(source: File, target: File) {
        require(source.isFile)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp." + UUID.randomUUID())
        try {
            FileInputStream(source).buffered().use { input ->
                FileOutputStream(temp).buffered().use { output -> input.copyTo(output, COPY_BUFFER) }
            }
            syncFile(temp)
            atomicReplace(temp, target)
        } finally { temp.delete() }
    }

    private fun copyTree(source: File, target: File, skipProjectJson: Boolean) {
        source.walkTopDown().forEach { item ->
            val rel = item.relativeTo(source)
            if (skipProjectJson && rel.invariantSeparatorsPath == "project.json") return@forEach
            val dst = File(target, rel.path)
            if (item.isDirectory) dst.mkdirs() else {
                dst.parentFile?.mkdirs()
                Files.copy(item.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
        atomicReplace(temp, target)
    }

    private fun syncFile(file: File) { RandomAccessFile(file, "rw").use { it.fd.sync() } }

    private fun atomicReplace(temp: File, target: File) {
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun projectRoot(projectId: String) = File(projectsRoot, projectId)

    private fun <T> withProjectLock(projectId: String, block: () -> T): T {
        require(isCanonicalProjectId(projectId))
        val lockFile = File(locksRoot, "$projectId.lock")
        return synchronized(PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val lock = channel.lock()
                try { block() } finally { lock.release() }
            }
        }
    }

    private companion object {
        const val COPY_BUFFER = 1024 * 1024
        val PROCESS_LOCK = Any()
    }
}
