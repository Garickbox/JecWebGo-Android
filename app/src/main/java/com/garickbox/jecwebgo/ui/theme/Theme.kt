package com.garickbox.jecwebgo.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Dark = darkColorScheme(
    primary = Color(0xFF00A0D0), onPrimary = Color.White,
    tertiary = Color(0xFFFF6B35), onTertiary = Color.White,
    background = Color(0xFF0A0A0A), surface = Color(0xFF161616),
    onBackground = Color.White, onSurface = Color.White
)
private val Light = lightColorScheme(
    primary = Color(0xFF007AA0),
    tertiary = Color(0xFFE55100), onTertiary = Color.White,
    background = Color.White, surface = Color(0xFFF5F5F5)
)
@Composable fun JecWebTheme(useDark: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (useDark) Dark else Light
    MaterialTheme(colorScheme = scheme) {
        // ФИКС: после удаления Scaffold в CP10.125 LocalContentColor = Color.Black
        // → все Text без явного color рендерились чёрным на чёрном (Settings/About/Home).
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground,
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.bodyLarge
        ) {
            content()
        }
    }
}
