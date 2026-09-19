package com.gbw.android.domain

enum class SourceScoreBand(val label: String, val accessibilityLabel: String) {
    LOW("Baixa", "qualidade baixa"),
    INTERMEDIATE("Intermediária", "qualidade intermediária"),
    GOOD("Boa", "boa qualidade"),
}

/**
 * Presentation bands for the normalized source-ranking score (0..100).
 *
 * Usable candidates start from a 45..50 provider baseline. Scores below 55
 * therefore reflect meaningful penalties or weak evidence. Scores from 55
 * through 74 are usable but less strongly corroborated. Reaching 75 normally
 * requires strong title/artist identity plus quality, official-profile, or
 * duration-consensus evidence after penalties.
 */
object SourceScorePresentation {
    const val INTERMEDIATE_MIN = 55
    const val GOOD_MIN = 75

    fun bandFor(score: Int): SourceScoreBand =
        when (score.coerceIn(0, 100)) {
            in GOOD_MIN..100 -> SourceScoreBand.GOOD
            in INTERMEDIATE_MIN until GOOD_MIN -> SourceScoreBand.INTERMEDIATE
            else -> SourceScoreBand.LOW
        }
}
