package com.gbw.android.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCoalescingPolicyTest {
    private fun state(
        id: String,
        dirtySince: Long,
        changedAt: Long,
    ) = DirtyProjectState(
        projectId = id,
        desiredRevisionId = "r-$id",
        dirtySinceEpochMs = dirtySince,
        lastChangedAtEpochMs = changedAt,
    )

    @Test fun recentMutationWaitsForQuietWindow() {
        val now = 100_000L
        val states = listOf(state("a", now - 5_000L, now - 1_000L))
        assertTrue(
            BackupCoalescingPolicy.eligible(states, now, 30_000L, 300_000L).isEmpty()
        )
        assertEquals(
            29_000L,
            BackupCoalescingPolicy.nextDelayMs(states, now, 30_000L, 300_000L),
        )
    }

    @Test fun quietProjectIsEligible() {
        val now = 100_000L
        val states = listOf(state("a", now - 40_000L, now - 31_000L))
        assertEquals(
            listOf("a"),
            BackupCoalescingPolicy.eligible(states, now, 30_000L, 300_000L)
                .map { it.projectId },
        )
    }

    @Test fun maxLatencyPreventsPerpetualPostponement() {
        val now = 400_000L
        val states = listOf(state("a", now - 301_000L, now - 1_000L))
        assertEquals(
            1,
            BackupCoalescingPolicy.eligible(states, now, 30_000L, 300_000L).size,
        )
    }

    @Test fun emptyQueueHasNoDelay() {
        assertEquals(
            null,
            BackupCoalescingPolicy.nextDelayMs(emptyList(), 0L, 30_000L, 300_000L),
        )
    }
}
