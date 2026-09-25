package com.codefixr.beummati.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Ayah
import com.codefixr.beummati.data.QuranAudioCache
import com.codefixr.beummati.data.QuranReciter
import com.codefixr.beummati.data.QuranTranslationVoice
import com.codefixr.beummati.player.QuranAyahPlayer
import com.codefixr.beummati.ui.MutedText
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranAudioBar(
    ayahs: List<Ayah>,
    highlightedAyah: Int?,
    onOpenVideoShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val play by QuranAyahPlayer.current.collectAsState()
    val reciter by QuranAudioCache.reciter.collectAsState()
    val voice by QuranAudioCache.voice.collectAsState()
    val repeatTr by QuranAudioCache.repeatTranslation.collectAsState()
    val ayahRepeat by QuranAudioCache.ayahRepeatCount.collectAsState()
    var reciterOpen by remember { mutableStateOf(false) }
    var voiceOpen by remember { mutableStateOf(false) }
    var savingSurah by remember { mutableStateOf(false) }
    var saveDetail by remember { mutableStateOf("") }
    val surahNumber = ayahs.firstOrNull()?.surah ?: 0
    val cached = remember(reciter, surahNumber, savingSurah) {
        if (surahNumber <= 0) 0 else QuranAudioCache.surahCachedCount(reciter, surahNumber)
    }
    val totalAyahs = ayahs.size.coerceAtLeast(1)

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Headphones, contentDescription = null)
            Text(
                "Ayah audio",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            TextButton(onClick = onOpenVideoShare) { Text("Video") }
            IconButton(
                onClick = {
                    if (play.playing) {
                        QuranAyahPlayer.pause()
                    } else if (play.surah > 0 && !play.loading) {
                        QuranAyahPlayer.resume()
                    } else {
                        val start = highlightedAyah ?: play.ayah.takeIf { it > 0 } ?: 1
                        QuranAyahPlayer.playSurah(context, ayahs, startAyah = start)
                    }
                }
            ) {
                Icon(
                    if (play.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (play.playing) "Pause" else "Play"
                )
            }
            IconButton(onClick = { QuranAyahPlayer.stop() }, enabled = play.surah > 0) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
        }

        if (play.surah > 0) {
            MutedText(
                buildString {
                    append("Ayah ${play.ayah}")
                    if (play.phase.isNotBlank()) append(" · ${play.phase}")
                    if (play.loading) append(" · loading")
                    play.error?.let { append(" · $it") }
                }
            )
        }

        ExposedDropdownMenuBox(expanded = reciterOpen, onExpandedChange = { reciterOpen = it }) {
            OutlinedTextField(
                value = reciter.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Reciter (everyayah.com)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reciterOpen) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = reciterOpen, onDismissRequest = { reciterOpen = false }) {
                QuranReciter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            QuranAudioCache.setReciter(option)
                            reciterOpen = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = voiceOpen, onExpandedChange = { voiceOpen = it }) {
            OutlinedTextField(
                value = voice.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Translation voice") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceOpen) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = voiceOpen, onDismissRequest = { voiceOpen = false }) {
                QuranTranslationVoice.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                MutedText(option.blurb)
                            }
                        },
                        onClick = {
                            QuranAudioCache.setVoice(option)
                            voiceOpen = false
                        }
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Play translation after Arabic", modifier = Modifier.weight(1f))
            Switch(
                checked = repeatTr,
                onCheckedChange = { QuranAudioCache.setRepeatTranslation(it) }
            )
        }

        Text("Repeat ayah", fontWeight = FontWeight.Medium)
        MutedText("Each Arabic ayah plays this many times before the next one.")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QuranAudioCache.AYAH_REPEAT_OPTIONS) { count ->
                FilterChip(
                    selected = count == ayahRepeat,
                    onClick = { QuranAudioCache.setAyahRepeatCount(count) },
                    label = { Text(if (count == 1) "Off" else "×$count") }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = false,
                onClick = { QuranAyahPlayer.playSurah(context, ayahs, startAyah = 1) },
                label = { Text("From start") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    val start = highlightedAyah ?: 1
                    QuranAyahPlayer.playSurah(context, ayahs, startAyah = start)
                },
                label = { Text("From here") }
            )
            FilterChip(
                selected = cached >= totalAyahs,
                enabled = !savingSurah && surahNumber > 0,
                onClick = {
                    savingSurah = true
                    scope.launch {
                        runCatching {
                            QuranAudioCache.downloadSurah(reciter, surahNumber) { p, detail ->
                                saveDetail = detail
                            }
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "Download failed", Toast.LENGTH_LONG).show()
                        }
                        savingSurah = false
                        saveDetail = ""
                        Toast.makeText(context, "Surah audio saved offline", Toast.LENGTH_SHORT).show()
                    }
                },
                label = {
                    Text(
                        when {
                            savingSurah -> saveDetail.ifBlank { "Saving…" }
                            cached >= totalAyahs -> "Surah offline ✓"
                            else -> "Save surah ($cached/$totalAyahs)"
                        }
                    )
                }
            )
        }
    }
}
