package com.codefixr.beummati.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codefixr.beummati.data.AppAppearance
import com.codefixr.beummati.data.AppThemeKind

val Teal = Color(0xFF1F474A)
val TealMuted = Color(0xFF386B6E)
val Brass = Color(0xFFB89452)
val Parchment = Color(0xFFF6F1E6)
val ParchmentDeep = Color(0xFFF0E8D9)
val Ink = Color(0xFF29241F)
val InkSecondary = Color(0xFF59524A)

@Immutable
data class BuPalette(
    val parchment: Color,
    val parchmentDeep: Color,
    val teal: Color,
    val tealSoft: Color,
    val tealMuted: Color,
    val brass: Color,
    val brassSoft: Color,
    val card: Color,
    val ink: Color,
    val inkSecondary: Color,
    val isDark: Boolean
)

val LocalBuPalette = staticCompositionLocalOf {
    AppThemeKind.MANUSCRIPT.palette()
}

fun AppThemeKind.palette(): BuPalette = when (this) {
    AppThemeKind.MANUSCRIPT -> BuPalette(
        parchment = Color(0xFFF6F1E6),
        parchmentDeep = Color(0xFFF0E8D9),
        teal = Color(0xFF1F474A),
        tealSoft = Color(0x1F1F474A),
        tealMuted = Color(0xFF386B6E),
        brass = Color(0xFFB89452),
        brassSoft = Color(0x2EB89452),
        card = Color(0xB8FFFFFF),
        ink = Color(0xFF29241F),
        inkSecondary = Color(0xFF59524A),
        isDark = false
    )
    AppThemeKind.MIDNIGHT -> BuPalette(
        parchment = Color(0xFF121418),
        parchmentDeep = Color(0xFF1C1F26),
        teal = Color(0xFF599E9E),
        tealSoft = Color(0x2E599E9E),
        tealMuted = Color(0xFF477A80),
        brass = Color(0xFFD9B361),
        brassSoft = Color(0x33D9B361),
        card = Color(0xFF24262E),
        ink = Color(0xFFF0EDE6),
        inkSecondary = Color(0xFFA6A399),
        isDark = true
    )
    AppThemeKind.EMERALD -> BuPalette(
        parchment = Color(0xFFF2F5ED),
        parchmentDeep = Color(0xFFE6EDE0),
        teal = Color(0xFF1A5947),
        tealSoft = Color(0x1F1A5947),
        tealMuted = Color(0xFF337A61),
        brass = Color(0xFF9E7A38),
        brassSoft = Color(0x2E9E7A38),
        card = Color(0xC7FFFFFF),
        ink = Color(0xFF1F2924),
        inkSecondary = Color(0xFF526157),
        isDark = false
    )
    AppThemeKind.OCEAN -> BuPalette(
        parchment = Color(0xFFEDF2F7),
        parchmentDeep = Color(0xFFE0E8F0),
        teal = Color(0xFF1F527A),
        tealSoft = Color(0x1F1F527A),
        tealMuted = Color(0xFF407394),
        brass = Color(0xFF8C7347),
        brassSoft = Color(0x2E8C7347),
        card = Color(0xCCFFFFFF),
        ink = Color(0xFF1F2938),
        inkSecondary = Color(0xFF596675),
        isDark = false
    )
    AppThemeKind.UMMATI -> BuPalette(
        parchment = Color(0xFF0F0F12),
        parchmentDeep = Color(0xFF1A1A1C),
        teal = Color(0xFFB81F24),
        tealSoft = Color(0x33B81F24),
        tealMuted = Color(0xFF8C2E33),
        brass = Color(0xFFEBEBEB),
        brassSoft = Color(0x1FFFFFFF),
        card = Color(0xFF212124),
        ink = Color(0xFFF5F5F5),
        inkSecondary = Color(0xFF9E9999),
        isDark = true
    )
    AppThemeKind.SOFT_DAY -> BuPalette(
        parchment = Color(0xFFFAFAF7),
        parchmentDeep = Color(0xFFF0F2ED),
        teal = Color(0xFF476B5C),
        tealSoft = Color(0x1F476B5C),
        tealMuted = Color(0xFF618575),
        brass = Color(0xFF94754D),
        brassSoft = Color(0x2994754D),
        card = Color.White,
        ink = Color(0xFF2E2E2E),
        inkSecondary = Color(0xFF737373),
        isDark = false
    )
}

private fun BuPalette.toLightScheme() = lightColorScheme(
    primary = teal,
    onPrimary = Color.White,
    primaryContainer = tealSoft,
    onPrimaryContainer = teal,
    secondary = brass,
    onSecondary = Color.White,
    secondaryContainer = brassSoft,
    onSecondaryContainer = ink,
    tertiary = tealMuted,
    onTertiary = Color.White,
    background = parchment,
    onBackground = ink,
    surface = parchment,
    onSurface = ink,
    surfaceVariant = parchmentDeep,
    onSurfaceVariant = inkSecondary,
    outline = inkSecondary.copy(alpha = 0.35f),
    outlineVariant = parchmentDeep
)

private fun BuPalette.toDarkScheme() = darkColorScheme(
    primary = teal,
    onPrimary = Color.White,
    primaryContainer = tealSoft,
    onPrimaryContainer = ink,
    secondary = brass,
    onSecondary = parchment,
    secondaryContainer = brassSoft,
    onSecondaryContainer = ink,
    tertiary = tealMuted,
    onTertiary = Color.White,
    background = parchment,
    onBackground = ink,
    surface = parchment,
    onSurface = ink,
    surfaceVariant = parchmentDeep,
    onSurfaceVariant = inkSecondary,
    outline = inkSecondary.copy(alpha = 0.4f),
    outlineVariant = card
)

val LocalParchment = staticCompositionLocalOf { Parchment }
val LocalTeal = staticCompositionLocalOf { Teal }
val LocalBrass = staticCompositionLocalOf { Brass }
val LocalInk = staticCompositionLocalOf { Ink }

private val BuTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun BeUmmatiTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    themeKind: AppThemeKind = AppThemeKind.MANUSCRIPT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val selected = themeKind.palette()

    // Named dark themes (Midnight, Ummati) always use their palette.
    // Light themes honour Appearance: Light stays as-picked; Dark/System-dark swaps to Midnight.
    val activePalette = when {
        selected.isDark -> selected
        appearance == AppAppearance.DARK -> AppThemeKind.MIDNIGHT.palette()
        appearance == AppAppearance.SYSTEM && systemDark -> AppThemeKind.MIDNIGHT.palette()
        else -> selected
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (activePalette.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        activePalette.isDark -> activePalette.toDarkScheme()
        else -> activePalette.toLightScheme()
    }

    CompositionLocalProvider(
        LocalBuPalette provides activePalette,
        LocalParchment provides activePalette.parchment,
        LocalTeal provides activePalette.teal,
        LocalBrass provides activePalette.brass,
        LocalInk provides activePalette.ink
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BuTypography,
            content = content
        )
    }
}
