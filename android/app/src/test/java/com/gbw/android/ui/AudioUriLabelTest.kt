package com.gbw.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioUriLabelTest {
    @Test
    fun `fallback keeps ordinary file names`() {
        assertEquals("song.wav", fallbackAudioDisplayName("primary:Music/song.wav"))
        assertEquals("mix final.m4a", fallbackAudioDisplayName("Music/mix final.m4a"))
    }

    @Test
    fun `fallback hides opaque provider identifiers`() {
        val opaque = "9DGVNfS1YuGsW_03b_PQKiatwP0v8Y5Jqn94sHSvC4n35r9UF9163CFjjKNIQ6MzdQ=="
        assertEquals("Arquivo selecionado", fallbackAudioDisplayName(opaque))
        assertEquals("Arquivo selecionado", fallbackAudioDisplayName(null))
    }
}
