package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RetroGreen,
    secondary = RetroOlive,
    tertiary = TerminalBlue,
    background = RetroBackground,
    surface = RetroCard,
    onPrimary = Color(0xFF1A1C1E),
    onSecondary = RetroText,
    onBackground = RetroText,
    onSurface = RetroText,
    surfaceVariant = RetroGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Cyber Mode
    dynamicColor: Boolean = false, // Preserve our retro colors instead of system colors
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
