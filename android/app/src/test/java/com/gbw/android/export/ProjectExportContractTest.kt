package com.gbw.android.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProjectExportContractTest {
    @Test
    fun backingContainsExactlyFiveNonGuitarStems() {
        assertEquals(listOf("drums", "bass", "other", "vocals", "piano"), ProjectExportContract.BACKING_ROLES)
        assertFalse("guitar" in ProjectExportContract.BACKING_ROLES)
        assertTrue(ProjectExportContract.validateStemRoles(setOf("drums","bass","other","vocals","piano","guitar")))
    }

    @Test
    fun sharedGain_isOneFactorForCombinedPeak() {
        val factor = SharedGain.factorForCombinedPeak(1.0, -1.0)
        val target = SharedGain.targetLinear(-1.0)
        assertTrue(abs(factor - target) < 1e-9)
        val backing = 0.50 * factor
        val guitar = 0.50 * factor
        assertTrue(abs((backing + guitar) - target) < 1e-9)
        assertTrue(abs(backing / guitar - 1.0) < 1e-9)
    }

    @Test
    fun sharedGain_doesNotBoostQuietPair() {
        assertEquals(1.0, SharedGain.factorForCombinedPeak(0.2, -1.0), 0.0)
        assertEquals(0.0, SharedGain.gainDb(1.0), 0.0)
    }
}
