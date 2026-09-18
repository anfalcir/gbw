package com.gbw.android.separation

import com.gbw.android.audio.FloatWavWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SeparationResultFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun \`quick and high quality directories are deterministic\`() {
        val root = temp.root
        assertEquals(
            "jobs/job-123/separation/quick",
            SeparationResultFiles.outputDirectory(root, "job-123", "separation-quick")
                ?.relativeTo(root)?.path?.replace('\\', '/'),
        )
        assertEquals(
            "jobs/job-123/separation/high-quality",
            SeparationResultFiles.outputDirectory(root, "job-123", "separation-high-quality")
                ?.relativeTo(root)?.path?.replace('\\', '/'),
        )
        assertNull(SeparationResultFiles.outputDirectory(root, "job-123", "other"))
    }

    @Test
    fun \`validated result requires all six aligned float stems\`() {
        val root = temp.newFolder("files")
        val record = StoredSeparationResult(
            jobId = "job-six",
            type = "separation-quick",
            completedAt = 1L,
            frames = 128L,
            elapsedMillis = 10L,
            peakPssKb = 20L,
            runtimeIdentity = "test",
        )
        val directory = requireNotNull(
            SeparationResultFiles.outputDirectory(root, record.jobId, record.type)
        )
        directory.mkdirs()
        SeparationResultFiles.stemNames.forEach { stem ->
            FloatWavWriter(directory.resolve("$stem.wav"), 44_100, 2).use { writer ->
                writer.write(FloatArray(128 * 2))
            }
        }
        val validated = SeparationResultFiles.validate(root, record)
        assertNotNull(validated)
        assertEquals(6, validated?.stems?.size)
        directory.resolve("piano.wav").delete()
        assertNull(SeparationResultFiles.validate(root, record))
    }
}
