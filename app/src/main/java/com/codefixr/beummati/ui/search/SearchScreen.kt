package com.codefixr.beummati.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Destination
import com.codefixr.beummati.data.SearchHit
import com.codefixr.beummati.data.SearchIndex
import com.codefixr.beummati.data.SearchKind
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (Destination) -> Unit) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<SearchKind?>(null) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        hits = withContext(Dispatchers.IO) { SearchIndex.search(query) }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val kinds = remember(hits) { hits.map { it.kind }.distinct() }
    val visible = remember(hits, kind) { if (kind == null) hits else hits.filter { it.kind == kind } }
    val grouped = remember(visible) { visible.groupBy { it.kind } }

    ScreenScaffold(title = "Search", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Qur’an & Hadith text (EN / AR), Urdu kitābs, series…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus)
            )

            if (kinds.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = kind == null,
                            onClick = { kind = null },
                            label = { Text("All (${hits.size})") }
                        )
                    }
                    items(kinds, key = { it.name }) { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = if (kind == option) null else option },
                            label = { Text("${option.label} (${hits.count { it.kind == option }})") }
                        )
                    }
                }
            }

            when {
                query.trim().length < 2 -> EmptyState(
                    "Search Qur’an and Hadith",
                    "Ayahs in EN / UR / AR, hadith matn in EN / AR, plus Urdu kitāb names, series, quotes and duas."
                )
                visible.isEmpty() -> EmptyState("No matches", "Try a shorter word, an ayah phrase, or a name like “Umar”.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (group, list) ->
                        item(key = "header-${group.name}") { SectionHeader(group.label) }
                        items(list.size, key = { "${group.name}-$it-${list[it].title}" }) { index ->
                            val hit = list[index]
                            HitCard(hit, onClick = { onOpen(hit.destination) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: SearchHit, onClick: () -> Unit) {
    ContentCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hit.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hit.subtitle.isNotBlank()) MutedText(hit.subtitle, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        if (hit.body.isNotBlank()) MutedText(hit.body, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
    }
}
