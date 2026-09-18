package com.gbw.android.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupReconcilerTest {
    @Test fun remoteOnly_imports() =
        assertEquals(ReconcileAction.REMOTE_ONLY_IMPORT, BackupReconciler.decide(null, "r2", null))

    @Test fun localOnly_uploads() =
        assertEquals(ReconcileAction.LOCAL_ONLY_UPLOAD, BackupReconciler.decide("r1", null, null))

    @Test fun sameRevision_noOp() =
        assertEquals(ReconcileAction.NO_OP, BackupReconciler.decide("r1", "r1", "r1"))

    @Test fun localChanged_uploadsWhenRemoteIsLastSynced() =
        assertEquals(ReconcileAction.UPLOAD_LOCAL, BackupReconciler.decide("r2", "r1", "r1"))

    @Test fun remoteChanged_downloadsWhenLocalIsLastSynced() =
        assertEquals(ReconcileAction.DOWNLOAD_REMOTE, BackupReconciler.decide("r1", "r2", "r1"))

    @Test fun bothChanged_conflicts() =
        assertEquals(ReconcileAction.CONFLICT, BackupReconciler.decide("r2", "r3", "r1"))
}
