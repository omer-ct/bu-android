package com.codefixr.beummati.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.codefixr.beummati.data.AppAppearance

private val Accent = Color(0xFFE8C547)
private val Ink = Color(0xFF121216)
private val Paper = Color(0xFFF7F4EC)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    background = Color(0xFF121216),
    onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF1C1C22),
    onSurface = Color(0xFFF2F0EA)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A12),
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink
)

@Composable
fun BeUmmatiTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
