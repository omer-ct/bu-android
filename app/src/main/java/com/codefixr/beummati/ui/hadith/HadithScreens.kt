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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.ContentBrowseMode
import com.codefixr.beummati.data.HadithApi
import com.codefixr.beummati.data.HadithItem
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.BindImmersiveReading
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.BrowseModeBar
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.EnglishScriptText
import com.codefixr.beummati.ui.ErrorBox
import com.codefixr.beummati.ui.ImmersiveReading
import com.codefixr.beummati.ui.LoadState
import com.codefixr.beummati.ui.LoadingBox
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton
import com.codefixr.beummati.ui.SwipeItemPager
import com.codefixr.beummati.ui.UrduScriptText
import kotlinx.coroutines.launch

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
                            if (book.nameUrdu.isNotBlank()) {
                                RtlText(book.nameUrdu, fontSize = 17, urduFace = true, modifier = Modifier.padding(top = 2.dp))
                            }
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
fun HadithChaptersScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenChapter: (index: Int, hadithNumber: Int?) -> Unit
) {
    val book = remember(slug) { Catalogs.hadithBook(slug) }
    var query by remember { mutableStateOf("") }
    var showJump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf<String?>(null) }
    val maxHadith = book?.maxHadithNumber ?: 0
    val chapters = remember(book, query) {
        val q = query.trim()
        val all = book?.chapters.orEmpty()
        if (q.isEmpty()) {
            all
        } else {
            all.filter { ch ->
                ch.index.toString() == q ||
                    ch.name.contains(q, ignoreCase = true) ||
                    ch.nameUrdu.contains(q, ignoreCase = true)
            }
        }
    }

    fun goToHadith(raw: String) {
        val n = raw.toIntOrNull()
        if (n == null || book == null) {
            jumpError = "Enter a hadith number"
            return
        }
        val ch = book.chapterForHadith(n)
        if (ch == null) {
            jumpError = if (maxHadith > 0) "Not in this book (1–$maxHadith)" else "Not found in this book"
            return
        }
        showJump = false
        jumpError = null
        onOpenChapter(ch.index, n)
    }

    ScreenScaffold(
        title = book?.name ?: "Hadith",
        onBack = onBack,
        actions = {
            if (maxHadith > 0) {
                IconButton(
                    onClick = {
                        jumpText = ""
                        jumpError = null
                        showJump = true
                    }
                ) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Jump to hadith #")
                }
            }
        }
    ) { padding ->
        if (book == null || book.chapters.isEmpty()) {
            EmptyState("No chapters", "This collection has no chapter index yet.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search books (EN / UR)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(chapters, key = { it.index }) { ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChapter(ch.index, null) }
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
                        if (ch.nameUrdu.isNotBlank()) {
                            RtlText(ch.nameUrdu, fontSize = 18, urduFace = true)
                            Text(
                                ch.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(ch.name, fontWeight = FontWeight.Medium)
                        }
                        if (ch.count > 0) MutedText("#${ch.first}–${ch.last} · ${ch.count} hadith")
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }

    if (showJump && maxHadith > 0) {
        JumpToHadithDialog(
            title = "Go to hadith #",
            hint = "Jump in ${book?.name ?: "this book"} (1–$maxHadith)",
            text = jumpText,
            error = jumpError,
            onTextChange = {
                jumpText = it.filter { ch -> ch.isDigit() }.take(5)
                jumpError = null
            },
            onDismiss = { showJump = false },
            onGo = { goToHadith(jumpText) }
        )
    }
}

@Composable
fun HadithChapterScreen(
    slug: String,
    index: Int,
    onBack: () -> Unit,
    initialHadith: Int? = null,
    onOpenHadith: (chapterIndex: Int, hadithNumber: Int) -> Unit = { _, _ -> }
) {
    val book = remember(slug) { Catalogs.hadithBook(slug) }
    val chapter = remember(book, index) { book?.chapters?.firstOrNull { it.index == index } }
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<HadithItem>>>(LoadState.Loading) }
    var noteFor by remember { mutableStateOf<HadithItem?>(null) }
    var showJump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf<String?>(null) }
    var didJumpToInitial by remember(slug, index, initialHadith) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val browseMode by SettingsStore.contentBrowseMode.collectAsState()
    val immersive by ImmersiveReading.active.collectAsState()
    BindImmersiveReading()
    val bookName = book?.name ?: slug
    val loadedCount = (state as? LoadState.Loaded)?.value?.size ?: 0
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { loadedCount.coerceAtLeast(1) }
    )

    LaunchedEffect(slug, index, reload) {
        state = LoadState.Loading
        state = runCatching { HadithApi.chapter(slug, index) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }

    fun indexOfHadith(items: List<HadithItem>, number: Int): Int {
        val exact = items.indexOfFirst { it.number == number.toString() }
        if (exact >= 0) return exact
        return items.indexOfFirst { it.number.toDoubleOrNull()?.toInt() == number }
    }

    fun scrollToHadith(items: List<HadithItem>, number: Int): Boolean {
        val i = indexOfHadith(items, number)
        if (i < 0) return false
        scope.launch {
            if (browseMode == ContentBrowseMode.SLIDE) {
                pagerState.animateScrollToPage(i)
            } else {
                listState.animateScrollToItem(i)
            }
        }
        return true
    }

    LaunchedEffect(state, initialHadith, browseMode) {
        val target = initialHadith ?: return@LaunchedEffect
        if (didJumpToInitial) return@LaunchedEffect
        val items = (state as? LoadState.Loaded)?.value ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        didJumpToInitial = true
        scrollToHadith(items, target)
    }

    val chapterTitle = when {
        !chapter?.nameUrdu.isNullOrBlank() -> chapter!!.nameUrdu
        !chapter?.name.isNullOrBlank() -> chapter!!.name
        else -> "$bookName · $index"
    }
    val loaded = (state as? LoadState.Loaded)?.value.orEmpty()
    val rangeLabel = when {
        chapter != null && chapter.first > 0 && chapter.last >= chapter.first ->
            "${chapter.first}–${chapter.last}"
        loaded.isNotEmpty() -> {
            val nums = loaded.mapNotNull { it.number.toIntOrNull() }
            if (nums.isNotEmpty()) "${nums.min()}–${nums.max()}" else null
        }
        else -> null
    }

    ScreenScaffold(
        title = chapterTitle,
        onBack = onBack,
        hideTopBar = immersive,
        actions = {
            if (loaded.isNotEmpty() || (chapter?.count ?: 0) > 0) {
                IconButton(
                    onClick = {
                        jumpText = ""
                        jumpError = null
                        showJump = true
                    }
                ) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Jump to hadith #")
                }
            }
            IconButton(onClick = { ImmersiveReading.set(true) }) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "Full screen")
            }
        }
    ) { padding ->
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> {
                if (s.value.isEmpty()) {
                    EmptyState("No hadith found", "This section returned no text.", Modifier.padding(padding))
                } else {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (!immersive) {
                            BrowseModeBar(
                                mode = browseMode,
                                onMode = { SettingsStore.setContentBrowseMode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        when (browseMode) {
                            ContentBrowseMode.SLIDE -> SwipeItemPager(
                                items = s.value,
                                pagerState = pagerState,
                                key = { it.number },
                                label = { _, h -> "$bookName ${h.number}" },
                                showChrome = !immersive,
                                modifier = Modifier.weight(1f)
                            ) { h, _ ->
                                HadithCard(
                                    item = h,
                                    bookName = bookName,
                                    route = Routes.hadithChapter(slug, index, h.number.toIntOrNull()),
                                    compact = immersive,
                                    onTap = { ImmersiveReading.toggle() },
                                    onNote = { noteFor = h }
                                )
                            }
                            ContentBrowseMode.LIST -> LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(s.value, key = { it.number }) { h ->
                                    HadithCard(
                                        item = h,
                                        bookName = bookName,
                                        route = Routes.hadithChapter(slug, index, h.number.toIntOrNull()),
                                        compact = immersive,
                                        onTap = { ImmersiveReading.toggle() },
                                        onNote = { noteFor = h }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJump) {
        JumpToHadithDialog(
            title = "Go to hadith #",
            hint = buildString {
                append("Jump in this kitāb")
                if (rangeLabel != null) append(" ($rangeLabel)")
            },
            text = jumpText,
            error = jumpError,
            onTextChange = {
                jumpText = it.filter { ch -> ch.isDigit() }.take(5)
                jumpError = null
            },
            onDismiss = { showJump = false },
            onGo = {
                val n = jumpText.toIntOrNull()
                if (n == null) {
                    jumpError = "Enter a hadith number"
                    return@JumpToHadithDialog
                }
                val elsewhere = book?.chapterForHadith(n)
                if (elsewhere != null && elsewhere.index != index) {
                    showJump = false
                    onOpenHadith(elsewhere.index, n)
                    return@JumpToHadithDialog
                }
                if (loaded.isEmpty() || !scrollToHadith(loaded, n)) {
                    jumpError = if (rangeLabel != null) "Not in this section ($rangeLabel)" else "Not in this section"
                    return@JumpToHadithDialog
                }
                showJump = false
            }
        )
    }

    noteFor?.let { h ->
        NoteDialog(
            initialTitle = "$bookName ${h.number}",
            initialBody = "",
            onDismiss = { noteFor = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "hadith:${h.book}:${h.number}", route = Routes.hadithChapter(slug, index, h.number.toIntOrNull()))
                noteFor = null
            }
        )
    }
}

@Composable
private fun JumpToHadithDialog(
    title: String,
    hint: String,
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGo: () -> Unit
) {
    val canGo = text.toIntOrNull() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MutedText(hint)
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Hadith number") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (canGo) onGo() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGo, enabled = canGo) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HadithCard(
    item: HadithItem,
    bookName: String,
    route: String,
    compact: Boolean = false,
    onTap: () -> Unit = {},
    onNote: () -> Unit
) {
    val primary = item.primaryGrade
    ContentCard(onClick = onTap) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$bookName ${item.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (primary != null) {
                HadithGradeChip(primary, Modifier.padding(start = 8.dp))
            }
            Box(Modifier.weight(1f))
            if (!compact) {
                ShareMenuButton {
                    ShareCard(
                        kind = "Hadith",
                        reference = buildString {
                            append("$bookName ${item.number}")
                            if (primary != null) append(" · $primary")
                        },
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
        }
        if (item.grades.isNotEmpty()) {
            MutedText(
                item.grades.joinToString(" · ") { g ->
                    if (g.scholar.isBlank()) g.grade else "${g.scholar}: ${g.grade}"
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (item.arabic.isNotBlank()) ArabicScriptText(item.arabic, Modifier.padding(vertical = 6.dp))
        if (item.english.isNotBlank()) EnglishScriptText(item.english, Modifier.padding(top = 4.dp))
        if (item.urdu.isNotBlank()) UrduScriptText(item.urdu, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun HadithGradeChip(grade: String, modifier: Modifier = Modifier) {
    val kind = remember(grade) { gradeKind(grade) }
    val (bg, fg) = when (kind) {
        GradeKind.Sahih -> Color(0xFF1B5E20).copy(alpha = 0.14f) to Color(0xFF1B5E20)
        GradeKind.Hasan -> Color(0xFF0D47A1).copy(alpha = 0.14f) to Color(0xFF0D47A1)
        GradeKind.Daif -> Color(0xFFB71C1C).copy(alpha = 0.14f) to Color(0xFFB71C1C)
        GradeKind.Other -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val urdu = remember(grade, kind) { gradeUrdu(grade, kind) }
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = if (urdu != null) "$grade · $urdu" else grade,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private enum class GradeKind { Sahih, Hasan, Daif, Other }

private fun gradeKind(grade: String): GradeKind {
    val g = grade.lowercase()
        .replace('ʿ', '\'')
        .replace('ā', 'a')
        .replace('ī', 'i')
        .replace('ū', 'u')
    return when {
        "mawdu" in g || "fabricat" in g || "موضوع" in grade -> GradeKind.Daif
        "da'if" in g || "daif" in g || "weak" in g || "ضعیف" in grade -> GradeKind.Daif
        "hasan" in g || "حسن" in grade -> GradeKind.Hasan
        "sahih" in g || "saheeh" in g || "صحيح" in grade || "صحیح" in grade -> GradeKind.Sahih
        else -> GradeKind.Other
    }
}

private fun gradeUrdu(grade: String, kind: GradeKind): String? {
    if (grade.any { it in '\u0600'..'\u06FF' }) return null
    return when (kind) {
        GradeKind.Sahih -> "صحیح"
        GradeKind.Hasan -> "حسن"
        GradeKind.Daif -> "ضعیف"
        GradeKind.Other -> null
    }
}
