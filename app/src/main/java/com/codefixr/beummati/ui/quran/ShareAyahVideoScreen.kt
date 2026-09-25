package com.codefixr.beummati.ui.quran

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import com.codefixr.beummati.data.AyahShareLang
import com.codefixr.beummati.data.AyahVideoExporter
import com.codefixr.beummati.data.AyahVideoRequest
import com.codefixr.beummati.data.QuranAudioCache
import com.codefixr.beummati.data.QuranReciter
import com.codefixr.beummati.data.QuranTranslationVoice
import com.codefixr.beummati.data.ScriptFont
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun ShareAyahVideoScreen(
    surahName: String,
    ayahs: List<Ayah>,
    arabicFont: ScriptFont,
    initialSelected: Set<Int> = emptySet(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedReciter by QuranAudioCache.reciter.collectAsState()
    val cachedVoice by QuranAudioCache.voice.collectAsState()
    var reciterPick by remember { mutableStateOf(cachedReciter) }
    var translationPick by remember {
        mutableStateOf(
            when (cachedVoice) {
                QuranTranslationVoice.URDU_SHAMSHAD -> QuranTranslationVoice.URDU_SHAMSHAD
                else -> QuranTranslationVoice.URDU_FARHAT
            }
        )
    }
    var selected by remember {
        mutableStateOf(
            initialSelected.ifEmpty { ayahs.take(3).map { it.numberInSurah }.toSet() }
        )
    }
    var lang by remember { mutableStateOf(AyahShareLang.ARABIC_URDU) }
    var busy by remember { mutableStateOf(false) }

    val needsUrdu = lang == AyahShareLang.URDU_ONLY ||
        lang == AyahShareLang.ARABIC_URDU ||
        lang == AyahShareLang.ALL
    val needsEnglish = lang == AyahShareLang.ENGLISH_ONLY ||
        lang == AyahShareLang.ARABIC_ENGLISH ||
        lang == AyahShareLang.ALL

    ScreenScaffold(title = "Share as video", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(surahName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                MutedText(
                    "1) Languages  2) Arabic reciter  3) Urdu voice (if needed)  4) Ayahs. " +
                        "On-screen Urdu/English text comes from your Reading settings; " +
                        "spoken Urdu is from everyayah.com."
                )
            }

            item { SectionHeader("1 · Languages on screen + in audio") }
            items(AyahShareLang.entries, key = { it.name }) { option ->
                ContentCard(onClick = { lang = option }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = lang == option, onClick = { lang = option })
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(option.label, fontWeight = FontWeight.SemiBold)
                            MutedText(option.blurb, Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            item { SectionHeader("2 · Arabic reciter") }
            items(QuranReciter.entries, key = { "r_${it.id}" }) { option ->
                ContentCard(onClick = {
                    reciterPick = option
                    QuranAudioCache.setReciter(option)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = reciterPick == option,
                            onClick = {
                                reciterPick = option
                                QuranAudioCache.setReciter(option)
                            }
                        )
                        Text(option.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            if (needsUrdu) {
                item { SectionHeader("3 · Urdu translation voice") }
                items(
                    listOf(QuranTranslationVoice.URDU_FARHAT, QuranTranslationVoice.URDU_SHAMSHAD),
                    key = { "tr_${it.id}" }
                ) { option ->
                    ContentCard(onClick = {
                        translationPick = option
                        QuranAudioCache.setVoice(option)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = translationPick == option,
                                onClick = {
                                    translationPick = option
                                    QuranAudioCache.setVoice(option)
                                }
                            )
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(option.label, fontWeight = FontWeight.SemiBold)
                                MutedText(option.blurb, Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (needsEnglish) {
                item {
                    ContentCard {
                        Text("English audio", fontWeight = FontWeight.SemiBold)
                        MutedText(
                            "Uses your device voice to speak the English translation shown on screen.",
                            Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item { SectionHeader(if (needsUrdu) "4 · Ayahs (${selected.size} selected)" else "3 · Ayahs (${selected.size} selected)") }
            items(ayahs, key = { it.key }) { ayah ->
                val checked = ayah.numberInSurah in selected
                ContentCard(onClick = {
                    selected = if (checked) selected - ayah.numberInSurah else selected + ayah.numberInSurah
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (it) selected + ayah.numberInSurah else selected - ayah.numberInSurah
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Ayah ${ayah.numberInSurah}", fontWeight = FontWeight.SemiBold)
                            MutedText(
                                ayah.english.ifBlank { ayah.urdu.ifBlank { ayah.arabic } }.take(90),
                                Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            item {
                val voiceLabel = when {
                    needsUrdu && translationPick == QuranTranslationVoice.URDU_SHAMSHAD ->
                        "Urdu · Shamshad Ali Khan"
                    needsUrdu -> "Urdu · Farhat Hashmi"
                    needsEnglish -> "English · device voice"
                    else -> "none"
                }
                Button(
                    onClick = {
                        val picks = ayahs.filter { it.numberInSurah in selected }.sortedBy { it.numberInSurah }
                        if (picks.isEmpty()) {
                            Toast.makeText(context, "Select at least one ayah", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val ok = AyahVideoExporter.exportAndShare(
                                context,
                                AyahVideoRequest(
                                    surahName = surahName,
                                    ayahs = picks,
                                    lang = lang,
                                    arabicFont = arabicFont,
                                    reciter = reciterPick,
                                    translationVoice = when {
                                        needsUrdu -> translationPick
                                        needsEnglish -> QuranTranslationVoice.ENGLISH_TTS
                                        else -> QuranTranslationVoice.NONE
                                    }
                                )
                            )
                            busy = false
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    "Could not create video — check network for audio",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !busy && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (busy) "Downloading audio & rendering…" else "Create & share video")
                }
                MutedText(
                    "Arabic: ${reciterPick.label} · Translation audio: $voiceLabel · ${lang.label}",
                    Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
