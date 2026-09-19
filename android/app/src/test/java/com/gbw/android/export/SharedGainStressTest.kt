package com.gbw.android.export

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SharedGainStressTest {
    @Test fun combinedPeakNeverExceedsTargetAfterSharedGain() {
        val target = SharedGain.targetLinear(-1.0)
        for (step in 1..2_000) {
            val peak = step / 250.0
            val factor = SharedGain.factorForCombinedPeak(peak, -1.0)
            assertTrue(factor in 0.0..1.0)
            val output = peak * factor
            if (peak > target) {
                assertTrue(abs(output - target) < 1e-9)
            } else {
                assertTrue(abs(output - peak) < 1e-9)
            }
        }
    }

    @Test fun invalidOrSilentPeakNeverBoosts() {
        assertTrue(SharedGain.factorForCombinedPeak(0.0) == 1.0)
        assertTrue(SharedGain.factorForCombinedPeak(Double.NaN) == 1.0)
        assertTrue(SharedGain.factorForCombinedPeak(Double.POSITIVE_INFINITY) == 1.0)
    }
}
