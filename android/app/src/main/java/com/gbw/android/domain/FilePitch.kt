package com.gbw.android.domain

object FilePitchRules {
    fun semitonesFromTunings(source: String, target: String): Int? = Tunings.delta(source, target)

    fun validateSemitones(value: Int): Boolean = value in -12..12

    fun suggestedName(
        originalName: String,
        sourceTuning: String?,
        targetTuning: String?,
        semitones: Int,
        outputFormat: OutputFormat,
    ): String {
        val base = originalName.substringBeforeLast('.').ifBlank { "audio" }
        fun compact(value: String) = value.replace(" ", "").replace("#", "Sharp")
        val suffix = if (!sourceTuning.isNullOrBlank() && !targetTuning.isNullOrBlank()) {
            "_${compact(sourceTuning)}_para_${compact(targetTuning)}"
        } else {
            "_pitch_${if (semitones >= 0) "+" else ""}${semitones}st"
        }
        return "$base$suffix.${outputFormat.extension}"
    }
}
