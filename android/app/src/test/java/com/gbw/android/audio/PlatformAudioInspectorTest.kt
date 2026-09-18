package com.gbw.android.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAudioInspectorTest {
    @Test
    fun aacM4aIsLossy() {
        assertFalse(PlatformAudioInspector.isLosslessMime("audio/mp4a-latm"))
    }

    @Test
    fun commonLossyCodecsStayLossy() {
        assertFalse(PlatformAudioInspector.isLosslessMime("audio/mpeg"))
        assertFalse(PlatformAudioInspector.isLosslessMime("audio/opus"))
    }

    @Test
    fun flacAndAlacAreLossless() {
        assertTrue(PlatformAudioInspector.isLosslessMime("audio/flac"))
        assertTrue(PlatformAudioInspector.isLosslessMime("audio/alac"))
    }
}
