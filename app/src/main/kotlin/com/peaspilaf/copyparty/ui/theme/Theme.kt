package com.peaspilaf.copyparty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.peaspilaf.copyparty.core.ThemeMode

private val Gold = Color(0xFFF4C95D)
private val GoldDark = Color(0xFF8A6D1F)
private val Navy = Color(0xFF0F172A)
private val Ink = Color(0xFF1E293B)
private val Mist = Color(0xFFF7F7F4)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B5200),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF231B00),
    secondary = Color(0xFF4F5B6E),
    onSecondary = Color.White,
    background = Mist,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Navy,
    primaryContainer = GoldDark,
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFB9C4D6),
    onSecondary = Navy,
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFE2E8F0),
)

@Composable
fun CopypartyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
