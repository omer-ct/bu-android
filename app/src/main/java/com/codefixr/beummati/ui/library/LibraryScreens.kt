package com.codefixr.beummati.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.Dua
import com.codefixr.beummati.data.LibraryChapter
import com.codefixr.beummati.data.LibrarySeries
import com.codefixr.beummati.data.SahabaStory
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.SrtCueParser
import com.codefixr.beummati.ui.AutoDirectionText
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.shareText

private const val KIND_SAHABA = "sahaba"
private const val KIND_TAFSIR = "quranTafsir"

// region Library home

@Composable
fun LibraryScreen(navigate: (String) -> Unit, onOpenQuran: () -> Unit) {
    val lectures = remember { Catalogs.lectureSeries }
    val reading = remember { Catalogs.library.series.filterNot { it.id in Catalogs.audioSeriesIds } }

    fun open(series: LibrarySeries) = when (series.kind) {
        KIND_SAHABA -> navigate(Routes.SAHABA)
        KIND_TAFSIR -> onOpenQuran()
        else -> navigate(Routes.series(series.id))
    }

    ScreenScaffold(title = "Library") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionHeader("Lectures · audio + subtitles") }
            items(lectures, key = { it.id }) { s -> SeriesCard(s, Icons.Filled.Headphones) { open(s) } }

            item { SectionHeader("Reading") }
            items(reading, key = { it.id }) { s ->
                val icon = when (s.kind) {
                    KIND_SAHABA -> Icons.Outlined.Groups
                    KIND_TAFSIR -> Icons.AutoMirrored.Outlined.MenuBook
                    else -> Icons.Outlined.AutoStories
                }
                SeriesCard(s, icon) { open(s) }
            }

            item { SectionHeader("Duas") }
            item {
                val hisn = Catalogs.hisnAlMuslim
                ContentCard(onClick = { navigate(Routes.DUAS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hisn al-Muslim", fontWeight = FontWeight.SemiBold)
                            MutedText("Fortress of the Muslim · ${hisn.categories.size} categories")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(series: LibrarySeries, icon: ImageVector, onClick: () -> Unit) {
    ContentCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(series.title, fontWeight = FontWeight.SemiBold)
                series.subtitle?.takeIf { it.isNotBlank() }?.let { MutedText(it, maxLines = 2) }
                if (series.chapters.isNotEmpty()) {
                    Text(
                        "${series.chapters.size} chapters",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

// endregion

// region Series

@Composable
fun SeriesScreen(seriesId: String, onBack: () -> Unit, navigate: (String) -> Unit) {
    val found = remember(seriesId) { Catalogs.series(seriesId) }
    val completed by LecturePlayerSession.completed.collectAsState()
    val playerState by LecturePlayerSession.state.collectAsState()

    ScreenScaffold(
        title = found?.title ?: "Series",
        onBack = onBack,
        actions = {
            if (found != null) {
                BookmarkButton(
                    id = "series:${found.id}",
                    bookmark = {
                        Bookmark(
                            id = "series:${found.id}",
                            kind = "Series",
                            title = found.title,
                            subtitle = found.subtitle.orEmpty(),
                            route = Routes.series(found.id)
                        )
                    }
                )
            }
        }
    ) { padding ->
        if (found == null) {
            EmptyState("Series not found", seriesId, Modifier.padding(padding))
            return@ScreenScaffold
        }
        val series: LibrarySeries = found
        val hasAudio = series.id in Catalogs.audioSeriesIds
        val grouped = remember(series) { series.chapters.groupBy { it.volumeTitle } }

        fun openChapter(ch: LibraryChapter) {
            val track = Catalogs.track(series.id, ch.id)
            if (track != null) {
                LecturePlayerSession.play(series, ch, track)
                navigate(Routes.PLAYER)
            } else {
                navigate(Routes.chapter(series.id, ch.id))
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    series.subtitle?.let { MutedText(it) }
                    if (hasAudio) {
                        val done = series.chapters.count { "${series.id}/${it.id}" in completed }
                        Text(
                            "$done of ${series.chapters.size} completed",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        val firstUnfinished = series.chapters.firstOrNull {
                            "${series.id}/${it.id}" !in completed && Catalogs.hasAudio(series.id, it.id)
                        } ?: series.chapters.firstOrNull { Catalogs.hasAudio(series.id, it.id) }
                        if (firstUnfinished != null) {
                            Button(onClick = { openChapter(firstUnfinished) }, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (done == 0) "Start series" else "Continue · ${firstUnfinished.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            grouped.forEach { (volume, chapters) ->
                if (!volume.isNullOrBlank()) {
                    item(key = "vol-$volume") {
                        SectionHeader(volume, Modifier.padding(horizontal = 16.dp))
                    }
                }
                items(chapters, key = { it.id }) { ch ->
                    val nowPlaying = playerState.nowPlaying?.let { it.seriesId == series.id && it.chapterId == ch.id } == true
                    ChapterRow(
                        series = series,
                        chapter = ch,
                        isCompleted = "${series.id}/${ch.id}" in completed,
                        isNowPlaying = nowPlaying,
                        onClick = { openChapter(ch) },
                        onRead = { navigate(Routes.chapter(series.id, ch.id)) }
                    )
                }
            }
            series.attribution?.takeIf { it.isNotBlank() }?.let {
                item { MutedText(it, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    series: LibrarySeries,
    chapter: LibraryChapter,
    isCompleted: Boolean,
    isNowPlaying: Boolean,
    onClick: () -> Unit,
    onRead: () -> Unit
) {
    val hasAudio = remember(chapter) { Catalogs.hasAudio(series.id, chapter.id) }
    val hasText = remember(chapter) { Catalogs.hasChapterText(series.id, chapter.id) }
    val hasSubs = remember(chapter) {
        SrtCueParser.bundleContains(chapter.srtEnglish) || SrtCueParser.bundleContains(chapter.srtUrdu) ||
            SrtCueParser.bundleContains(Catalogs.track(series.id, chapter.id)?.srtFile)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasAudio || hasText, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                isNowPlaying -> Icon(Icons.Filled.PlayCircleFilled, contentDescription = "Now playing", tint = MaterialTheme.colorScheme.primary)
                isCompleted -> Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                else -> Text(
                    chapter.index?.toString() ?: "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(chapter.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val tags = buildList {
                if (hasAudio) add("Audio")
                if (hasSubs) add("Subtitles")
                if (hasText) add("Transcript")
                if (!hasAudio && !hasText) add("Not available yet")
            }.joinToString(" · ")
            MutedText(tags, maxLines = 1)
        }
        if (hasText && hasAudio) {
            IconButton(onClick = onRead) { Icon(Icons.Outlined.Description, contentDescription = "Read transcript") }
        }
        if (hasAudio) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else if (hasText) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

// endregion

// region Chapter reader

private enum class ReadLang(val label: String) { ENGLISH("English"), URDU("Urdu"), ARABIC("Arabic") }

@Composable
fun ChapterReaderScreen(seriesId: String, chapterId: String, onBack: () -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val series = remember(seriesId) { Catalogs.series(seriesId) }
    val chapter = remember(seriesId, chapterId) { Catalogs.chapter(seriesId, chapterId) }
    val text = remember(seriesId, chapterId) { Catalogs.chapterText(seriesId, chapterId) }
    val track = remember(seriesId, chapterId) { Catalogs.track(seriesId, chapterId) }
    var showNote by remember { mutableStateOf(false) }

    val available = remember(text) {
        buildList {
            if (!text?.english.isNullOrBlank()) add(ReadLang.ENGLISH)
            if (!text?.urdu.isNullOrBlank()) add(ReadLang.URDU)
            if (!text?.arabic.isNullOrBlank()) add(ReadLang.ARABIC)
        }
    }
    val preferred = if (series?.primaryLanguage == "urdu" && ReadLang.URDU in available) ReadLang.URDU else available.firstOrNull()
    var lang by remember(available) { mutableStateOf(preferred) }

    val title = text?.title?.takeIf { it.isNotBlank() } ?: chapter?.title ?: "Chapter"
    val body = when (lang) {
        ReadLang.ENGLISH -> text?.english
        ReadLang.URDU -> text?.urdu
        ReadLang.ARABIC -> text?.arabic
        null -> null
    }.orEmpty()
    val paragraphs = remember(body) { body.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() } }
    val rtl = lang == ReadLang.URDU || lang == ReadLang.ARABIC

    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { showNote = true }) { Icon(Icons.Outlined.PostAdd, contentDescription = "Add note") }
            if (body.isNotBlank()) {
                IconButton(onClick = { shareText(context, "$title\n\n${body.take(1500)}") }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share")
                }
            }
            BookmarkButton(
                id = "chapter:$seriesId/$chapterId",
                bookmark = {
                    Bookmark(
                        id = "chapter:$seriesId/$chapterId",
                        kind = "Library",
                        title = title,
                        subtitle = series?.title.orEmpty(),
                        route = Routes.chapter(seriesId, chapterId)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                series?.title?.let { MutedText(it) }
                if (track != null && series != null && chapter != null) {
                    Button(
                        onClick = {
                            LecturePlayerSession.play(series, chapter, track)
                            navigate(Routes.PLAYER)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.Headphones, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Listen with subtitles")
                    }
                }
                if (available.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        available.forEach { l ->
                            FilterChip(selected = lang == l, onClick = { lang = l }, label = { Text(l.label) })
                        }
                    }
                }
            }
            if (text == null || paragraphs.isEmpty()) {
                item {
                    EmptyState(
                        "No transcript yet",
                        if (track != null) "Audio is available — tap Listen above." else "Text for this chapter hasn’t been bundled yet."
                    )
                }
            } else {
                items(paragraphs.size) { i ->
                    if (rtl) RtlText(paragraphs[i], fontSize = 19) else Text(paragraphs[i], style = MaterialTheme.typography.bodyLarge)
                }
            }
            text?.reference?.takeIf { it.isNotBlank() }?.let { ref ->
                item { MutedText(ref, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }

    if (showNote) {
        NoteDialog(
            initialTitle = title,
            initialBody = "",
            onDismiss = { showNote = false },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "library:$seriesId/$chapterId", route = Routes.chapter(seriesId, chapterId))
                showNote = false
            }
        )
    }
}

// endregion

// region Sahaba

@Composable
fun SahabaScreen(onBack: () -> Unit) {
    val stories = remember { Catalogs.sahabaStories }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showUrdu by remember { mutableStateOf(false) }
    val series = remember { Catalogs.library.series.firstOrNull { it.kind == KIND_SAHABA } }

    ScreenScaffold(title = series?.title ?: "Sahaba Stories", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showUrdu, onClick = { showUrdu = false }, label = { Text("English") })
                    FilterChip(selected = showUrdu, onClick = { showUrdu = true }, label = { Text("Urdu") })
                }
            }
            items(stories, key = { it.id }) { story ->
                SahabaCard(story, expanded = expanded == story.id, urdu = showUrdu) {
                    expanded = if (expanded == story.id) null else story.id
                }
            }
        }
    }
}

@Composable
private fun SahabaCard(story: SahabaStory, expanded: Boolean, urdu: Boolean, onToggle: () -> Unit) {
    ContentCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(story.title, fontWeight = FontWeight.SemiBold)
                MutedText(listOf(story.name, story.theme).filter { it.isNotBlank() }.joinToString(" · "))
            }
            BookmarkButton(
                id = "sahaba:${story.id}",
                bookmark = {
                    Bookmark(
                        id = "sahaba:${story.id}",
                        kind = "Sahaba",
                        title = story.title,
                        subtitle = story.name,
                        body = story.english,
                        route = Routes.SAHABA
                    )
                }
            )
        }
        val body = if (urdu && story.urdu.isNotBlank()) story.urdu else story.english
        if (expanded) {
            Spacer(Modifier.padding(top = 6.dp))
            AutoDirectionText(body)
            if (story.reference.isNotBlank()) MutedText(story.reference, modifier = Modifier.padding(top = 8.dp))
        } else {
            MutedText(body, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// endregion

// region Hisn al-Muslim

@Composable
fun DuasScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val hisn = remember { Catalogs.hisnAlMuslim }
    var query by remember { mutableStateOf("") }
    val categories = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) hisn.categories else hisn.categories.filter {
            it.titleEn.lowercase().contains(q) || it.titleAr.contains(query.trim()) ||
                it.duas.any { d -> d.english.lowercase().contains(q) }
        }
    }

    ScreenScaffold(title = "Hisn al-Muslim", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search duas") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (hisn.shortcuts.isNotEmpty() && query.isBlank()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hisn.shortcuts, key = { it.id }) { sc ->
                            FilterChip(
                                selected = false,
                                onClick = { sc.categoryIds.firstOrNull()?.let { navigate(Routes.duaCategory(it)) } },
                                label = { Text(sc.title) }
                            )
                        }
                    }
                }
            }
            items(categories, key = { it.id }) { cat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { navigate(Routes.duaCategory(cat.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${cat.id}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(36.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(cat.titleEn, fontWeight = FontWeight.Medium)
                        MutedText("${cat.duas.size} duas", maxLines = 1)
                    }
                    if (cat.titleAr.isNotBlank()) {
                        Text(cat.titleAr, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
            hisn.source?.let { item { MutedText(it, modifier = Modifier.padding(16.dp)) } }
        }
    }
}

@Composable
fun DuaCategoryScreen(id: Int, onBack: () -> Unit) {
    val category = remember(id) { Catalogs.duaCategory(id) }
    val context = LocalContext.current
    ScreenScaffold(title = category?.titleEn ?: "Duas", onBack = onBack) { padding ->
        if (category == null) {
            EmptyState("Not found", "Category $id", Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (category.titleAr.isNotBlank()) item { RtlText(category.titleAr, fontSize = 20) }
            items(category.duas, key = { it.id }) { dua ->
                DuaCard(dua, category.titleEn, onShare = {
                    shareText(context, "${dua.arabic}\n\n${dua.transliteration}\n\n${dua.english}\n\n— ${dua.reference}")
                })
            }
        }
    }
}

@Composable
private fun DuaCard(dua: Dua, categoryTitle: String, onShare: () -> Unit) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dua.count > 1) {
                Text("×${dua.count}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
            BookmarkButton(
                id = "dua:${dua.id}",
                bookmark = {
                    Bookmark(
                        id = "dua:${dua.id}",
                        kind = "Dua",
                        title = categoryTitle,
                        body = dua.english,
                        route = Routes.duaCategory(dua.categoryId)
                    )
                }
            )
        }
        if (dua.arabic.isNotBlank()) RtlText(dua.arabic, fontSize = 22, modifier = Modifier.padding(vertical = 6.dp))
        if (dua.transliteration.isNotBlank()) {
            Text(
                dua.transliteration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        if (dua.english.isNotBlank()) Text(dua.english, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        if (dua.reference.isNotBlank()) MutedText(dua.reference, modifier = Modifier.padding(top = 6.dp))
    }
}

// endregion
