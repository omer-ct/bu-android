package com.codefixr.beummati.ui.quran

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Ayah
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.QuranApi
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.Surah
import com.codefixr.beummati.data.SurahDetail
import com.codefixr.beummati.data.TafsirApi
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.ErrorBox
import com.codefixr.beummati.ui.LoadState
import com.codefixr.beummati.ui.LoadingBox
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton

@Composable
fun QuranListScreen(onOpenSurah: (Int) -> Unit) {
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { surahs = QuranApi.surahs() }

    val filtered = remember(surahs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) surahs else surahs.filter {
            it.number.toString() == q ||
                it.englishName.lowercase().contains(q) ||
                it.englishNameTranslation.lowercase().contains(q) ||
                it.name.contains(query.trim())
        }
    }

    ScreenScaffold(title = "Qur’an") { padding ->
        if (surahs.isEmpty()) {
            LoadingBox(Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search surah name or number") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(filtered, key = { it.number }) { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSurah(s.number) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberBadge(s.number)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.englishName, fontWeight = FontWeight.SemiBold)
                        val meta = listOf(s.englishNameTranslation, s.revelationType, if (s.numberOfAyahs > 0) "${s.numberOfAyahs} ayahs" else "")
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (meta.isNotEmpty()) MutedText(meta, maxLines = 1)
                    }
                    if (s.name.isNotBlank()) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
private fun NumberBadge(n: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SurahScreen(number: Int, onBack: () -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<SurahDetail>>(LoadState.Loading) }
    var showTafsir by remember { mutableStateOf(false) }
    var tafsir by remember { mutableStateOf<LoadState<Map<Int, String>>?>(null) }
    var noteFor by remember { mutableStateOf<Ayah?>(null) }
    // Words are fetched one ayah at a time, so expansion is opt-in per ayah.
    var expandedWords by remember(number) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(number, reload) {
        state = LoadState.Loading
        state = runCatching { QuranApi.surah(number) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }
    LaunchedEffect(showTafsir, number) {
        if (showTafsir && tafsir !is LoadState.Loaded) {
            tafsir = LoadState.Loading
            tafsir = runCatching { TafsirApi.urduIbnKathir(number) }
                .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
        }
    }

    val title = (state as? LoadState.Loaded)?.value?.surah?.englishName?.takeIf { it.isNotBlank() } ?: "Surah $number"

    ScreenScaffold(title = title, onBack = onBack) { padding ->
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> {
                val detail = s.value
                val tafsirMap = (tafsir as? LoadState.Loaded)?.value.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(detail.surah.name, style = MaterialTheme.typography.headlineMedium)
                            MutedText(
                                listOf(detail.surah.englishNameTranslation, detail.surah.revelationType, "${detail.ayahs.size} ayahs")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                            )
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = showTafsir,
                                    onClick = { showTafsir = !showTafsir },
                                    label = { Text("Tafsir Ibn Kathir (Urdu)") }
                                )
                            }
                            val t = tafsir
                            if (showTafsir && t == LoadState.Loading) MutedText("Loading tafsir…")
                            if (showTafsir && t is LoadState.Failed) MutedText("Tafsir unavailable: ${t.message}")
                        }
                    }
                    items(detail.ayahs, key = { it.key }) { ayah ->
                        AyahCard(
                            ayah = ayah,
                            surahName = detail.surah.englishName.ifBlank { "Surah $number" },
                            tafsir = if (showTafsir) tafsirMap[ayah.numberInSurah] else null,
                            showWords = ayah.key in expandedWords,
                            onNote = { noteFor = ayah },
                            onToggleWords = {
                                expandedWords = if (ayah.key in expandedWords) {
                                    expandedWords - ayah.key
                                } else {
                                    expandedWords + ayah.key
                                }
                            }
                        )
                    }
                    item {
                        MutedText(
                            "Arabic: Uthmani · English: Saheeh International · via alquran.cloud · " +
                                "word by word via quran.com"
                        )
                    }
                }
            }
        }
    }

    noteFor?.let { ayah ->
        NoteDialog(
            initialTitle = "Qur’an ${ayah.key}",
            initialBody = "",
            onDismiss = { noteFor = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "quran:${ayah.key}", route = Routes.surah(ayah.surah))
                noteFor = null
            }
        )
    }
}

@Composable
private fun AyahCard(
    ayah: Ayah,
    surahName: String,
    tafsir: String?,
    showWords: Boolean,
    onNote: () -> Unit,
    onToggleWords: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(ayah.numberInSurah)
            Box(Modifier.weight(1f))
            IconButton(onClick = onToggleWords) {
                Icon(
                    Icons.Outlined.Translate,
                    contentDescription = if (showWords) "Hide word by word" else "Word by word",
                    tint = if (showWords) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
            ShareMenuButton {
                ShareCard(
                    kind = "Qur’an",
                    reference = ayah.key,
                    arabic = ayah.arabic,
                    english = ayah.english
                )
            }
            IconButton(onClick = onNote) { Icon(Icons.Outlined.PostAdd, contentDescription = "Add note") }
            BookmarkButton(
                id = "ayah:${ayah.key}",
                bookmark = {
                    Bookmark(
                        id = "ayah:${ayah.key}",
                        kind = "Qur’an",
                        title = "$surahName ${ayah.key}",
                        body = ayah.english,
                        route = Routes.surah(ayah.surah)
                    )
                }
            )
        }
        RtlText(ayah.arabic, fontSize = 24, modifier = Modifier.padding(vertical = 6.dp))
        Text(ayah.english, style = MaterialTheme.typography.bodyMedium)
        if (showWords) {
            WordByWordPanel(ayah.key, Modifier.padding(top = 10.dp))
        }
        if (!tafsir.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                RtlText(tafsir, fontSize = 17, modifier = Modifier.padding(12.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }
}
