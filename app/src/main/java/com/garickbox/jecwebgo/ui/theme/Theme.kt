package com.garickbox.jecwebgo.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Dark = darkColorScheme(
    primary = Color(0xFF00A0D0), onPrimary = Color.White,
    background = Color(0xFF0A0A0A), surface = Color(0xFF161616),
    onBackground = Color.White, onSurface = Color.White
)
private val Light = lightColorScheme(
    primary = Color(0xFF007AA0), background = Color.White, surface = Color(0xFFF5F5F5)
)
@Composable fun JecWebTheme(useDark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (useDark) Dark else Light, content = content)
}
