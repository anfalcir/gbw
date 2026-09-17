package com.gbw.android.domain

enum class SeparationMode(val code: String, val publicLabel: String) {
    QUICK("B", "Rápida — recomendada no Android"),
    HIGH_QUALITY("A", "Alta qualidade"),
    COMPARE("AB", "Comparar as duas");

    companion object {
        val androidDefault = QUICK
    }
}

enum class PitchMode { TUNING, MANUAL, NONE }

enum class AudioKind { INSTRUMENT_OR_MIX, VOCAL }

enum class OutputFormat(val label: String, val extension: String) {
    WAV_FLOAT32("WAV 32-bit float", "wav"),
    WAV_24("WAV 24-bit", "wav"),
    FLAC_24("FLAC 24-bit", "flac")
}

data class WorkflowConfig(
    val artist: String = "",
    val song: String = "",
    val separatorMode: SeparationMode = SeparationMode.androidDefault,
    val finalSeparator: SeparationMode = SeparationMode.androidDefault,
    val pitchMode: PitchMode = PitchMode.TUNING,
    val originalTuning: String = "",
    val targetTuning: String = "Drop D",
    val semitones: Int = 0,
    val vocalFormants: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.FLAC_24,
)

data class ProjectState(
    val sourcePrepared: Boolean = false,
    val separationComplete: Boolean = false,
    val tuningConfirmed: Boolean = false,
    val exportComplete: Boolean = false,
)

data class ProjectDocument(
    val config: WorkflowConfig = WorkflowConfig(),
    val state: ProjectState = ProjectState(),
)
