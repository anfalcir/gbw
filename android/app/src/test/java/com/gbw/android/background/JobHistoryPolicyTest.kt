package com.gbw.android.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobHistoryPolicyTest {
    private fun job(id: String = "a", state: String) = PersistedJob(
        id = id,
        type = "test",
        label = "Teste",
        state = state,
        progress = 0,
        startedAt = 1L,
        message = state,
    )

    @Test fun recordsFirstObservation() {
        assertTrue(JobHistoryPolicy.shouldRecord(null, job(state = "RUNNING")))
    }

    @Test fun ignoresProgressOnlyUpdates() {
        val previous = job(state = "RUNNING")
        val next = previous.copy(progress = 75, message = "75%")
        assertFalse(JobHistoryPolicy.shouldRecord(previous, next))
    }

    @Test fun recordsStateTransitionsAndNewJobs() {
        assertTrue(
            JobHistoryPolicy.shouldRecord(
                job(state = "RUNNING"),
                job(state = "SUCCESS"),
            )
        )
        assertTrue(
            JobHistoryPolicy.shouldRecord(
                job(id = "a", state = "SUCCESS"),
                job(id = "b", state = "RUNNING"),
            )
        )
    }
}
