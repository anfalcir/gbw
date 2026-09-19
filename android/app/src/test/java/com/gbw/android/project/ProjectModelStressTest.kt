package com.gbw.android.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModelStressTest {
    @Test fun thousandProjectIdentitiesRemainUniqueAndSearchable() {
        val projects = (0 until 1_000).map { index ->
            ProjectManifest.new(
                name = "ignored",
                artist = "Artist " + ('A'.code + (index % 25)).toChar(),
                song = "Song $index",
                nowEpochMs = index.toLong(),
            )
        }
        assertEquals(1_000, projects.map { it.projectId }.toSet().size)
        assertEquals(
            40,
            projects.count { projectMatchesSearch(it, "artist h") },
        )
        assertEquals(1, projects.count { projectMatchesSearch(it, "song 999") })
    }

    @Test fun copyNamingRemainsDistinctAcrossLargeSiblingSet() {
        val existing = mutableListOf("Song")
        repeat(100) {
            existing += nextProjectCopySong("Song", existing)
        }
        assertEquals(101, existing.map(::foldProjectSearchText).toSet().size)
        assertEquals("Song (Cópia 100)", existing.last())
    }

    @Test fun pathTraversalVariantsStayRejected() {
        val invalid = listOf(
            "../secret",
            "stems/../../secret",
            "./project.json",
            "folder/../file",
            "C:/absolute",
            "a:b",
        )
        invalid.forEach { path ->
            assertTrue(runCatching { normalizeRelativePath(path) }.isFailure)
        }
    }
}
