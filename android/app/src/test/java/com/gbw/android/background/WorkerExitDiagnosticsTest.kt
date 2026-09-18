package com.gbw.android.background

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerExitDiagnosticsTest {
    @Test
    fun nativeCrashAndMemoryReasonsAreHumanReadable() {
        assertEquals(
            "crash nativo",
            WorkerExitDiagnostics.reasonLabel(ApplicationExitInfo.REASON_CRASH_NATIVE),
        )
        assertEquals(
            "memória baixa",
            WorkerExitDiagnostics.reasonLabel(ApplicationExitInfo.REASON_LOW_MEMORY),
        )
    }

    @Test
    fun signaledProcessIsHumanReadable() {
        assertEquals(
            "processo sinalizado",
            WorkerExitDiagnostics.reasonLabel(ApplicationExitInfo.REASON_SIGNALED),
        )
    }
}
