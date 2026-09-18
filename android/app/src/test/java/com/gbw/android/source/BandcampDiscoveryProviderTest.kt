package com.gbw.android.source

import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandcampDiscoveryProviderTest {
    private val fixture = """
        <ul>
          <li class="searchresult data-search">
            <div class="heading"><a href="https://wolvesatthegate.bandcamp.com/track/enemy">Enemy</a></div>
            <div class="subhead">by Wolves At The Gate</div>
          </li>
          <li class="searchresult">
            <div class="heading"><a href="https://flyleaf.bandcamp.com/track/all-around-me?from=search">All &amp; Around Me</a></div>
            <div class="subhead">by Flyleaf</div>
          </li>
        </ul>
    """.trimIndent()

    @Test
    fun `parser extracts track title artist and canonical url`() {
        val items = BandcampSearchParser.parse(fixture)
        assertEquals(2, items.size)
        assertEquals("Enemy", items[0].title)
        assertEquals("Wolves At The Gate", items[0].uploader)
        assertEquals("https://wolvesatthegate.bandcamp.com/track/enemy", items[0].url)
        assertEquals("All & Around Me", items[1].title)
    }

    @Test
    fun `parser falls back to urls when result markup changes`() {
        val html = """
            <main>
              something https://artist-name.bandcamp.com/track/song-name
              duplicated https://artist-name.bandcamp.com/track/song-name
            </main>
        """.trimIndent()
        val items = BandcampSearchParser.parse(html)
        assertEquals(1, items.size)
        assertEquals("song name", items.single().title)
        assertEquals("artist name", items.single().uploader)
    }

    @Test
    fun `provider respects robust and maximum limits`() = runBlocking {
        val many = buildString {
            append("<main>")
            for (i in 1..20) {
                append(" https://artist.bandcamp.com/track/song-$i ")
            }
            append("</main>")
        }
        val provider = BandcampDiscoveryProvider { many }

        val robust = provider.search(SourceSearchRequest("", "song", SourceSearchDepth.ROBUST))
        val maximum = provider.search(SourceSearchRequest("", "song", SourceSearchDepth.MAXIMUM))

        assertEquals(8, robust.size)
        assertEquals(15, maximum.size)
        assertTrue(maximum.all { it.url.startsWith("https://") })
    }
}
