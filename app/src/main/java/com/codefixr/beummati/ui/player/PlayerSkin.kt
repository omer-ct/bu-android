package com.codefixr.beummati.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.codefixr.beummati.data.PlayerSkin
import com.codefixr.beummati.data.SettingsStore

/**
 * Palette the lecture player paints itself with. Kept separate from the app `MaterialTheme` so the
 * player can stay dark while the rest of the app follows the system, matching iOS.
 */
data class PlayerSkinColors(
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val ink: Color,
    val inkSecondary: Color,
    val controlFill: Color
) {
    val background: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom))

    /** Readable foreground for anything filled with [accent]. */
    val onAccent: Color get() = if (accent.luminanceish() > 0.6f) Color(0xFF14110C) else Color.White
}

private fun Color.luminanceish(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun skinColors(skin: PlayerSkin): PlayerSkinColors = when (skin) {
    PlayerSkin.DARK -> PlayerSkinColors(
        bgTop = Color(0xFF1B1B21),
        bgBottom = Color(0xFF0E0E12),
        accent = Color(0xFFE8C547),
        ink = Color(0xFFF2F0EA),
        inkSecondary = Color(0xFF9C988C),
        controlFill = Color(0xFF2A2A33)
    )
    PlayerSkin.LIGHT -> PlayerSkinColors(
        bgTop = Color(0xFFFBF8F1),
        bgBottom = Color(0xFFEFE8DA),
        accent = Color(0xFF8A6A12),
        ink = Color(0xFF1B1813),
        inkSecondary = Color(0xFF6E665A),
        controlFill = Color(0xFFE2D9C7)
    )
    PlayerSkin.SOUNDCLOUD -> PlayerSkinColors(
        bgTop = Color(0xFF241A12),
        bgBottom = Color(0xFF120D09),
        accent = Color(0xFFFF7700),
        ink = Color(0xFFFFF6EC),
        inkSecondary = Color(0xFFB79878),
        controlFill = Color(0xFF3A2718)
    )
}

@Composable
fun playerSkinColors(): PlayerSkinColors {
    val skin by SettingsStore.playerSkin.collectAsState()
    return skinColors(skin)
}
