package com.gbw.android.backup

import com.gbw.android.project.ProjectManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLayoutTest {
    @Test fun projectFolderIsHumanReadable() {
        val project = ProjectManifest.new("ignored", "WOLVES AT THE GATE", "deadbolt", 1L)
        assertEquals("Wolves At The Gate - Deadbolt", BackupLayout.projectFolderName(project))
    }

    @Test fun artifactsAreSeparatedByPurpose() {
        val sha = "abcdef1234567890abcdef1234567890"
        assertTrue(BackupLayout.artifactRemotePath("source/original.m4a", sha).startsWith("Fonte/"))
        assertTrue(BackupLayout.artifactRemotePath("stems/guitar.wav", sha).startsWith("Separacao - Stems/"))
        assertTrue(BackupLayout.artifactRemotePath("exports/e1/original/backing.flac", sha).startsWith("Exports/Original/"))
        assertTrue(BackupLayout.artifactRemotePath("project.json", sha).startsWith("Projeto/Dados/"))
    }

    @Test fun exportPathDoesNotExposeOpaqueExportId() {
        val path = BackupLayout.artifactRemotePath(
            "exports/e_123_abcdef/pitch_-3st/backing.flac",
            "abcdef1234567890",
        )
        assertTrue(path.startsWith("Exports/Ajustado -3st/"))
        assertTrue("e_123_abcdef" !in path)
    }

    @Test fun artifactNameCarriesStrongIdentityWithoutBeingOpaque() {
        val path = BackupLayout.artifactRemotePath("stems/drums.wav", "0123456789abcdef")
        assertEquals("Separacao - Stems/drums__0123456789ab.wav", path)
    }
}
