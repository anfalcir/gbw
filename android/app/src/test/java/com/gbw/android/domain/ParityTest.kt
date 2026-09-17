package com.gbw.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParityTest {
    @Test fun tuningDeltasMatchLinux523() {
        assertEquals(3, Tunings.delta("Drop B", "Drop D"))
        assertEquals(-3, Tunings.delta("Drop D", "Drop B"))
        assertNull(Tunings.delta("E Standard", "Drop D"))
    }

    @Test fun androidUsesQuickSeparatorByDefault() {
        assertEquals(SeparationMode.QUICK, SeparationMode.androidDefault)
        assertEquals("B", SeparationMode.androidDefault.code)
    }

    @Test fun qualityTiersMatchLinux523Rules() {
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

    @Test fun workflowIsLinear() {
        val doc = ProjectDocument(
            config = WorkflowConfig(originalTuning = "Drop B", targetTuning = "Drop D"),
            state = ProjectState(sourcePrepared = true, separationComplete = true, tuningConfirmed = true, exportComplete = false),
        )
        val states = WorkflowRules.states(doc)
        assertTrue(states[WorkflowStep.SOURCE] == true)
        assertTrue(states[WorkflowStep.SEPARATION] == true)
        assertTrue(states[WorkflowStep.TUNING] == true)
        assertTrue(states[WorkflowStep.EXPORT] == false)
    }
}
