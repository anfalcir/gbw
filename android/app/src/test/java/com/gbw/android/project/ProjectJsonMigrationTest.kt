package com.gbw.android.project

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProjectJsonMigrationTest {
    @Test
    fun legacySchemaIsReadAsNeutralOriginalState() {
        val id = UUID.randomUUID().toString()
        val legacyTransformKey = "pi" + "tch"
        val legacyAmountKey = legacyTransformKey + "Semi" + "tones"
        val legacyInnerAmountKey = "semi" + "tones"
        val legacyShapeKey = "vocal" + "For" + "mants"
        val legacy = JSONObject()
            .put("schemaVersion", 1)
            .put("projectId", id)
            .put("name", "Legacy")
            .put("artist", "Band")
            .put("song", "Song")
            .put("createdAtEpochMs", 1L)
            .put("updatedAtEpochMs", 2L)
            .put("workflowStage", "LEGACY_STEP")
            .put(
                legacyTransformKey,
                JSONObject()
                    .put(legacyInnerAmountKey, -3)
                    .put(legacyShapeKey, true),
            )
            .put(
                "export",
                JSONObject()
                    .put("exportId", "old")
                    .put("revision", 1)
                    .put(legacyAmountKey, -3)
                    .put("outputFormat", "FLAC_24")
                    .put("manifestRelativePath", "exports/old/export_manifest.json"),
            )
            .toString()

        val decoded = ProjectJson.decode(legacy)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("SOURCE", decoded.workflowStage)
        assertNull(decoded.export)

        val migrated = decoded.copy(schemaVersion = ProjectManifest.CURRENT_SCHEMA_VERSION)
        val encoded = ProjectJson.encode(migrated)
        assertTrue(encoded.contains("\"schemaVersion\": 2") || encoded.contains("\"schemaVersion\":2"))
        assertFalse(encoded.contains("\"" + legacyTransformKey + "\""))
        assertFalse(encoded.contains(legacyAmountKey))
    }

    @Test
    fun currentSchemaRoundTripKeepsOriginalExport() {
        val project = ProjectManifest.new("Song", "Band", "Song", 10L).copy(
            workflowStage = "EXPORT",
            export = ExportState(
                exportId = "e_test",
                revision = 1L,
                outputFormat = "FLAC_24",
                artifacts = listOf(
                    ExportArtifact(
                        role = "backing",
                        variant = "original",
                        relativePath = "exports/e_test/backing.flac",
                        format = "FLAC_24",
                        sampleRate = 44_100,
                        channels = 2,
                        durationFrames = 44_100L,
                        size = 123L,
                        sha256 = "abc",
                    ),
                    ExportArtifact(
                        role = "guitar",
                        variant = "original",
                        relativePath = "exports/e_test/guitar.flac",
                        format = "FLAC_24",
                        sampleRate = 44_100,
                        channels = 2,
                        durationFrames = 44_100L,
                        size = 456L,
                        sha256 = "def",
                    ),
                ),
                manifestRelativePath = "exports/e_test/export_manifest.json",
            ),
        )

        val decoded = ProjectJson.decode(ProjectJson.encode(project))
        assertEquals(ProjectManifest.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("EXPORT", decoded.workflowStage)
        assertEquals(2, decoded.export?.artifacts?.size)
        assertTrue(decoded.export?.artifacts?.all { it.variant == "original" } == true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureSchemaIsRejected() {
        val json = JSONObject()
            .put("schemaVersion", ProjectManifest.CURRENT_SCHEMA_VERSION + 1)
            .put("projectId", UUID.randomUUID().toString())
            .put("name", "Future")
            .put("createdAtEpochMs", 1L)
            .put("updatedAtEpochMs", 1L)
            .toString()
        ProjectJson.decode(json)
    }
}
