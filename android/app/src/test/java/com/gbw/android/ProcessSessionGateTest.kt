package com.gbw.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessSessionGateTest {
    @Test
    fun `only first activity creation in a process starts a clean session`() {
        val gate = ProcessSessionGate()
        assertTrue(gate.beginSession())
        assertFalse(gate.beginSession())
        assertFalse(gate.beginSession())
    }

    @Test
    fun `new process gate starts a new clean session`() {
        val firstProcess = ProcessSessionGate()
        assertTrue(firstProcess.beginSession())
        assertFalse(firstProcess.beginSession())

        val nextProcess = ProcessSessionGate()
        assertTrue(nextProcess.beginSession())
    }

    @Test
    fun `fresh launcher activity starts clean even when process survived task removal`() {
        assertTrue(
            SessionLaunchPolicy.shouldStartClean(
                firstActivityInProcess = false,
                hasSavedInstanceState = false,
                launcherIntent = true,
            )
        )
    }

    @Test
    fun `rotation does not close the active project`() {
        assertFalse(
            SessionLaunchPolicy.shouldStartClean(
                firstActivityInProcess = false,
                hasSavedInstanceState = true,
                launcherIntent = true,
            )
        )
    }

    @Test
    fun `simple foreground return without recreation keeps the session`() {
        assertFalse(
            SessionLaunchPolicy.shouldStartClean(
                firstActivityInProcess = false,
                hasSavedInstanceState = false,
                launcherIntent = false,
            )
        )
    }
}
