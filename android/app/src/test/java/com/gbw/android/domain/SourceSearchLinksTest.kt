package com.gbw.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchLinksTest {
    @Test
    fun `broad search links preserve artist and song across providers`() {
        val links = SourceSearchLinks.forRequest(
            SourceSearchRequest("Wolves At The Gate", "Enemy"),
        )

        assertEquals(3, links.size)
        assertTrue(links.any { it.label.contains("YouTube") && "Wolves+At+The+Gate+Enemy" in it.url })
        assertTrue(links.any { it.label.contains("SoundCloud") && "Wolves+At+The+Gate+Enemy" in it.url })
        assertTrue(links.any { it.label.contains("Bandcamp") && "Wolves+At+The+Gate+Enemy" in it.url })
    }

    @Test
    fun `blank request does not expose meaningless links`() {
        assertTrue(SourceSearchLinks.forRequest(SourceSearchRequest("", "")).isEmpty())
    }
}
