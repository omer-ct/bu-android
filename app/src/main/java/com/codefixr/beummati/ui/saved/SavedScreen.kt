package com.codefixr.beummati.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.codefixr.beummati.data.Note
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.ScreenScaffold
import java.text.DateFormat
import java.util.Date

/** Lecture moment refs look like `seriesId/chapterId@seconds`. */
private val lectureRef = Regex("^([^/:]+)/([^@]+)@(\\d+)$")

private sealed interface NoteEdit {
    data object New : NoteEdit
    data class Existing(val note: Note) : NoteEdit
}

@Composable
fun SavedScreen(navigate: (String) -> Unit) {
    val bookmarks by SavedStore.bookmarks.collectAsState()
    val notes by SavedStore.notes.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<NoteEdit?>(null) }

    ScreenScaffold(
        title = "Saved",
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { editing = NoteEdit.New }) {
                    Icon(Icons.Filled.Add, contentDescription = "New note")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks (${bookmarks.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes (${notes.size})") })
            }
            when (tab) {
                0 -> BookmarksList(bookmarks, navigate)
                else -> NotesList(
                    notes = notes,
                    navigate = navigate,
                    onEdit = { editing = NoteEdit.Existing(it) }
                )
            }
        }
    }

    when (val e = editing) {
        null -> Unit
        NoteEdit.New -> NoteDialog(
            initialTitle = "",
            initialBody = "",
            heading = "New note",
            onDismiss = { editing = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b)
                editing = null
            }
        )
        is NoteEdit.Existing -> NoteDialog(
            initialTitle = e.note.title,
            initialBody = e.note.body,
            heading = "Edit note",
            onDismiss = { editing = null },
            onSave = { t, b ->
                SavedStore.updateNote(e.note.id, t, b)
                editing = null
            }
        )
    }
}

@Composable
private fun BookmarksList(bookmarks: List<Bookmark>, navigate: (String) -> Unit) {
    if (bookmarks.isEmpty()) {
        EmptyState("No bookmarks yet", "Tap the bookmark icon on an ayah, hadith, dua, quote or chapter.")
        return
    }
    val grouped = remember(bookmarks) { bookmarks.groupBy { it.kind } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        grouped.forEach { (kind, list) ->
            item(key = "h-$kind") {
                Text(kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(list, key = { it.id }) { b ->
                val route = b.route
                ContentCard(onClick = if (route != null) ({ navigate(route) }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.SemiBold)
                            if (b.subtitle.isNotBlank()) MutedText(b.subtitle, maxLines = 1)
                        }
                        IconButton(onClick = { SavedStore.removeBookmark(b.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                        }
                    }
                    if (b.body.isNotBlank()) MutedText(b.body, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesList(notes: List<Note>, navigate: (String) -> Unit, onEdit: (Note) -> Unit) {
    if (notes.isEmpty()) {
        EmptyState("No notes yet", "Add notes from the Qur’an, Hadith, Library or the lecture player — or tap + to write one.")
        return
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(notes, key = { it.id }) { note ->
            val moment = lectureRef.find(note.ref)
            val route = note.route
            ContentCard(onClick = if (route != null) ({ navigate(route) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(note.title, fontWeight = FontWeight.SemiBold)
                        MutedText(dateFormat.format(Date(note.updatedAt)), maxLines = 1)
                    }
                    if (moment != null) {
                        IconButton(onClick = {
                            val (series, chapter, seconds) = moment.destructured
                            if (LecturePlayerSession.playChapter(series, chapter, seconds.toDouble())) {
                                navigate(Routes.PLAYER)
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play from this moment")
                        }
                    }
                    IconButton(onClick = { onEdit(note) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { SavedStore.deleteNote(note.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                }
                if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
