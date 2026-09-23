@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.AppAppearance
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlin.math.roundToInt

private const val SAMPLE_ARABIC = "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val appearance by SettingsStore.appearance.collectAsState()
    val arabicSize by SettingsStore.arabicFontSize.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()
    val showTransliteration by SettingsStore.showTransliteration.collectAsState()

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Theme") }
            item {
                ContentCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppAppearance.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { SettingsStore.setAppearance(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppAppearance.entries.size),
                                label = { Text(option.label) }
                            )
                        }
                    }
                    MutedText("Light and dark keep the same parchment and brass palette.", Modifier.padding(top = 8.dp))
                }
            }

            item { SectionHeader("Reading") }
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Arabic size", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${arabicSize.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = arabicSize,
                        onValueChange = { SettingsStore.setArabicFontSize(it) },
                        valueRange = SettingsStore.MIN_ARABIC_SIZE..SettingsStore.MAX_ARABIC_SIZE,
                        steps = 7
                    )
                    RtlText(SAMPLE_ARABIC, fontSize = 24)
                    Spacer(Modifier.padding(top = 8.dp))
                    ToggleRow(
                        title = "Show Urdu",
                        subtitle = "Urdu translations in hadith, stories and reminders",
                        checked = showUrdu,
                        onChange = { SettingsStore.setShowUrdu(it) }
                    )
                    ToggleRow(
                        title = "Show transliteration",
                        subtitle = "Latin transliteration under duas",
                        checked = showTransliteration,
                        onChange = { SettingsStore.setShowTransliteration(it) }
                    )
                    TextButton(onClick = { SettingsStore.resetReading() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Reset reading settings")
                    }
                }
            }

            item { SectionHeader("Content") }
            item {
                ContentCard {
                    Text("Offline packs", fontWeight = FontWeight.Medium)
                    MutedText(
                        if (Catalogs.hasOfflineTafsir) {
                            "Urdu Tafsir Ibn Kathir is bundled — surahs read it from assets with no network."
                        } else {
                            "Urdu Tafsir loads from the network and is cached per surah."
                        }
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("Attribution", fontWeight = FontWeight.Medium)
                    MutedText(Catalogs.audio.attribution.orEmpty())
                    MutedText(Catalogs.hisnAlMuslim.source.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            MutedText(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
