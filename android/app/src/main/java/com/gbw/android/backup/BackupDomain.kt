package com.gbw.android.backup

data class DirtyProjectState(
    val projectId: String,
    val desiredRevisionId: String,
    val dirtySinceEpochMs: Long,
    val lastChangedAtEpochMs: Long,
    val lastBackedUpRevisionId: String? = null,
)

data class RemoteProjectState(
    val projectId: String,
    val revisionId: String,
    val updatedAtEpochMs: Long,
)

enum class ReconcileAction {
    REMOTE_ONLY_IMPORT,
    LOCAL_ONLY_UPLOAD,
    NO_OP,
    UPLOAD_LOCAL,
    DOWNLOAD_REMOTE,
    CONFLICT,
}

object BackupReconciler {
    fun decide(
        localRevisionId: String?,
        remoteRevisionId: String?,
        lastSyncedRevisionId: String?,
    ): ReconcileAction {
        if (localRevisionId == null && remoteRevisionId != null) return ReconcileAction.REMOTE_ONLY_IMPORT
        if (localRevisionId != null && remoteRevisionId == null) return ReconcileAction.LOCAL_ONLY_UPLOAD
        if (localRevisionId == null && remoteRevisionId == null) return ReconcileAction.NO_OP
        if (localRevisionId == remoteRevisionId) return ReconcileAction.NO_OP
        val localChanged = localRevisionId != lastSyncedRevisionId
        val remoteChanged = remoteRevisionId != lastSyncedRevisionId
        return when {
            localChanged && !remoteChanged -> ReconcileAction.UPLOAD_LOCAL
            !localChanged && remoteChanged -> ReconcileAction.DOWNLOAD_REMOTE
            localChanged && remoteChanged -> ReconcileAction.CONFLICT
            else -> ReconcileAction.NO_OP
        }
    }
}
