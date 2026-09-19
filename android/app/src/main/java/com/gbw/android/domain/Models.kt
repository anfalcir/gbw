package com.gbw.android.domain

enum class OutputFormat(val label: String, val extension: String) {
    WAV_FLOAT32("WAV 32-bit float", "wav"),
    WAV_24("WAV 24-bit", "wav"),
    FLAC_24("FLAC 24-bit", "flac")
}

data class WorkflowConfig(
    val artist: String = "",
    val song: String = "",
    val outputFormat: OutputFormat = OutputFormat.FLAC_24,
)

data class ProjectState(
    val sourcePrepared: Boolean = false,
    val separationComplete: Boolean = false,
    val exportComplete: Boolean = false,
)

data class ProjectDocument(
    val config: WorkflowConfig = WorkflowConfig(),
    val state: ProjectState = ProjectState(),
)
