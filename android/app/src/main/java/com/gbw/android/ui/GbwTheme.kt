package com.gbw.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GbwDarkColors = darkColorScheme(
    primary = Color(0xFFC8B6FF),
    onPrimary = Color(0xFF281453),
    primaryContainer = Color(0xFF3E286A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFBFC4D6),
    onSecondary = Color(0xFF292E3B),
    secondaryContainer = Color(0xFF3F4452),
    onSecondaryContainer = Color(0xFFDCE1F3),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE7E8EE),
    surface = Color(0xFF151820),
    onSurface = Color(0xFFE7E8EE),
    surfaceVariant = Color(0xFF242832),
    onSurfaceVariant = Color(0xFFC5C8D2),
    outline = Color(0xFF8E919C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun GbwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GbwDarkColors,
        content = content,
    )
}
