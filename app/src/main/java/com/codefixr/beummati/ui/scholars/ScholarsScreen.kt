package com.codefixr.beummati.ui.scholars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.ScholarQuote
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.shareText

private fun ScholarQuote.stableId(): String = "quote:$scholarId:${title.ifBlank { english.take(40) }.hashCode()}"

@Composable
fun ScholarsScreen() {
    val context = LocalContext.current
    val quotes = remember { Catalogs.scholarQuotes }
    val scholars = remember { quotes.map { it.scholarId to it.author }.distinct() }
    val themes = remember { quotes.map { it.theme }.filter { it.isNotBlank() }.distinct().sorted() }
    var scholar by remember { mutableStateOf<String?>(null) }
    var theme by remember { mutableStateOf<String?>(null) }

    val filtered = remember(scholar, theme) {
        quotes.filter { (scholar == null || it.scholarId == scholar) && (theme == null || it.theme == theme) }
    }

    ScreenScaffold(title = "Scholars") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = scholar == null, onClick = { scholar = null }, label = { Text("All scholars") }) }
                    items(scholars, key = { it.first }) { (id, name) ->
                        FilterChip(selected = scholar == id, onClick = { scholar = if (scholar == id) null else id }, label = { Text(name) })
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = theme == null, onClick = { theme = null }, label = { Text("All themes") }) }
                    items(themes, key = { it }) { t ->
                        FilterChip(selected = theme == t, onClick = { theme = if (theme == t) null else t }, label = { Text(t) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { EmptyState("No quotes", "Try a different scholar or theme.") }
            }
            items(filtered, key = { it.stableId() }) { q ->
                QuoteCard(q, onShare = {
                    shareText(context, "“${q.english}”\n\n— ${q.author}${if (q.reference.isNotBlank()) ", ${q.reference}" else ""}")
                })
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: ScholarQuote, onShare: () -> Unit) {
    ContentCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (quote.title.isNotBlank()) Text(quote.title, fontWeight = FontWeight.SemiBold)
                MutedText(listOf(quote.author, quote.theme).filter { it.isNotBlank() }.joinToString(" · "))
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
            val id = quote.stableId()
            BookmarkButton(
                id = id,
                bookmark = {
                    Bookmark(id = id, kind = "Scholar", title = quote.title.ifBlank { quote.author }, subtitle = quote.author, body = quote.english)
                }
            )
        }
        Box(Modifier.padding(top = 6.dp)) {
            Text("“${quote.english}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
        if (quote.arabic.isNotBlank()) RtlText(quote.arabic, fontSize = 20, modifier = Modifier.padding(top = 8.dp))
        if (quote.urdu.isNotBlank()) RtlText(quote.urdu, fontSize = 18, modifier = Modifier.padding(top = 8.dp))
        if (quote.reference.isNotBlank()) MutedText(quote.reference, modifier = Modifier.padding(top = 8.dp))
    }
}
