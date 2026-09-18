package com.gbw.android.source

import com.gbw.android.domain.SourceProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpInfoParserTest {
    @Test
    fun `lossless format wins and preview is rejected`() {
        val json = JSONObject(
            """
            {
              "title":"Enemy",
              "artist":"Wolves At The Gate",
              "duration":197.0,
              "webpage_url":"https://example.test/enemy",
              "formats":[
                {"format_id":"preview","ext":"mp3","vcodec":"none","acodec":"mp3","format_note":"preview"},
                {"format_id":"flac","ext":"flac","vcodec":"none","acodec":"flac","filesize":123456}
              ]
            }
            """.trimIndent()
        )
        val draft = YtDlpInfoParser.toDraft(json, SourceProvider.BANDCAMP, "https://example.test/enemy")
        assertEquals("flac", draft.formatId)
        assertTrue(draft.quality.startsWith("LOSSLESS"))
        assertFalse(draft.previewOnly)
        assertTrue(draft.automaticDownloadSupported)
    }

    @Test
    fun `preview only source is marked unusable`() {
        val json = JSONObject(
            """
            {
              "title":"Enemy",
              "uploader":"Channel",
              "duration":30.0,
              "formats":[
                {"format_id":"sample","ext":"m4a","vcodec":"none","acodec":"aac","format_note":"sample"}
              ]
            }
            """.trimIndent()
        )
        val draft = YtDlpInfoParser.toDraft(json, SourceProvider.YOUTUBE, "https://example.test/enemy")
        assertTrue(draft.previewOnly)
        assertTrue(draft.quality.startsWith("PREVIEW/TRUNCADO"))
    }
}
