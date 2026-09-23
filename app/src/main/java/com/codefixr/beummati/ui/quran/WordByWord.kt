@file:OptIn(ExperimentalLayoutApi::class)

package com.codefixr.beummati.ui.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.codefixr.beummati.data.QuranApi
import com.codefixr.beummati.data.QuranWord
import com.codefixr.beummati.ui.LoadState
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.arabicScale

/**
 * Word-by-word breakdown of one ayah — port of the iOS `WordByWordView`. Words wrap right to
 * left; tapping one shows its transliteration and gloss underneath.
 *
 * Payloads are cached on disk by [QuranApi.words], so an ayah opened once works offline.
 */
@Composable
fun WordByWordPanel(ayahKey: String, modifier: Modifier = Modifier) {
    var state by remember(ayahKey) { mutableStateOf<LoadState<List<QuranWord>>>(LoadState.Loading) }
    var selected by remember(ayahKey) { mutableStateOf<QuranWord?>(null) }

    LaunchedEffect(ayahKey) {
        state = runCatching { QuranApi.words(ayahKey) }.fold(
            { LoadState.Loaded(it) },
            { LoadState.Failed(it.message ?: "Couldn’t load words") }
        )
        selected = (state as? LoadState.Loaded)?.value?.firstOrNull()
    }

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            when (val s = state) {
                LoadState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                is LoadState.Failed -> MutedText("Word by word unavailable: ${s.message}")

                is LoadState.Loaded -> {
                    if (s.value.isEmpty()) {
                        MutedText("No word breakdown for this ayah.")
                    } else {
                        WordFlow(words = s.value, selected = selected, onSelect = { selected = it })
                        WordDetail(selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordFlow(words: List<QuranWord>, selected: QuranWord?, onSelect: (QuranWord) -> Unit) {
    val size = (18f * arabicScale()).coerceIn(14f, 34f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            words.forEach { word ->
                val isSelected = selected?.position == word.position
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.clickable { onSelect(word) }
                ) {
                    Text(
                        word.arabic,
                        fontSize = size.sp,
                        lineHeight = (size * 1.8f).sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WordDetail(word: QuranWord?) {
    if (word == null) {
        MutedText("Tap a word for its meaning", Modifier.padding(top = 10.dp))
        return
    }
    val size = (22f * arabicScale()).coerceIn(16f, 40f)
    Column(Modifier.padding(top = 10.dp)) {
        Text(
            word.arabic,
            fontSize = size.sp,
            lineHeight = (size * 1.7f).sp,
            fontWeight = FontWeight.Medium
        )
        if (word.transliteration.isNotBlank()) {
            MutedText(word.transliteration)
        }
        if (word.translation.isNotBlank()) {
            Text(
                word.translation,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
