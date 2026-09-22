package com.example.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    onPrimary = Color(0xFF10101A),
    primaryContainer = Color(0xFF2A2A40),
    background = Color(0xFF12121C),
    surface = Color(0xFF1B1B2A),
    surfaceVariant = Color(0xFF2A2A3D),
    onSurface = Color(0xFFEDEDF5),
    onSurfaceVariant = Color(0xFFB6B6C8),
    outline = Color(0xFF3A3A52),
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
