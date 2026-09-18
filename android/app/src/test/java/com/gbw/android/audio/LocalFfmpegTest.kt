package com.gbw.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFfmpegTest {
    @Test
    fun compactsOnlyTheTailOfFfmpegOutput() {
        val result = LocalFfmpeg.compactDetail(
            "one\ntwo\nthree\nfour\nfive\n",
            80,
        )
        assertEquals("two | three | four | five", result)
    }

    @Test
    fun blankOutputHasNoDetail() {
        assertNull(LocalFfmpeg.compactDetail("   \n"))
    }

    @Test
    fun missingRuntimeClassKeepsTheClassDescriptorInTheDiagnostic() {
        val error =
            NoClassDefFoundError(
                "Failed resolution of: Lcom/arthenica/smartexception/java/Exceptions;"
            )
        assertEquals(
            "NoClassDefFoundError: Failed resolution of: " +
                "Lcom/arthenica/smartexception/java/Exceptions;",
            LocalFfmpeg.throwableDetail(error),
        )
    }
}
