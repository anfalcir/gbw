package com.gbw.android.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SafAudioStagerTest {
    @Test
    fun preservesCommonAudioExtension() {
        assertEquals("m4a", SafAudioStager.safeExtension("song.M4A"))
        assertEquals("flac", SafAudioStager.safeExtension("mix.flac"))
        assertEquals("wav", SafAudioStager.safeExtension("guitar.wav"))
    }

    @Test
    fun rejectsUnsafeOrMissingExtension() {
        assertEquals("bin", SafAudioStager.safeExtension("audio"))
        assertEquals("bin", SafAudioStager.safeExtension("audio.verylongextension"))
        assertEquals("bin", SafAudioStager.safeExtension("audio.m4a/../../x"))
        assertEquals("bin", SafAudioStager.safeExtension(null))
    }
}
