package com.gbw.android.backup

import android.content.Context
import android.net.Uri
import com.gbw.android.project.ProjectHashing
import com.gbw.android.project.ProjectRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BackupSummary(
    val uploadedProjects: Int,
    val importedProjects: Int = 0,
    val downloadedProjects: Int = 0,
    val conflicts: List<BackupConflict> = emptyList(),
)

internal data class BackupConflict(
    val projectId: String,
    val name: String,
    val localRevisionId: String,
    val remoteRevisionId: String,
)

internal class ProjectBackupCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val repo = ProjectRepository(appContext)
    private val dirty = BackupDirtyStore(appContext)
    private val settingsStore = BackupSettingsStore(appContext)
    private val deletionStore = ProjectDeletionStore(appContext)

    suspend fun backup(projectIds: Set<String>? = null): BackupSummary = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            val settings = settingsStore.load()
        val treeUri = settings.treeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: error("Escolha uma pasta de backup primeiro.")
        val remote = SafBackupRemoteStore(appContext, treeUri)
        remote.initializeRoot()
        val selected = repo.list().filter { projectIds == null || it.projectId in projectIds }
        var uploaded = 0
        selected.forEach { project ->
            val snapshot = repo.createSnapshot(project.projectId)
            try {
                val revision = remote.uploadSnapshot(snapshot)
                repo.setLastSynced(project.projectId, revision.revisionId)
                dirty.markBackedUp(project.projectId, revision.revisionId)
                deletionStore.clear(project.projectId)
                uploaded += 1
            } finally {
                snapshot.root.deleteRecursively()
            }
        }
            BackupSummary(uploadedProjects = uploaded)
        }
    }

    suspend fun reconcileExisting(): BackupSummary = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            val settings = settingsStore.load()
        val treeUri = settings.treeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: error("Escolha uma pasta de backup primeiro.")
        val remoteStore = SafBackupRemoteStore(appContext, treeUri)
        remoteStore.initializeRoot()
        val remotes = remoteStore.scanValid().associateBy { it.projectId }
        val locals = repo.list().associateBy { it.projectId }
        var uploaded = 0
        var imported = 0
        var downloaded = 0
        val conflicts = mutableListOf<BackupConflict>()
        val ids = (remotes.keys + locals.keys).toSortedSet()

        ids.forEach { id ->
            val local = locals[id]
            val remote = remotes[id]
            if (local == null && remote != null) {
                if (!deletionStore.isIgnored(id)) {
                    restoreNew(remoteStore, remote)
                    imported += 1
                }
                return@forEach
            }
            if (local != null && remote == null) {
                val snapshot = repo.createSnapshot(id)
                try {
                    val committed = remoteStore.uploadSnapshot(snapshot)
                    repo.setLastSynced(id, committed.revisionId)
                    dirty.markBackedUp(id, committed.revisionId)
                    uploaded += 1
                } finally { snapshot.root.deleteRecursively() }
                return@forEach
            }
            if (local == null || remote == null) return@forEach

            val localRevision = ProjectHashing.revisionId(local)
            when (BackupReconciler.decide(localRevision, remote.revisionId, local.lastSyncedRevisionId)) {
                ReconcileAction.NO_OP -> {
                    if (local.lastSyncedRevisionId != remote.revisionId) repo.setLastSynced(id, remote.revisionId)
                    dirty.markBackedUp(id, remote.revisionId)
                }
                ReconcileAction.UPLOAD_LOCAL, ReconcileAction.LOCAL_ONLY_UPLOAD -> {
                    val snapshot = repo.createSnapshot(id)
                    try {
                        val committed = remoteStore.uploadSnapshot(snapshot)
                        repo.setLastSynced(id, committed.revisionId)
                        dirty.markBackedUp(id, committed.revisionId)
                        uploaded += 1
                    } finally { snapshot.root.deleteRecursively() }
                }
                ReconcileAction.DOWNLOAD_REMOTE, ReconcileAction.REMOTE_ONLY_IMPORT -> {
                    restoreReplace(remoteStore, remote)
                    downloaded += 1
                }
                ReconcileAction.CONFLICT -> conflicts += BackupConflict(
                    id, local.name, localRevision, remote.revisionId
                )
            }
        }
            val summary = BackupSummary(uploaded, imported, downloaded, conflicts)
            BackupConflictStore(appContext).save(conflicts)
            summary
        }
    }

    suspend fun resolveKeepLocal(projectId: String) = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            val tree = settingsStore.load().treeUri?.let(Uri::parse) ?: error("Destino ausente")
            val remote = SafBackupRemoteStore(appContext, tree)
            val snapshot = repo.createSnapshot(projectId)
            try {
                val committed = remote.uploadSnapshot(snapshot)
                repo.setLastSynced(projectId, committed.revisionId)
                dirty.markBackedUp(projectId, committed.revisionId)
                BackupConflictStore(appContext).remove(projectId)
            } finally {
                snapshot.root.deleteRecursively()
            }
        }
    }

    suspend fun resolveUseRemote(projectId: String) = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            val tree = settingsStore.load().treeUri?.let(Uri::parse) ?: error("Destino ausente")
            val remote = SafBackupRemoteStore(appContext, tree)
            val revision = remote.scanValid().firstOrNull { it.projectId == projectId }
                ?: error("Projeto não encontrado no backup.")
            restoreReplace(remote, revision)
            BackupConflictStore(appContext).remove(projectId)
        }
    }

    suspend fun deleteLocalOnly(projectId: String) = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            deletionStore.ignore(projectId)
            repo.deleteLocal(projectId)
            BackupConflictStore(appContext).remove(projectId)
        }
    }

    suspend fun deleteLocalAndRemote(projectId: String) = withContext(Dispatchers.IO) {
        BackupOperationLock.withLock {
            val tree = settingsStore.load().treeUri?.let(Uri::parse)
            if (tree != null) {
                SafBackupRemoteStore(appContext, tree).deleteProject(projectId)
            }
            deletionStore.clear(projectId)
            repo.deleteLocal(projectId)
            BackupConflictStore(appContext).remove(projectId)
        }
    }

    private suspend fun restoreNew(store: SafBackupRemoteStore, remote: RemoteRevision) {
        val staging = File(appContext.cacheDir, "restore-staging/${remote.projectId}-${UUID.randomUUID()}")
        try {
            store.restoreRevision(remote, staging)
            repo.installRestoredProject(staging, remote.projectId, remote.revisionId)
            dirty.markBackedUp(remote.projectId, remote.revisionId)
        } finally { staging.deleteRecursively() }
    }

    private suspend fun restoreReplace(store: SafBackupRemoteStore, remote: RemoteRevision) {
        val staging = File(appContext.cacheDir, "restore-staging/${remote.projectId}-${UUID.randomUUID()}")
        try {
            store.restoreRevision(remote, staging)
            repo.replaceRestoredProject(staging, remote.projectId, remote.revisionId)
            dirty.markBackedUp(remote.projectId, remote.revisionId)
        } finally { staging.deleteRecursively() }
    }
}
