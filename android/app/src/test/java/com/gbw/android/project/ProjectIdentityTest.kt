package com.gbw.android.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIdentityTest {
    @Test
    fun create_generatesCanonicalUuid() {
        val p = ProjectManifest.new("Song", nowEpochMs = 1000L)
        assertTrue(isCanonicalProjectId(p.projectId))
    }

    @Test
    fun rename_preservesProjectId_butChangesRevision() {
        val p = ProjectManifest.new("Song", nowEpochMs = 1000L)
        val renamed = p.copy(name = "Enemy Practice", updatedAtEpochMs = 2000L)
        assertEquals(p.projectId, renamed.projectId)
        assertNotEquals(ProjectHashing.revisionId(p), ProjectHashing.revisionId(renamed))
    }

    @Test
    fun duplicate_semantics_requireNewUuid() {
        val original = ProjectManifest.new("Song", nowEpochMs = 1000L)
        val duplicate = ProjectManifest.new("Song — cópia", nowEpochMs = 1000L)
        assertNotEquals(original.projectId, duplicate.projectId)
    }

    @Test
    fun revisionId_isDeterministicAndContentSensitive() {
        val p = ProjectManifest.new("Song", nowEpochMs = 1000L)
        assertEquals(ProjectHashing.revisionId(p), ProjectHashing.revisionId(p))
        val changed = p.copy(artist = "Skillet")
        assertNotEquals(ProjectHashing.revisionId(p), ProjectHashing.revisionId(changed))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathTraversal_isRejected() {
        normalizeRelativePath("stems/../../secret.wav")
    }
}
