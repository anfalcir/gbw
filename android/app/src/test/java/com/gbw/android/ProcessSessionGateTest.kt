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
}
