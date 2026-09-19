package com.gbw.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParityTest {
    @Test fun qualityTiersRemainStable() {
        val ideal = AudioQualityRules.classify("a.wav", "WAV", "pcm_f32le", 48_000, 1, 32, true, true, peakDbfs = -6.0)
        val adequate = AudioQualityRules.classify("a.flac", "FLAC", "flac", 48_000, 1, 24, false, true, peakDbfs = -6.0)
        val caution = AudioQualityRules.classify("a.mp3", "MP3", "mp3", 44_100, 2, null, false, false, peakDbfs = -6.0)
        assertEquals(QualityStatus.IDEAL, ideal.status)
        assertEquals(QualityStatus.ADEQUATE, adequate.status)
        assertEquals(QualityStatus.CAUTION, caution.status)
    }

    @Test fun projectTextIsCaseAndAccentFriendly() {
        assertEquals("Wolves At The Gate", TextNormalization.titleCase("WOLVES AT THE GATE"))
        assertEquals("arvore", TextNormalization.searchKey("ÁRVORE"))
    }

    @Test fun workflowIsOriginalOnlyAndLinear() {
        val doc = ProjectDocument(
            state = ProjectState(
                sourcePrepared = true,
                separationComplete = true,
                exportComplete = false,
            ),
        )
        val states = WorkflowRules.states(doc)
        assertTrue(states[WorkflowStep.SOURCE] == true)
        assertTrue(states[WorkflowStep.SEPARATION] == true)
        assertTrue(states[WorkflowStep.EXPORT] == false)
        assertEquals(WorkflowStep.EXPORT, WorkflowRules.activeStep(doc))
    }
}
