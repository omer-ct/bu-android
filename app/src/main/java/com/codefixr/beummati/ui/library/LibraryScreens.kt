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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.ContentBrowseMode
import com.codefixr.beummati.data.Dua
import com.codefixr.beummati.data.LibraryChapter
import com.codefixr.beummati.data.LibraryProgressStore
import com.codefixr.beummati.data.LibrarySeries
import com.codefixr.beummati.data.SahabaStory
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.SrtCueParser
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.AutoDirectionText
import com.codefixr.beummati.ui.BookmarkButton
import com.codefixr.beummati.ui.BrowseModeBar
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EmptyState
import com.codefixr.beummati.ui.EnglishScriptText
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton
import com.codefixr.beummati.ui.SwipeItemPager
import com.codefixr.beummati.ui.UrduScriptText

private const val KIND_SAHABA = "sahaba"
private const val KIND_PROPHETS = "prophets"
private const val KIND_TAFSIR = "quranTafsir"

// region Library home

@Composable
fun LibraryScreen(navigate: (String) -> Unit, onOpenQuran: () -> Unit) {
    val lectures = remember { Catalogs.lectureSeries }
    val reading = remember { Catalogs.library.series.filterNot { it.id in Catalogs.audioSeriesIds } }

    fun open(series: LibrarySeries) = when (series.kind) {
        KIND_SAHABA -> navigate(Routes.SAHABA)
        KIND_PROPHETS -> navigate(Routes.PROPHETS)
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
                    KIND_PROPHETS -> Icons.Outlined.AutoStories
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
    val completedBySeries by LibraryProgressStore.completed.collectAsState()
    val completed = completedBySeries[seriesId].orEmpty()
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
                    if (series.chapters.isNotEmpty()) {
                        val total = series.chapters.size
                        val reached = LibraryProgressStore.progressCount(series.id, total)
                        Text(
                            "$reached of $total · ${completed.size} completed",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        LinearProgressIndicator(
                            progress = { reached.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                        val resume = LibraryProgressStore.resumeChapter(series)
                        if (resume != null) {
                            val playable = Catalogs.hasAudio(series.id, resume.id)
                            Button(onClick = { openChapter(resume) }, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(
                                    if (playable) Icons.Filled.PlayArrow else Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (completed.isEmpty() && LibraryProgressStore.last(series.id) == null) {
                                        "Start series"
                                    } else {
                                        "Continue · ${resume.title}"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                        isCompleted = ch.id in completed,
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
    val series = remember(seriesId) { Catalogs.series(seriesId) }
    val chapter = remember(seriesId, chapterId) { Catalogs.chapter(seriesId, chapterId) }
    val text = remember(seriesId, chapterId) { Catalogs.chapterText(seriesId, chapterId) }
    val track = remember(seriesId, chapterId) { Catalogs.track(seriesId, chapterId) }
    var showNote by remember { mutableStateOf(false) }
    val completedBySeries by LibraryProgressStore.completed.collectAsState()
    val isCompleted = completedBySeries[seriesId]?.contains(chapterId) == true

    // Opening a chapter is what "continue reading" resumes to.
    LaunchedEffect(seriesId, chapterId) { LibraryProgressStore.mark(seriesId, chapterId) }

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
                ShareMenuButton {
                    // Readers can be book-length, so share the opening passage rather than the lot.
                    ShareCard(
                        title = title,
                        kind = series?.title.orEmpty(),
                        reference = text?.reference.orEmpty(),
                        arabic = if (lang == ReadLang.ARABIC) body.take(900) else "",
                        english = if (lang == ReadLang.ENGLISH) body.take(900) else "",
                        urdu = if (lang == ReadLang.URDU) body.take(900) else ""
                    )
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
                    if (rtl) RtlText(paragraphs[i], fontSize = 19, urduFace = true)
                    else EnglishScriptText(paragraphs[i])
                }
            }
            text?.reference?.takeIf { it.isNotBlank() }?.let { ref ->
                item { MutedText(ref, modifier = Modifier.padding(top = 8.dp)) }
            }
            item {
                TextButton(
                    onClick = { LibraryProgressStore.toggleCompleted(seriesId, chapterId) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isCompleted) "Marked complete" else "Mark chapter complete")
                }
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
    StorySeriesScreen(
        onBack = onBack,
        stories = Catalogs.sahabaStories,
        kindKey = KIND_SAHABA,
        fallbackTitle = "Sahaba Stories",
        bookmarkKind = "Sahaba",
        route = Routes.SAHABA
    )
}

@Composable
fun ProphetsScreen(onBack: () -> Unit) {
    StorySeriesScreen(
        onBack = onBack,
        stories = Catalogs.prophetsStories,
        kindKey = KIND_PROPHETS,
        fallbackTitle = "Stories of the Prophets",
        bookmarkKind = "Prophets",
        route = Routes.PROPHETS
    )
}

@Composable
private fun StorySeriesScreen(
    onBack: () -> Unit,
    stories: List<SahabaStory>,
    kindKey: String,
    fallbackTitle: String,
    bookmarkKind: String,
    route: String
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var showUrdu by remember { mutableStateOf(false) }
    val series = remember(kindKey) { Catalogs.library.series.firstOrNull { it.kind == kindKey } }
    val browseMode by SettingsStore.contentBrowseMode.collectAsState()
    val pagerState = rememberPagerState(pageCount = { stories.size.coerceAtLeast(1) })

    ScreenScaffold(title = series?.title ?: fallbackTitle, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrowseModeBar(
                mode = browseMode,
                onMode = { SettingsStore.setContentBrowseMode(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !showUrdu, onClick = { showUrdu = false }, label = { Text("English") })
                FilterChip(selected = showUrdu, onClick = { showUrdu = true }, label = { Text("Urdu") })
            }
            when (browseMode) {
                ContentBrowseMode.SLIDE -> SwipeItemPager(
                    items = stories,
                    pagerState = pagerState,
                    key = { it.id },
                    label = { i, s -> "${i + 1} / ${stories.size} · ${s.name}" },
                    modifier = Modifier.weight(1f)
                ) { story, _ ->
                    StoryCard(
                        story = story,
                        expanded = true,
                        urdu = showUrdu,
                        bookmarkKind = bookmarkKind,
                        route = route,
                        onToggle = {}
                    )
                }
                ContentBrowseMode.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(stories, key = { it.id }) { story ->
                        StoryCard(
                            story = story,
                            expanded = expanded == story.id,
                            urdu = showUrdu,
                            bookmarkKind = bookmarkKind,
                            route = route
                        ) {
                            expanded = if (expanded == story.id) null else story.id
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    story: SahabaStory,
    expanded: Boolean,
    urdu: Boolean,
    bookmarkKind: String,
    route: String,
    onToggle: () -> Unit
) {
    ContentCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(story.title, fontWeight = FontWeight.SemiBold)
                MutedText(listOf(story.name, story.theme).filter { it.isNotBlank() }.joinToString(" · "))
            }
            BookmarkButton(
                id = "${bookmarkKind.lowercase()}:${story.id}",
                bookmark = {
                    Bookmark(
                        id = "${bookmarkKind.lowercase()}:${story.id}",
                        kind = bookmarkKind,
                        title = story.title,
                        subtitle = story.name,
                        body = story.english,
                        route = route
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
            it.titleEn.lowercase().contains(q) ||
                it.titleUr.contains(query.trim()) ||
                it.titleAr.contains(query.trim()) ||
                it.duas.any { d ->
                    d.english.lowercase().contains(q) ||
                        d.urdu.contains(query.trim()) ||
                        d.transliteration.lowercase().contains(q)
                }
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
                        if (cat.titleUr.isNotBlank()) {
                            RtlText(cat.titleUr, fontSize = 17, urduFace = true)
                            Text(
                                cat.titleEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(cat.titleEn, fontWeight = FontWeight.Medium)
                        }
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
    val browseMode by SettingsStore.contentBrowseMode.collectAsState()
    val duas = category?.duas.orEmpty()
    val pagerState = rememberPagerState(pageCount = { duas.size.coerceAtLeast(1) })

    ScreenScaffold(title = category?.titleEn ?: "Duas", onBack = onBack) { padding ->
        if (category == null) {
            EmptyState("Not found", "Category $id", Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrowseModeBar(
                mode = browseMode,
                onMode = { SettingsStore.setContentBrowseMode(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (category.titleAr.isNotBlank() && browseMode == ContentBrowseMode.LIST) {
                ArabicScriptText(category.titleAr, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            when (browseMode) {
                ContentBrowseMode.SLIDE -> SwipeItemPager(
                    items = duas,
                    pagerState = pagerState,
                    key = { it.id },
                    label = { i, _ -> "${category.titleEn} · ${i + 1} / ${duas.size}" },
                    modifier = Modifier.weight(1f)
                ) { dua, _ ->
                    DuaCard(dua, category.titleEn)
                }
                ContentBrowseMode.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(duas, key = { it.id }) { dua -> DuaCard(dua, category.titleEn) }
                }
            }
        }
    }
}

@Composable
private fun DuaCard(dua: Dua, categoryTitle: String) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dua.count > 1) {
                Text("×${dua.count}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Box(Modifier.weight(1f))
            ShareMenuButton {
                ShareCard(
                    title = categoryTitle,
                    kind = "Dua",
                    reference = dua.reference,
                    arabic = dua.arabic,
                    transliteration = dua.transliteration,
                    english = dua.english,
                    urdu = dua.urdu
                )
            }
            BookmarkButton(
                id = "dua:${dua.id}",
                bookmark = {
                    Bookmark(
                        id = "dua:${dua.id}",
                        kind = "Dua",
                        title = categoryTitle,
                        body = dua.english.ifBlank { dua.urdu },
                        route = Routes.duaCategory(dua.categoryId)
                    )
                }
            )
        }
        if (dua.arabic.isNotBlank()) ArabicScriptText(dua.arabic, Modifier.padding(vertical = 6.dp))
        if (dua.transliteration.isNotBlank()) {
            Text(
                dua.transliteration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        if (dua.english.isNotBlank()) EnglishScriptText(dua.english, Modifier.padding(top = 6.dp))
        if (dua.urdu.isNotBlank()) UrduScriptText(dua.urdu, Modifier.padding(top = 8.dp))
        if (dua.reference.isNotBlank()) MutedText(dua.reference, modifier = Modifier.padding(top = 6.dp))
    }
}

// endregion
