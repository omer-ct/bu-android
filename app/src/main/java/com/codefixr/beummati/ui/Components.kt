@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.player.SrtCueParser

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

/**
 * Screen chrome shared by every tab. Window insets are zeroed because the root scaffold
 * already reserves space for the mini player + navigation bar.
 *
 * @param hideTopBar when true (immersive reading), the TopAppBar is omitted so content
 * can use the full height. Pair with [ImmersiveReading] + root bottom-bar hide.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    hideTopBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions
                )
            }
        },
        floatingActionButton = floatingActionButton,
        content = { padding ->
            if (hideTopBar) {
                Box(Modifier.statusBarsPadding()) {
                    content(padding)
                }
            } else {
                content(padding)
            }
        }
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn’t load", style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        maxLines = maxLines,
        modifier = modifier
    )
}

/**
 * Multiplier applied to Arabic / Urdu type, driven by the reading settings so every
 * script surface scales together.
 */
@Composable
fun arabicScale(): Float {
    val size by SettingsStore.arabicFontSize.collectAsState()
    return size / SettingsStore.DEFAULT_ARABIC_SIZE
}

/** Arabic / Urdu body text — uses Reading settings Arabic face + size scaling. */
@Composable
fun RtlText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 22,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** When true, prefer the Urdu face from Reading settings (Nastaliq etc.). */
    urduFace: Boolean = false
) {
    val arabicFont by SettingsStore.arabicFont.collectAsState()
    val urduFont by SettingsStore.urduFont.collectAsState()
    val font = if (urduFace) urduFont else arabicFont
    val scaled = (fontSize * arabicScale()).coerceIn(12f, 56f)
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = fontFamilyFor(font),
            fontSize = scaled.sp,
            lineHeight = (scaled * 1.7f).sp,
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Right,
            color = color
        )
    )
}

/** Picks script-aware body text depending on direction. */
@Composable
fun AutoDirectionText(text: String, modifier: Modifier = Modifier, fontSize: Int = 16) {
    if (SrtCueParser.isPrimarilyRtl(text)) {
        RtlText(text, modifier, fontSize = fontSize + 3)
    } else {
        val font by SettingsStore.englishFont.collectAsState()
        val color by SettingsStore.englishColor.collectAsState()
        Text(
            text,
            modifier = modifier,
            style = TextStyle(
                fontFamily = fontFamilyFor(font),
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.5f).sp,
                color = scriptColor(color)
            )
        )
    }
}

@Composable
fun BookmarkButton(bookmark: () -> Bookmark, id: String) {
    val all by SavedStore.bookmarks.collectAsState()
    val saved = all.any { it.id == id }
    IconButton(onClick = { SavedStore.toggleBookmark(bookmark()) }) {
        Icon(
            if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (saved) "Remove bookmark" else "Bookmark",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/** Title + body editor used for new and existing notes. */
@Composable
fun NoteDialog(
    initialTitle: String,
    initialBody: String,
    heading: String = "Note",
    onDismiss: () -> Unit,
    onSave: (title: String, body: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Note") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body) }, enabled = title.isNotBlank() || body.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

/**
 * Share action — opens the studio (languages, edit text, designs) for Quran / Hadith / Duas / etc.
 */
@Composable
fun ShareMenuButton(card: () -> ShareCard) {
    var studio by remember { mutableStateOf<ShareCard?>(null) }
    IconButton(onClick = { studio = card() }) {
        Icon(Icons.Outlined.Share, contentDescription = "Share")
    }
    studio?.let { pending ->
        ShareStudioSheet(card = pending, onDismiss = { studio = null })
    }
}
