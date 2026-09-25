package com.codefixr.beummati.ui.scholars

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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.ContentBrowseMode
import com.codefixr.beummati.data.ScholarQuote
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.BrowseModeBar
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.EnglishScriptText
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton
import com.codefixr.beummati.ui.SwipeItemPager
import com.codefixr.beummati.ui.UrduScriptText

private fun ScholarQuote.stableId(): String = "quote:$scholarId:${title.ifBlank { english.take(40) }.hashCode()}"

@Composable
fun ScholarsScreen() {
    val quotes = remember { Catalogs.scholarQuotes }
    val scholars = remember { quotes.map { it.scholarId to it.author }.distinct() }
    val themes = remember { quotes.map { it.theme }.filter { it.isNotBlank() }.distinct().sorted() }
    var scholar by remember { mutableStateOf<String?>(null) }
    var theme by remember { mutableStateOf<String?>(null) }
    val browseMode by SettingsStore.contentBrowseMode.collectAsState()

    val filtered = remember(scholar, theme) {
        quotes.filter { (scholar == null || it.scholarId == scholar) && (theme == null || it.theme == theme) }
    }
    val pagerState = rememberPagerState(pageCount = { filtered.size.coerceAtLeast(1) })

    ScreenScaffold(title = "Scholars") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrowseModeBar(
                mode = browseMode,
                onMode = { SettingsStore.setContentBrowseMode(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                item { FilterChip(selected = scholar == null, onClick = { scholar = null }, label = { Text("All scholars") }) }
                items(scholars, key = { it.first }) { (id, name) ->
                    FilterChip(selected = scholar == id, onClick = { scholar = if (scholar == id) null else id }, label = { Text(name) })
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                item { FilterChip(selected = theme == null, onClick = { theme = null }, label = { Text("All themes") }) }
                items(themes, key = { it }) { t ->
                    FilterChip(selected = theme == t, onClick = { theme = if (theme == t) null else t }, label = { Text(t) })
                }
            }
            when {
                filtered.isEmpty() -> EmptyState("No quotes", "Try a different scholar or theme.")
                browseMode == ContentBrowseMode.SLIDE -> SwipeItemPager(
                    items = filtered,
                    pagerState = pagerState,
                    key = { it.stableId() },
                    label = { i, q -> "${i + 1} / ${filtered.size} · ${q.author}" },
                    modifier = Modifier.weight(1f)
                ) { q, _ ->
                    QuoteCard(q, padded = false)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.stableId() }) { q -> QuoteCard(q) }
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: ScholarQuote, padded: Boolean = true) {
    ContentCard(modifier = if (padded) Modifier.padding(horizontal = 16.dp) else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (quote.title.isNotBlank()) Text(quote.title, fontWeight = FontWeight.SemiBold)
                MutedText(listOf(quote.author, quote.theme).filter { it.isNotBlank() }.joinToString(" · "))
            }
            ShareMenuButton {
                ShareCard(
                    title = quote.title.ifBlank { quote.author },
                    kind = quote.author,
                    reference = quote.reference,
                    arabic = quote.arabic,
                    english = if (quote.english.isBlank()) "" else "“${quote.english}”",
                    urdu = quote.urdu
                )
            }
            val id = quote.stableId()
            BookmarkButton(
                id = id,
                bookmark = {
                    Bookmark(id = id, kind = "Scholar", title = quote.title.ifBlank { quote.author }, subtitle = quote.author, body = quote.english)
                }
            )
        }
        if (quote.english.isNotBlank()) {
            EnglishScriptText("“${quote.english}”", Modifier.padding(top = 6.dp))
        }
        if (quote.arabic.isNotBlank()) ArabicScriptText(quote.arabic, Modifier.padding(top = 8.dp))
        if (quote.urdu.isNotBlank()) UrduScriptText(quote.urdu, Modifier.padding(top = 8.dp))
        if (quote.reference.isNotBlank()) MutedText(quote.reference, modifier = Modifier.padding(top = 8.dp))
    }
}
