package com.gbw.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gbw.android.domain.SourceScoreBand
import com.gbw.android.domain.SourceScorePresentation

@Composable
internal fun SourceScoreBadge(score: Int) {
    val normalized = score.coerceIn(0, 100)
    val band = SourceScorePresentation.bandFor(normalized)
    val dark = isSystemInDarkTheme()

    // Every foreground/background pair is intentionally high-contrast in both themes.
    val containerColor = when (band) {
        SourceScoreBand.LOW -> if (dark) Color(0xFF5F1414) else Color(0xFFFFCDD2)
        SourceScoreBand.INTERMEDIATE -> if (dark) Color(0xFF5A4300) else Color(0xFFFFE8A3)
        SourceScoreBand.GOOD -> if (dark) Color(0xFF153D23) else Color(0xFFD8F5DF)
    }
    val contentColor = when (band) {
        SourceScoreBand.LOW -> if (dark) Color(0xFFFFC4C4) else Color(0xFF7F1D1D)
        SourceScoreBand.INTERMEDIATE -> if (dark) Color(0xFFFFE08A) else Color(0xFF5E4300)
        SourceScoreBand.GOOD -> if (dark) Color(0xFF9CE3AD) else Color(0xFF155E2B)
    }
    val accessibility =
        "Qualidade da fonte: ${band.accessibilityLabel}. Nota $normalized de 100."

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { contentDescription = accessibility },
    ) {
        Text(
            "$normalized/100 • ${band.label}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
