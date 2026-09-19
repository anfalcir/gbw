package com.gbw.android.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNamingTest {
    @Test fun linuxStyleTextNormalization() {
        assertEquals("Wolves At The Gate", normalizeProjectText("WOLVES at THE gate"))
        assertEquals("Rodox", normalizeProjectText("rodox"))
        assertEquals("Árvore", normalizeProjectText("  ÁRVORE  "))
    }

    @Test fun automaticNameUsesArtistAndSong() {
        assertEquals(
            "Wolves At The Gate - Deadbolt",
            automaticProjectName("wolves at the gate", "DEADBOLT"),
        )
    }

    @Test fun newProjectUsesAutomaticIdentityName() {
        val p = ProjectManifest.new("ignored", "WOLVES AT THE GATE", "deadbolt", 1000L)
        assertEquals("Wolves At The Gate", p.artist)
        assertEquals("Deadbolt", p.song)
        assertEquals("Wolves At The Gate - Deadbolt", p.name)
        assertTrue(isCanonicalProjectId(p.projectId))
    }

    @Test fun duplicateIdentityStillRequiresNewUuid() {
        val one = ProjectManifest.new("x", "Band", "Song", 1000L)
        val two = ProjectManifest.new("x", "Band", "Song", 1000L)
        assertNotEquals(one.projectId, two.projectId)
    }

    @Test fun projectSearchIsCaseAccentAndTermInsensitive() {
        val project = ProjectManifest.new("x", "Árvore", "Coração Valente", 1000L)
        assertTrue(projectMatchesSearch(project, "arvore"))
        assertTrue(projectMatchesSearch(project, "CORACAO arvore"))
        assertTrue(projectMatchesSearch(project, "valente"))
        assertEquals(false, projectMatchesSearch(project, "skillet"))
    }

    @Test fun copySongLabelsStayDistinct() {
        assertEquals("Enemy (Cópia)", nextProjectCopySong("Enemy", listOf("Enemy")))
        assertEquals(
            "Enemy (Cópia 3)",
            nextProjectCopySong("Enemy", listOf("Enemy", "Enemy (Cópia)", "Enemy (Cópia 2)")),
        )
    }

    @Test fun workflowStageLabelsAreUserFacing() {
        assertEquals("Fonte", projectWorkflowStageLabel("SOURCE"))
        assertEquals("Separação", projectWorkflowStageLabel("SEPARATION"))
        assertEquals("Exportação", projectWorkflowStageLabel("EXPORT"))
    }
}
