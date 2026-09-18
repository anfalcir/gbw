package com.gbw.android.source

import com.gbw.android.domain.SourceProvider
import com.gbw.android.domain.SourceSearchDepth
import com.gbw.android.domain.SourceSearchRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicDiscoveryProviderTest {
    private val enemyFixture = """
        {
          "resultCount": 1,
          "results": [
            {
              "wrapperType": "track",
              "kind": "song",
              "artistName": "Wolves At The Gate",
              "collectionName": "Eclipse",
              "trackName": "Enemy",
              "trackViewUrl": "https://music.apple.com/us/album/enemy/1461517899?i=1461517911",
              "trackTimeMillis": 197000
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parser exposes exact catalog metadata without preview media`() {
        val items = AppleMusicSearchParser.parse(enemyFixture)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(SourceProvider.APPLE_MUSIC, item.provider)
        assertEquals("Enemy", item.title)
        assertEquals("Wolves At The Gate", item.uploader)
        assertEquals(197.0, item.durationSeconds, 0.001)
        assertTrue(item.quality.contains("Eclipse"))
        assertTrue(item.url.startsWith("https://music.apple.com/"))
    }

    @Test
    fun `provider falls back to US catalog only when local catalog lacks exact song`() = runBlocking {
        val urls = mutableListOf<String>()
        val provider = AppleMusicDiscoveryProvider(
            fetcher = { url ->
                urls += url
                if ("country=BR" in url) """{"resultCount":0,"results":[]}""" else enemyFixture
            },
            countryProvider = { "BR" },
        )

        val items = provider.search(
            SourceSearchRequest(
                artist = "Wolves At The Gate",
                song = "Enemy",
                depth = SourceSearchDepth.ROBUST,
            ),
        )

        assertEquals(2, urls.size)
        assertTrue("country=BR" in urls[0])
        assertTrue("country=US" in urls[1])
        assertEquals("Enemy", items.single().title)
    }
}
