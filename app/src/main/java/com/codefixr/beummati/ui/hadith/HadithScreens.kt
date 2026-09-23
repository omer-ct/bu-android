package com.codefixr.beummati.ui.hadith

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.HadithApi
import com.codefixr.beummati.data.HadithItem
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
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
fun HadithBooksScreen(onOpenBook: (String) -> Unit) {
    val books = remember { Catalogs.hadith.books }
    ScreenScaffold(title = "Hadith") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { it.slug }) { book ->
                ContentCard(onClick = { onOpenBook(book.slug) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(book.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val meta = buildList {
                                if (book.hadithCount > 0) add("${book.hadithCount} hadith")
                                if (book.chapters.isNotEmpty()) add("${book.chapters.size} books")
                                if (book.hasUrdu) add("Arabic · English · Urdu") else add("Arabic · English")
                            }.joinToString(" · ")
                            MutedText(meta)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item { MutedText("Texts via fawazahmed0/hadith-api (jsDelivr CDN).", modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
fun HadithChaptersScreen(slug: String, onBack: () -> Unit, onOpenChapter: (Int) -> Unit) {
    val book = remember(slug) { Catalogs.hadithBook(slug) }
    var query by remember { mutableStateOf("") }
    val chapters = remember(book, query) {
        val q = query.trim().lowercase()
        val all = book?.chapters.orEmpty()
        if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) || it.index.toString() == q }
    }

    ScreenScaffold(title = book?.name ?: "Hadith", onBack = onBack) { padding ->
        if (book == null || book.chapters.isEmpty()) {
            EmptyState("No chapters", "This collection has no chapter index yet.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search books") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(chapters, key = { it.index }) { ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChapter(ch.index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${ch.index}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, fontWeight = FontWeight.Medium)
                        if (ch.count > 0) MutedText("#${ch.first}–${ch.last} · ${ch.count} hadith")
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
fun HadithChapterScreen(slug: String, index: Int, onBack: () -> Unit) {
    val book = remember(slug) { Catalogs.hadithBook(slug) }
    val chapter = remember(book, index) { book?.chapters?.firstOrNull { it.index == index } }
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<HadithItem>>>(LoadState.Loading) }
    var noteFor by remember { mutableStateOf<HadithItem?>(null) }
    val bookName = book?.name ?: slug

    LaunchedEffect(slug, index, reload) {
        state = LoadState.Loading
        state = runCatching { HadithApi.chapter(slug, index) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }

    ScreenScaffold(title = chapter?.name ?: "$bookName · $index", onBack = onBack) { padding ->
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> {
                if (s.value.isEmpty()) {
                    EmptyState("No hadith found", "This section returned no text.", Modifier.padding(padding))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(s.value, key = { it.number }) { h ->
                            HadithCard(
                                item = h,
                                bookName = bookName,
                                route = Routes.hadithChapter(slug, index),
                                onNote = { noteFor = h }
                            )
                        }
                    }
                }
            }
        }
    }

    noteFor?.let { h ->
        NoteDialog(
            initialTitle = "$bookName ${h.number}",
            initialBody = "",
            onDismiss = { noteFor = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "hadith:${h.book}:${h.number}", route = Routes.hadithChapter(slug, index))
                noteFor = null
            }
        )
    }
}

@Composable
private fun HadithCard(
    item: HadithItem,
    bookName: String,
    route: String,
    onNote: () -> Unit
) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$bookName ${item.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.weight(1f))
            ShareMenuButton {
                ShareCard(
                    kind = "Hadith",
                    reference = "$bookName ${item.number}",
                    arabic = item.arabic,
                    english = item.english,
                    urdu = item.urdu
                )
            }
            IconButton(onClick = onNote) { Icon(Icons.Outlined.PostAdd, contentDescription = "Add note") }
            val id = "hadith:${item.book}:${item.number}"
            BookmarkButton(
                id = id,
                bookmark = {
                    Bookmark(
                        id = id,
                        kind = "Hadith",
                        title = "$bookName ${item.number}",
                        body = item.english.ifBlank { item.arabic },
                        route = route
                    )
                }
            )
        }
        if (item.arabic.isNotBlank()) RtlText(item.arabic, fontSize = 21, modifier = Modifier.padding(vertical = 6.dp))
        if (item.english.isNotBlank()) Text(item.english, style = MaterialTheme.typography.bodyMedium)
        if (item.urdu.isNotBlank()) RtlText(item.urdu, fontSize = 18, modifier = Modifier.padding(top = 8.dp))
    }
}
