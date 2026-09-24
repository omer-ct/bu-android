@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.AppReadingLanguage
import com.codefixr.beummati.data.ScriptColors
import com.codefixr.beummati.data.ScriptFont
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.TextAlignMode
import com.codefixr.beummati.data.TranslationChoices
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.TripleScriptText
import com.codefixr.beummati.ui.fontFamilyFor
import com.codefixr.beummati.ui.scriptColor
import kotlin.math.roundToInt

private const val SAMPLE_ARABIC = "فَإِنَّ مَعَ ٱلْعُسْرِ يُسْرًا"
private const val SAMPLE_ENGLISH = "So truly where there is hardship there is also ease."
private const val SAMPLE_URDU = "پس یقیناً مشکل کے ساتھ آسانی ہے۔"

/** Android twin of the iOS `ReadingSettingsView`: languages, translations, per-script type, preview. */
@Composable
fun ReadingSettingsScreen(onBack: () -> Unit) {
    val readingLanguage by SettingsStore.readingLanguage.collectAsState()
    val showArabic by SettingsStore.showArabic.collectAsState()
    val showEnglish by SettingsStore.showEnglish.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()
    val showTransliteration by SettingsStore.showTransliteration.collectAsState()
    val englishId by SettingsStore.englishTranslationId.collectAsState()
    val urduId by SettingsStore.urduTranslationId.collectAsState()
    val shareArabic by SettingsStore.shareArabic.collectAsState()
    val shareEnglish by SettingsStore.shareEnglish.collectAsState()
    val shareUrdu by SettingsStore.shareUrdu.collectAsState()
    val textAlign by SettingsStore.textAlign.collectAsState()
    val showLangLabels by SettingsStore.showLangLabels.collectAsState()
    val arabicFont by SettingsStore.arabicFont.collectAsState()
    val englishFont by SettingsStore.englishFont.collectAsState()
    val urduFont by SettingsStore.urduFont.collectAsState()
    val arabicSize by SettingsStore.arabicFontSize.collectAsState()
    val englishSize by SettingsStore.englishFontSize.collectAsState()
    val urduSize by SettingsStore.urduFontSize.collectAsState()
    val arabicLineSpacing by SettingsStore.arabicLineSpacing.collectAsState()
    val urduLineSpacing by SettingsStore.urduLineSpacing.collectAsState()
    val arabicColor by SettingsStore.arabicColor.collectAsState()
    val englishColor by SettingsStore.englishColor.collectAsState()
    val urduColor by SettingsStore.urduColor.collectAsState()

    ScreenScaffold(title = "Reading", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Reading language") }
            item {
                ContentCard {
                    AppReadingLanguage.entries.forEach { mode ->
                        ChoiceRow(
                            label = mode.label,
                            selected = mode == readingLanguage,
                            onClick = { SettingsStore.setReadingLanguage(mode) }
                        )
                    }
                    MutedText(
                        "Pick Arabic + English, Arabic + Urdu, or all three. You can still fine-tune " +
                            "the toggles below.",
                        Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { SectionHeader("Translations") }
            item {
                ContentCard {
                    PickerRow(
                        title = "English",
                        value = TranslationChoices.englishLabel(englishId),
                        options = TranslationChoices.english.map { it.label },
                        onPick = { index -> SettingsStore.setEnglishTranslationId(TranslationChoices.english[index].id) }
                    )
                    PickerRow(
                        title = "Urdu",
                        value = TranslationChoices.urduLabel(urduId),
                        options = TranslationChoices.urdu.map { it.label },
                        onPick = { index -> SettingsStore.setUrduTranslationId(TranslationChoices.urdu[index].id) }
                    )
                    MutedText(
                        "Changing a translation reloads the surah you are reading with clean text " +
                            "(footnotes stripped).",
                        Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { SectionHeader("When sharing") }
            item {
                ContentCard {
                    SwitchRow("Include Arabic", shareArabic) { SettingsStore.setShareArabic(it) }
                    SwitchRow("Include English", shareEnglish) { SettingsStore.setShareEnglish(it) }
                    SwitchRow("Include Urdu", shareUrdu) { SettingsStore.setShareUrdu(it) }
                    MutedText(
                        "The reference is always included. Pick one, two, or all languages.",
                        Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { SectionHeader("Layout") }
            item {
                ContentCard {
                    PickerRow(
                        title = "English alignment",
                        value = textAlign.label,
                        options = TextAlignMode.entries.map { it.label },
                        onPick = { index -> SettingsStore.setTextAlign(TextAlignMode.entries[index]) }
                    )
                    MutedText("Arabic and Urdu stay right-aligned.", Modifier.padding(bottom = 4.dp))
                    SwitchRow("Language labels", showLangLabels) { SettingsStore.setShowLangLabels(it) }
                }
            }

            item { SectionHeader("Show / hide") }
            item {
                ContentCard {
                    SwitchRow("Arabic", showArabic) { SettingsStore.setShowArabic(it) }
                    SwitchRow("English", showEnglish) { SettingsStore.setShowEnglish(it) }
                    SwitchRow("Urdu", showUrdu) { SettingsStore.setShowUrdu(it) }
                    SwitchRow("Transliteration", showTransliteration) { SettingsStore.setShowTransliteration(it) }
                }
            }

            item { SectionHeader("Arabic") }
            item {
                ContentCard {
                    FontPickerRow(
                        title = "Font",
                        value = arabicFont,
                        options = ScriptFont.arabicChoices,
                        sample = "بِسْمِ ٱللَّٰهِ",
                        onPick = { SettingsStore.setArabicFont(it) }
                    )
                    MutedText(
                        "Indo-Pak and Nastaliq faces read the Indo-Pak script; Uthmanic Hafs, Amiri " +
                            "and Noto Naskh read Uthmani.",
                        Modifier.padding(bottom = 4.dp)
                    )
                    SliderRow(
                        title = "Size",
                        value = arabicSize,
                        range = SettingsStore.MIN_ARABIC_SIZE..SettingsStore.MAX_ARABIC_SIZE,
                        suffix = "pt",
                        onChange = { SettingsStore.setArabicFontSize(it) }
                    )
                    SliderRow(
                        title = "Line height",
                        value = arabicLineSpacing,
                        range = 0f..SettingsStore.MAX_ARABIC_LINE_SPACING,
                        onChange = { SettingsStore.setArabicLineSpacing(it) }
                    )
                    ColorRow(arabicColor) { SettingsStore.setArabicColor(it) }
                }
            }

            item { SectionHeader("English") }
            item {
                ContentCard {
                    FontPickerRow(
                        title = "Font",
                        value = englishFont,
                        options = ScriptFont.englishChoices,
                        sample = "Ease",
                        onPick = { SettingsStore.setEnglishFont(it) }
                    )
                    SliderRow(
                        title = "Size",
                        value = englishSize,
                        range = SettingsStore.MIN_ENGLISH_SIZE..SettingsStore.MAX_ENGLISH_SIZE,
                        suffix = "pt",
                        onChange = { SettingsStore.setEnglishFontSize(it) }
                    )
                    ColorRow(englishColor) { SettingsStore.setEnglishColor(it) }
                }
            }

            item { SectionHeader("Urdu") }
            item {
                ContentCard {
                    FontPickerRow(
                        title = "Font",
                        value = urduFont,
                        options = ScriptFont.urduChoices,
                        sample = "اردو",
                        onPick = { SettingsStore.setUrduFont(it) }
                    )
                    SliderRow(
                        title = "Size",
                        value = urduSize,
                        range = SettingsStore.MIN_URDU_SIZE..SettingsStore.MAX_URDU_SIZE,
                        suffix = "pt",
                        onChange = { SettingsStore.setUrduFontSize(it) }
                    )
                    SliderRow(
                        title = "Line height",
                        value = urduLineSpacing,
                        range = 0f..SettingsStore.MAX_URDU_LINE_SPACING,
                        onChange = { SettingsStore.setUrduLineSpacing(it) }
                    )
                    ColorRow(urduColor) { SettingsStore.setUrduColor(it) }
                }
            }

            item { SectionHeader("Preview") }
            item {
                ContentCard {
                    TripleScriptText(
                        arabic = SAMPLE_ARABIC,
                        english = SAMPLE_ENGLISH,
                        urdu = SAMPLE_URDU
                    )
                    MutedText("Ash-Sharh 94:6", Modifier.padding(top = 8.dp))
                }
            }

            item {
                ContentCard {
                    TextButton(onClick = { SettingsStore.resetReading() }) {
                        Text("Reset to defaults", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Title on the left, current value as a dropdown trigger on the right — the Form `Picker` shape. */
@Composable
private fun PickerRow(title: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        trailingIcon = {
                            if (option == value) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            open = false
                            onPick(index)
                        }
                    )
                }
            }
        }
    }
}

/** Same as [PickerRow], but each option is drawn in its own face so the choice is obvious. */
@Composable
private fun FontPickerRow(
    title: String,
    value: ScriptFont,
    options: List<ScriptFont>,
    sample: String,
    onPick: (ScriptFont) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value.label, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, modifier = Modifier.weight(1f))
                                Text(
                                    sample,
                                    fontFamily = fontFamilyFor(option),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            if (option == value) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            open = false
                            onPick(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                listOf(value.roundToInt().toString(), suffix).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.labelLarge
            )
        }
        val steps = (range.endInclusive - range.start).roundToInt() - 1
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0)
        )
    }
}

/** Swatch strip standing in for the iOS `ColorPicker`; "Theme" keeps the app's own ink. */
@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Colour", fontWeight = FontWeight.Medium)
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScriptColors.swatches.forEach { (argb, label) ->
                val fill = scriptColor(argb)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            width = if (argb == selected) 3.dp else 1.dp,
                            color = if (argb == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            },
                            shape = CircleShape
                        )
                        .clickable { onPick(argb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (argb == ScriptColors.THEME_DEFAULT) {
                        Text(
                            label.take(1),
                            color = MaterialTheme.colorScheme.surface,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
