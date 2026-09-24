package com.codefixr.beummati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefixr.beummati.data.ScriptColors
import com.codefixr.beummati.data.ScriptFont
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.TextAlignMode

/** Resolves a [ScriptFont] to a Compose family — bundled file when there is one, else a platform face. */
fun fontFamilyFor(font: ScriptFont): FontFamily {
    val res = font.fontRes
    if (res != null) return FontFamily(Font(res))
    return when (font) {
        ScriptFont.SERIF -> FontFamily.Serif
        ScriptFont.MONO -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

/** `0` means "keep the theme's ink", so light and dark both stay readable. */
@Composable
fun scriptColor(stored: Int): Color =
    if (stored == ScriptColors.THEME_DEFAULT) MaterialTheme.colorScheme.onSurface else Color(stored)

private fun TextAlignMode.asTextAlign(): TextAlign = when (this) {
    TextAlignMode.CENTER -> TextAlign.Center
    TextAlignMode.NATURAL -> TextAlign.End
    TextAlignMode.LEADING -> TextAlign.Start
}

/** Ayah Arabic in the chosen face, size, line height and colour. */
@Composable
fun ArabicScriptText(text: String, modifier: Modifier = Modifier) {
    val font by SettingsStore.arabicFont.collectAsState()
    val size by SettingsStore.arabicFontSize.collectAsState()
    val spacing by SettingsStore.arabicLineSpacing.collectAsState()
    val color by SettingsStore.arabicColor.collectAsState()
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = fontFamilyFor(font),
            fontSize = size.sp,
            lineHeight = (size * 1.8f + spacing).sp,
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Right,
            color = scriptColor(color)
        )
    )
}

/** Urdu translation prose — always right-aligned, Nastaliq faces get the extra line height. */
@Composable
fun UrduScriptText(text: String, modifier: Modifier = Modifier) {
    val font by SettingsStore.urduFont.collectAsState()
    val size by SettingsStore.urduFontSize.collectAsState()
    val spacing by SettingsStore.urduLineSpacing.collectAsState()
    val color by SettingsStore.urduColor.collectAsState()
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = fontFamilyFor(font),
            fontSize = size.sp,
            lineHeight = (size * 1.9f + spacing).sp,
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Right,
            color = scriptColor(color)
        )
    )
}

/** English translation prose, honouring the alignment picked in Reading settings. */
@Composable
fun EnglishScriptText(text: String, modifier: Modifier = Modifier) {
    val font by SettingsStore.englishFont.collectAsState()
    val size by SettingsStore.englishFontSize.collectAsState()
    val color by SettingsStore.englishColor.collectAsState()
    val align by SettingsStore.textAlign.collectAsState()
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = fontFamilyFor(font),
            fontSize = size.sp,
            lineHeight = (size * 1.55f).sp,
            textAlign = align.asTextAlign(),
            color = scriptColor(color)
        )
    )
}

/** The small script caption above each block, shown when language labels are on. */
@Composable
private fun LanguageLabel(label: String, subtitle: String?, rtl: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rtl) Spacer(Modifier.weight(1f))
        if (subtitle != null && rtl) {
            Text(
                subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (subtitle != null && !rtl) {
            Text(
                subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        if (!rtl) Spacer(Modifier.weight(1f))
    }
}

/**
 * Arabic / English / Urdu stacked in reading order, skipping whatever is switched off. The Android
 * twin of the iOS `TripleText`, used by the Qur’an reader and the settings preview.
 */
@Composable
fun TripleScriptText(
    arabic: String,
    english: String,
    urdu: String,
    modifier: Modifier = Modifier
) {
    val showArabic by SettingsStore.showArabic.collectAsState()
    val showEnglish by SettingsStore.showEnglish.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()
    val labels by SettingsStore.showLangLabels.collectAsState()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showArabic && arabic.isNotBlank()) {
            if (labels) LanguageLabel("العربية", "Arabic", rtl = true)
            ArabicScriptText(arabic)
        }
        if (showEnglish && english.isNotBlank()) {
            if (labels) LanguageLabel("English", null, rtl = false)
            EnglishScriptText(english)
        }
        if (showUrdu && urdu.isNotBlank()) {
            if (labels) LanguageLabel("اردو", "Urdu", rtl = true)
            UrduScriptText(urdu)
        }
    }
}
