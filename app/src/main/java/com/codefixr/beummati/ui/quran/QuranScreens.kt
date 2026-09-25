package com.codefixr.beummati.ui.quran

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Ayah
import com.codefixr.beummati.data.Bookmark
import com.codefixr.beummati.data.ContentBrowseMode
import com.codefixr.beummati.data.HifzStore
import com.codefixr.beummati.data.JuzCatalog
import com.codefixr.beummati.data.JuzInfo
import com.codefixr.beummati.data.QuranApi
import com.codefixr.beummati.data.QuranAudioCache
import com.codefixr.beummati.data.QuranReadMode
import com.codefixr.beummati.data.ReadingProgressStore
import com.codefixr.beummati.data.RelatedVerses
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.ScriptFont
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.Surah
import com.codefixr.beummati.data.SurahDetail
import com.codefixr.beummati.data.TafsirApi
import com.codefixr.beummati.data.TranslationChoices
import com.codefixr.beummati.player.QuranAyahPlayer
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
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton
import com.codefixr.beummati.ui.SwipeItemPager
import com.codefixr.beummati.ui.TripleScriptText
import com.codefixr.beummati.ui.UrduScriptText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import kotlinx.coroutines.launch

@Composable
fun QuranListScreen(
    onOpenSurah: (Int) -> Unit,
    onOpenParah: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    var browseTab by remember { mutableStateOf(QuranBrowseTab.SURAH_QIRAT) }
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { surahs = QuranApi.surahs() }

    val filtered = remember(surahs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) surahs else surahs.filter {
            it.number.toString() == q ||
                it.englishName.lowercase().contains(q) ||
                it.englishNameTranslation.lowercase().contains(q) ||
                it.name.contains(query.trim())
        }
    }

    val filteredParahs = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) JuzCatalog.all
        else JuzCatalog.all.filter {
            it.number.toString() == q ||
                it.titleEnglish.lowercase().contains(q) ||
                it.titleUrdu.contains(query.trim()) ||
                it.startSurahName.lowercase().contains(q) ||
                it.startKey.contains(q) ||
                it.endKey.contains(q)
        }
    }

    ScreenScaffold(
        title = "Qur’an",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                QuranBrowseTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = browseTab == tab,
                        onClick = { browseTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, QuranBrowseTab.entries.size),
                        label = { Text(tab.label) }
                    )
                }
            }
            MutedText(
                when (browseTab) {
                    QuranBrowseTab.SURAH_QIRAT -> "Arabic only · open a surah to read (Qirat)"
                    QuranBrowseTab.PARAH -> "Arabic only · thirty parahs (ajzā’)"
                },
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        when (browseTab) {
                            QuranBrowseTab.SURAH_QIRAT -> "Search surah name or number"
                            QuranBrowseTab.PARAH -> "Search parah number or surah"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when (browseTab) {
                QuranBrowseTab.SURAH_QIRAT -> {
                    if (surahs.isEmpty()) {
                        LoadingBox(Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filtered, key = { it.number }) { s ->
                                SurahListRow(s, onClick = { onOpenSurah(s.number) })
                            }
                        }
                    }
                }
                QuranBrowseTab.PARAH -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredParahs, key = { it.number }) { juz ->
                            ParahListRow(juz, onClick = { onOpenParah(juz.number) })
                        }
                    }
                }
            }
        }
    }
}

private enum class QuranBrowseTab(val label: String) {
    SURAH_QIRAT("Surah Qirat"),
    PARAH("Parah")
}

@Composable
private fun SurahListRow(s: Surah, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberBadge(s.number)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.englishName, fontWeight = FontWeight.SemiBold)
            val meta = listOf(s.englishNameTranslation, s.revelationType, if (s.numberOfAyahs > 0) "${s.numberOfAyahs} ayahs" else "")
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotEmpty()) MutedText(meta, maxLines = 1)
        }
        if (s.name.isNotBlank()) {
            Text(s.name, style = MaterialTheme.typography.titleMedium)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun ParahListRow(juz: JuzInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberBadge(juz.number)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(juz.titleEnglish, fontWeight = FontWeight.SemiBold)
            MutedText("${juz.startSurahName} · ${juz.rangeLabel}", maxLines = 1)
        }
        Text(juz.titleUrdu, style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun NumberBadge(n: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParahScreen(number: Int, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val info = JuzCatalog.get(number)
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<Ayah>>>(LoadState.Loading) }
    var showJump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var tafsirFor by remember { mutableStateOf<Ayah?>(null) }
    var noteFor by remember { mutableStateOf<Ayah?>(null) }
    var mushafMenuFor by remember { mutableStateOf<Ayah?>(null) }
    var wordsSheetKey by remember { mutableStateOf<String?>(null) }
    var tafsir by remember { mutableStateOf<LoadState<Map<Int, String>>?>(null) }
    var tafsirSurah by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val arabicFont by SettingsStore.arabicFont.collectAsState()
    val immersive by ImmersiveReading.active.collectAsState()
    BindImmersiveReading()

    LaunchedEffect(number, reload) {
        state = LoadState.Loading
        state = runCatching { QuranApi.juzAyahs(number) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }

    // Prefetch Ibn Kathir for whatever surah the long-press menu/tafsir sheet needs.
    val menuSurah = mushafMenuFor?.surah ?: tafsirFor?.surah
    LaunchedEffect(menuSurah) {
        val surah = menuSurah ?: return@LaunchedEffect
        if (surah == tafsirSurah && tafsir is LoadState.Loaded) return@LaunchedEffect
        tafsirSurah = surah
        tafsir = LoadState.Loading
        tafsir = runCatching { TafsirApi.urduIbnKathir(surah) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }

    val title = info?.titleUrdu ?: "Parah $number"
    val ayahCount = (state as? LoadState.Loaded)?.value?.size ?: 0

    ScreenScaffold(
        title = title,
        onBack = onBack,
        hideTopBar = immersive,
        actions = {
            IconButton(
                onClick = {
                    jumpText = ""
                    showJump = true
                },
                enabled = ayahCount > 0
            ) {
                Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Jump in parah")
            }
            IconButton(onClick = { ImmersiveReading.set(true) }) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "Full screen")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Reading settings")
            }
        }
    ) { padding ->
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> {
                val ayahs = s.value
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!immersive) {
                        item(key = "parah-header") {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                Text(info?.titleUrdu.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                                MutedText(
                                    listOfNotNull(info?.titleEnglish, info?.rangeLabel, "${ayahs.size} ayahs")
                                        .joinToString(" · "),
                                    Modifier.padding(top = 4.dp).fillMaxWidth()
                                )
                                MutedText(
                                    "Arabic only · tap for full screen · long-press for tools",
                                    Modifier.padding(top = 4.dp).fillMaxWidth()
                                )
                            }
                        }
                    }
                    items(ayahs.size, key = { ayahs[it].key }) { index ->
                        val ayah = ayahs[index]
                        // Show a surah marker when the parah crosses into a new chapter.
                        val prev = ayahs.getOrNull(index - 1)
                        if (prev == null || prev.surah != ayah.surah) {
                            Text(
                                "سورة ${ayah.surah}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        MushafAyahRow(
                            ayah = ayah,
                            arabicFont = arabicFont,
                            onTap = { ImmersiveReading.toggle() },
                            onLongPress = { mushafMenuFor = ayah }
                        )
                    }
                }
            }
        }
    }

    if (showJump && ayahCount > 0) {
        JumpToAyahDialog(
            maxAyah = ayahCount,
            surahName = info?.titleEnglish ?: "Parah $number",
            text = jumpText,
            onTextChange = { jumpText = it.filter { ch -> ch.isDigit() }.take(4) },
            onDismiss = { showJump = false },
            onGo = {
                val n = jumpText.toIntOrNull()
                if (n != null && n in 1..ayahCount) {
                    showJump = false
                    // index 0 = header, ayah position n → index n
                    scope.launch { listState.animateScrollToItem(n) }
                }
            }
        )
    }

    mushafMenuFor?.let { ayah ->
        AyahActionsSheet(
            ayah = ayah,
            surahName = "Parah $number · ${ayah.key}",
            arabicFont = arabicFont,
            hasTafsir = (tafsir as? LoadState.Loaded)?.value?.containsKey(ayah.numberInSurah) == true,
            onDismiss = { mushafMenuFor = null },
            onTafsir = {
                mushafMenuFor = null
                tafsirFor = ayah
            },
            onWords = {
                mushafMenuFor = null
                wordsSheetKey = ayah.key
            },
            onNote = {
                mushafMenuFor = null
                noteFor = ayah
            }
        )
    }

    wordsSheetKey?.let { key ->
        WordByWordSheet(ayahKey = key, onDismiss = { wordsSheetKey = null })
    }

    tafsirFor?.let { ayah ->
        val body = (tafsir as? LoadState.Loaded)?.value?.get(ayah.numberInSurah).orEmpty()
        TafsirSheet(
            ayahKey = ayah.key,
            arabic = ayah.arabic(arabicFont),
            body = body,
            loading = tafsir is LoadState.Loading,
            failed = (tafsir as? LoadState.Failed)?.message,
            onDismiss = { tafsirFor = null }
        )
    }

    noteFor?.let { ayah ->
        NoteDialog(
            initialTitle = "Qur’an ${ayah.key}",
            initialBody = "",
            onDismiss = { noteFor = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "quran:${ayah.key}", route = Routes.parah(number))
                noteFor = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahScreen(
    number: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    navigate: (String) -> Unit = {},
    initialAyah: Int? = null
) {
    val openRelated: (String) -> Unit = { key ->
        RelatedVerses.parseKey(key)?.let { (s, a) -> navigate(Routes.surah(s, a)) }
    }
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<SurahDetail>>(LoadState.Loading) }
    var tafsir by remember { mutableStateOf<LoadState<Map<Int, String>>>(LoadState.Loading) }
    var noteFor by remember { mutableStateOf<Ayah?>(null) }
    var tafsirFor by remember { mutableStateOf<Ayah?>(null) }
    var mushafMenuFor by remember { mutableStateOf<Ayah?>(null) }
    var wordsSheetKey by remember { mutableStateOf<String?>(null) }
    var showJump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    // Words are fetched one ayah at a time, so expansion is opt-in per ayah (translation mode).
    var expandedWords by remember(number) { mutableStateOf(emptySet<String>()) }
    var didJumpToInitial by remember(number, initialAyah) { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showVideoShare by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val play by QuranAyahPlayer.current.collectAsState()
    val playingAyah = play.surah.takeIf { it == number }?.let { play.ayah.takeIf { a -> a > 0 } }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val readMode by SettingsStore.quranReadMode.collectAsState()
    val browseMode by SettingsStore.contentBrowseMode.collectAsState()
    val englishId by SettingsStore.englishTranslationId.collectAsState()
    val urduId by SettingsStore.urduTranslationId.collectAsState()
    val arabicFont by SettingsStore.arabicFont.collectAsState()
    val immersive by ImmersiveReading.active.collectAsState()
    BindImmersiveReading()

    val showBismillah = number != 1 && number != 9
    val ayahCountForPager = (state as? LoadState.Loaded)?.value?.ayahs?.size ?: 0
    val pagerState = rememberPagerState(
        initialPage = ((initialAyah ?: 1) - 1).coerceAtLeast(0),
        pageCount = { ayahCountForPager.coerceAtLeast(1) }
    )

    // Picking a different translation in Reading settings refetches the surah.
    LaunchedEffect(number, reload, englishId, urduId) {
        state = LoadState.Loading
        state = runCatching { QuranApi.surah(number, englishId, urduId) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }
    // Prefetch tafsir so opening a sheet is instant; keep the list short.
    LaunchedEffect(number) {
        tafsir = LoadState.Loading
        tafsir = runCatching { TafsirApi.urduIbnKathir(number) }
            .fold({ LoadState.Loaded(it) }, { LoadState.Failed(it.message ?: "Network error") })
    }

    // Search / deep-link: land on the ayah in Translation mode so EN/UR context is visible.
    LaunchedEffect(state, initialAyah, readMode, browseMode) {
        val ayah = initialAyah ?: return@LaunchedEffect
        if (didJumpToInitial) return@LaunchedEffect
        if (state !is LoadState.Loaded) return@LaunchedEffect
        val count = (state as LoadState.Loaded).value.ayahs.size
        if (ayah !in 1..count) return@LaunchedEffect
        if (browseMode == ContentBrowseMode.SLIDE) {
            didJumpToInitial = true
            pagerState.scrollToPage(ayah - 1)
            return@LaunchedEffect
        }
        if (readMode != QuranReadMode.TRANSLATION) {
            SettingsStore.setQuranReadMode(QuranReadMode.TRANSLATION)
            return@LaunchedEffect
        }
        didJumpToInitial = true
        listState.animateScrollToItem(ayahScrollIndex(readMode, ayah, showBismillah, immersive))
    }

    LaunchedEffect(play.surah, play.ayah, number) {
        if (play.surah == number && play.ayah > 0) {
            ReadingProgressStore.save(context, number, play.ayah)
        }
    }
    LaunchedEffect(state, number, initialAyah, play.surah) {
        if (state !is LoadState.Loaded) return@LaunchedEffect
        if (play.surah == number) return@LaunchedEffect
        ReadingProgressStore.save(context, number, initialAyah ?: 1)
    }
    LaunchedEffect(play.surah, play.ayah, number, browseMode, readMode, state, immersive) {
        if (play.surah != number || play.ayah <= 0) return@LaunchedEffect
        val loaded = state as? LoadState.Loaded ?: return@LaunchedEffect
        if (browseMode == ContentBrowseMode.SLIDE) {
            pagerState.animateScrollToPage(
                (play.ayah - 1).coerceIn(0, (loaded.value.ayahs.size - 1).coerceAtLeast(0))
            )
        } else {
            listState.animateScrollToItem(ayahScrollIndex(readMode, play.ayah, showBismillah, immersive))
        }
    }

    if (showVideoShare) {
        when (val s = state) {
            is LoadState.Loaded -> {
                ShareAyahVideoScreen(
                    surahName = s.value.surah.englishName.ifBlank { "Surah $number" },
                    ayahs = s.value.ayahs,
                    arabicFont = arabicFont,
                    initialSelected = setOfNotNull(playingAyah ?: initialAyah),
                    onBack = { showVideoShare = false }
                )
                return
            }
            LoadState.Loading -> LoadingBox(Modifier.fillMaxSize())
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.fillMaxSize())
        }
        return
    }

    val title = (state as? LoadState.Loaded)?.value?.surah?.englishName?.takeIf { it.isNotBlank() } ?: "Surah $number"
    val ayahCount = (state as? LoadState.Loaded)?.value?.ayahs?.size ?: 0

    ScreenScaffold(
        title = title,
        onBack = onBack,
        hideTopBar = immersive,
        actions = {
            IconButton(
                onClick = {
                    jumpText = ""
                    showJump = true
                },
                enabled = ayahCount > 0
            ) {
                Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Jump to ayah")
            }
            IconButton(onClick = { showAudio = !showAudio }) {
                Icon(Icons.Outlined.Headphones, contentDescription = "Ayah audio")
            }
            IconButton(onClick = { ImmersiveReading.set(true) }) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "Full screen")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Reading settings")
            }
        }
    ) { padding ->
        when (val s = state) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Failed -> ErrorBox(s.message, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            is LoadState.Loaded -> {
                val detail = s.value
                val tafsirMap = (tafsir as? LoadState.Loaded)?.value.orEmpty()
                val onPlayAyah: (Ayah) -> Unit = { ayah ->
                    QuranAyahPlayer.playAyah(context, ayah, detail.ayahs)
                }
                val onMemorizedAyah: (Ayah) -> Unit = { ayah ->
                    if (HifzStore.isMemorized(ayah.key)) {
                        HifzStore.unmark(ayah.key)
                        Toast.makeText(context, "Removed from Hifz", Toast.LENGTH_SHORT).show()
                    } else {
                        HifzStore.markMemorized(ayah.key)
                        val tip = if (HifzStore.consumeFirstMemorizeHint()) {
                            "Saved · open Hifz for spaced review"
                        } else {
                            "Marked memorized · due tomorrow"
                        }
                        Toast.makeText(context, tip, Toast.LENGTH_LONG).show()
                    }
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (!immersive && showAudio) {
                        QuranAudioBar(
                            ayahs = detail.ayahs,
                            highlightedAyah = playingAyah,
                            onOpenVideoShare = { showVideoShare = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    if (!immersive) {
                        ReadModeBar(
                            mode = readMode,
                            onMode = {
                                SettingsStore.setQuranReadMode(it)
                                scope.launch { listState.scrollToItem(0) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        BrowseModeBar(
                            mode = browseMode,
                            onMode = { SettingsStore.setContentBrowseMode(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    when (browseMode) {
                        ContentBrowseMode.SLIDE -> {
                            SwipeItemPager(
                                items = detail.ayahs,
                                pagerState = pagerState,
                                key = { it.key },
                                label = { _, ayah -> "Ayah ${ayah.numberInSurah} / ${detail.ayahs.size}" },
                                showChrome = !immersive,
                                modifier = Modifier.weight(1f)
                            ) { ayah, _ ->
                                when (readMode) {
                                    QuranReadMode.MUSHAF -> ContentCard {
                                        MushafAyahRow(
                                            ayah = ayah,
                                            arabicFont = arabicFont,
                                            onTap = { ImmersiveReading.toggle() },
                                            onLongPress = { mushafMenuFor = ayah }
                                        )
                                    }
                                    QuranReadMode.TRANSLATION -> AyahCard(
                                        ayah = ayah,
                                        arabicFont = arabicFont,
                                        surahName = detail.surah.englishName.ifBlank { "Surah ${detail.surah.number}" },
                                        hasTafsir = tafsirMap.containsKey(ayah.numberInSurah),
                                        tafsirReady = tafsir is LoadState.Loaded,
                                        showWords = ayah.key in expandedWords,
                                        highlighted = playingAyah == ayah.numberInSurah,
                                        compact = immersive,
                                        onTap = { ImmersiveReading.toggle() },
                                        onNote = { noteFor = ayah },
                                        onTafsir = { tafsirFor = ayah },
                                        onToggleWords = {
                                            expandedWords = if (ayah.key in expandedWords) {
                                                expandedWords - ayah.key
                                            } else {
                                                expandedWords + ayah.key
                                            }
                                        },
                                        onPlay = { onPlayAyah(ayah) },
                                        onMemorized = { onMemorizedAyah(ayah) },
                                        onShareVideo = { showVideoShare = true },
                                        onOpenRelated = openRelated
                                    )
                                }
                            }
                        }
                        ContentBrowseMode.LIST -> when (readMode) {
                            QuranReadMode.MUSHAF -> MushafReader(
                                detail = detail,
                                arabicFont = arabicFont,
                                showBismillah = showBismillah,
                                listState = listState,
                                immersive = immersive,
                                onTapAyah = { ImmersiveReading.toggle() },
                                onLongPressAyah = { mushafMenuFor = it }
                            )
                            QuranReadMode.TRANSLATION -> TranslationReader(
                                detail = detail,
                                arabicFont = arabicFont,
                                tafsirMap = tafsirMap,
                                tafsirState = tafsir,
                                expandedWords = expandedWords,
                                listState = listState,
                                englishId = englishId,
                                urduId = urduId,
                                playingAyah = playingAyah,
                                immersive = immersive,
                                onTapAyah = { ImmersiveReading.toggle() },
                                onNote = { noteFor = it },
                                onTafsir = { tafsirFor = it },
                                onToggleWords = { ayah ->
                                    expandedWords = if (ayah.key in expandedWords) {
                                        expandedWords - ayah.key
                                    } else {
                                        expandedWords + ayah.key
                                    }
                                },
                                onPlay = onPlayAyah,
                                onMemorized = onMemorizedAyah,
                                onShareVideo = { showVideoShare = true },
                                onOpenRelated = openRelated
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJump && ayahCount > 0) {
        JumpToAyahDialog(
            maxAyah = ayahCount,
            surahName = title,
            text = jumpText,
            onTextChange = { jumpText = it.filter { ch -> ch.isDigit() }.take(3) },
            onDismiss = { showJump = false },
            onGo = {
                val n = jumpText.toIntOrNull()
                if (n != null && n in 1..ayahCount) {
                    showJump = false
                    scope.launch {
                        if (browseMode == ContentBrowseMode.SLIDE) {
                            pagerState.animateScrollToPage(n - 1)
                        } else {
                            listState.animateScrollToItem(ayahScrollIndex(readMode, n, showBismillah, immersive))
                        }
                    }
                }
            }
        )
    }

    val detailAyahs = (state as? LoadState.Loaded)?.value?.ayahs
    mushafMenuFor?.let { ayah ->
        AyahActionsSheet(
            ayah = ayah,
            surahName = title,
            arabicFont = arabicFont,
            hasTafsir = (tafsir as? LoadState.Loaded)?.value?.containsKey(ayah.numberInSurah) == true,
            onDismiss = { mushafMenuFor = null },
            onTafsir = {
                mushafMenuFor = null
                tafsirFor = ayah
            },
            onWords = {
                mushafMenuFor = null
                wordsSheetKey = ayah.key
            },
            onNote = {
                mushafMenuFor = null
                noteFor = ayah
            },
            onPlay = {
                mushafMenuFor = null
                detailAyahs?.let { QuranAyahPlayer.playAyah(context, ayah, it) }
            },
            onMemorized = {
                mushafMenuFor = null
                if (HifzStore.isMemorized(ayah.key)) {
                    HifzStore.unmark(ayah.key)
                    Toast.makeText(context, "Removed from Hifz", Toast.LENGTH_SHORT).show()
                } else {
                    HifzStore.markMemorized(ayah.key)
                    val tip = if (HifzStore.consumeFirstMemorizeHint()) {
                        "Saved · open Hifz for spaced review"
                    } else {
                        "Marked memorized · due tomorrow"
                    }
                    Toast.makeText(context, tip, Toast.LENGTH_LONG).show()
                }
            },
            onShareVideo = {
                mushafMenuFor = null
                showVideoShare = true
            }
        )
    }

    wordsSheetKey?.let { key ->
        WordByWordSheet(ayahKey = key, onDismiss = { wordsSheetKey = null })
    }

    tafsirFor?.let { ayah ->
        val body = (tafsir as? LoadState.Loaded)?.value?.get(ayah.numberInSurah).orEmpty()
        TafsirSheet(
            ayahKey = ayah.key,
            arabic = ayah.arabic(arabicFont),
            body = body,
            loading = tafsir is LoadState.Loading,
            failed = (tafsir as? LoadState.Failed)?.message,
            onDismiss = { tafsirFor = null }
        )
    }

    noteFor?.let { ayah ->
        NoteDialog(
            initialTitle = "Qur’an ${ayah.key}",
            initialBody = "",
            onDismiss = { noteFor = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = "quran:${ayah.key}", route = Routes.surah(ayah.surah))
                noteFor = null
            }
        )
    }
}

/** LazyColumn index of ayah [n] (1-based) for the active reader. */
private fun ayahScrollIndex(
    mode: QuranReadMode,
    ayahNumber: Int,
    showBismillah: Boolean,
    immersive: Boolean = false
): Int =
    when (mode) {
        // Mushaf: [optional header][optional bismillah][ayahs…]
        QuranReadMode.MUSHAF ->
            (if (immersive) 0 else 1) + (if (showBismillah) 1 else 0) + (ayahNumber - 1)
        // Translation: [optional header][ayahs…][optional footer]
        QuranReadMode.TRANSLATION ->
            if (immersive) ayahNumber - 1 else ayahNumber
    }

private fun bismillahFor(font: ScriptFont): String =
    if (font.prefersIndoPak) {
        "بِسۡمِ اللهِ الرَّحۡمٰنِ الرَّحِيۡمِ"
    } else {
        "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
    }

@Composable
private fun ReadModeBar(
    mode: QuranReadMode,
    onMode: (QuranReadMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        QuranReadMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, QuranReadMode.entries.size),
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
private fun MushafReader(
    detail: SurahDetail,
    arabicFont: ScriptFont,
    showBismillah: Boolean,
    listState: LazyListState,
    immersive: Boolean = false,
    onTapAyah: () -> Unit = {},
    onLongPressAyah: (Ayah) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!immersive) {
            item(key = "mushaf-header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    if (detail.surah.name.isNotBlank()) {
                        ArabicScriptText(detail.surah.name)
                    }
                    MutedText(
                        "${detail.ayahs.size} ayahs · tap for full screen · long-press for tools",
                        Modifier.padding(top = 4.dp).fillMaxWidth()
                    )
                }
            }
        }
        if (showBismillah) {
            item(key = "bismillah") {
                ArabicScriptText(bismillahFor(arabicFont), Modifier.padding(bottom = 4.dp))
            }
        }
        items(detail.ayahs, key = { it.key }) { ayah ->
            MushafAyahRow(
                ayah = ayah,
                arabicFont = arabicFont,
                onTap = onTapAyah,
                onLongPress = { onLongPressAyah(ayah) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MushafAyahRow(
    ayah: Ayah,
    arabicFont: ScriptFont,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        ArabicScriptText(
            ayah.arabic(arabicFont),
            Modifier.weight(1f).padding(end = 10.dp)
        )
        BrassAyahBadge(ayah.numberInSurah)
    }
}

@Composable
private fun BrassAyahBadge(n: Int) {
    val brass = MaterialTheme.colorScheme.secondary
    Box(
        Modifier
            .size(28.dp)
            .border(1.dp, brass.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$n",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = brass
        )
    }
}

@Composable
private fun TranslationReader(
    detail: SurahDetail,
    arabicFont: ScriptFont,
    tafsirMap: Map<Int, String>,
    tafsirState: LoadState<Map<Int, String>>,
    expandedWords: Set<String>,
    listState: LazyListState,
    englishId: Int,
    urduId: Int,
    playingAyah: Int? = null,
    immersive: Boolean = false,
    onTapAyah: () -> Unit = {},
    onNote: (Ayah) -> Unit,
    onTafsir: (Ayah) -> Unit,
    onToggleWords: (Ayah) -> Unit,
    onPlay: (Ayah) -> Unit = {},
    onMemorized: (Ayah) -> Unit = {},
    onShareVideo: () -> Unit = {},
    onOpenRelated: (String) -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!immersive) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(detail.surah.name, style = MaterialTheme.typography.headlineMedium)
                    MutedText(
                        listOf(detail.surah.englishNameTranslation, detail.surah.revelationType, "${detail.ayahs.size} ayahs")
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    )
                    when (val t = tafsirState) {
                        LoadState.Loading -> MutedText("Preparing tafsir…", Modifier.padding(top = 8.dp))
                        is LoadState.Failed -> MutedText("Tafsir unavailable: ${t.message}", Modifier.padding(top = 8.dp))
                        is LoadState.Loaded -> MutedText(
                            "Tap for full screen · Tafsir / tools on each ayah",
                            Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        items(detail.ayahs, key = { it.key }) { ayah ->
            AyahCard(
                ayah = ayah,
                arabicFont = arabicFont,
                surahName = detail.surah.englishName.ifBlank { "Surah ${detail.surah.number}" },
                hasTafsir = tafsirMap.containsKey(ayah.numberInSurah),
                tafsirReady = tafsirState is LoadState.Loaded,
                showWords = ayah.key in expandedWords,
                highlighted = playingAyah == ayah.numberInSurah,
                compact = immersive,
                onTap = onTapAyah,
                onNote = { onNote(ayah) },
                onTafsir = { onTafsir(ayah) },
                onToggleWords = { onToggleWords(ayah) },
                onPlay = { onPlay(ayah) },
                onMemorized = { onMemorized(ayah) },
                onShareVideo = onShareVideo,
                onOpenRelated = onOpenRelated
            )
        }
        if (!immersive) {
            item(key = "footer") {
                MutedText(
                    "Arabic: ${if (arabicFont.prefersIndoPak) "Indo-Pak" else "Uthmani"} · " +
                        "English: ${TranslationChoices.englishLabel(englishId)} · " +
                        "Urdu: ${TranslationChoices.urduLabel(urduId)} · via quran.com"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahActionsSheet(
    ayah: Ayah,
    surahName: String,
    arabicFont: ScriptFont,
    hasTafsir: Boolean,
    onDismiss: () -> Unit,
    onTafsir: () -> Unit,
    onWords: () -> Unit,
    onNote: () -> Unit,
    onPlay: () -> Unit = {},
    onMemorized: () -> Unit = {},
    onShareVideo: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(ayah.key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MutedText(surahName, Modifier.padding(bottom = 8.dp))
            TextButton(
                onClick = onTafsir,
                enabled = hasTafsir,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Tafsir Ibn Kathir") }
            TextButton(onClick = onWords, modifier = Modifier.fillMaxWidth()) { Text("Word by word") }
            TextButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play ayah") }
            TextButton(onClick = onMemorized, modifier = Modifier.fillMaxWidth()) { Text("Mark memorized") }
            TextButton(onClick = onShareVideo, modifier = Modifier.fillMaxWidth()) { Text("Share video") }
            ShareMenuButton {
                ShareCard(
                    title = surahName,
                    kind = "Qur’an",
                    reference = ayah.key,
                    arabic = ayah.arabic(arabicFont),
                    english = ayah.english,
                    urdu = ayah.urdu
                )
            }
            TextButton(onClick = onNote, modifier = Modifier.fillMaxWidth()) { Text("Add note") }
            BookmarkButton(
                id = "ayah:${ayah.key}",
                bookmark = {
                    Bookmark(
                        id = "ayah:${ayah.key}",
                        kind = "Qur’an",
                        title = "$surahName ${ayah.key}",
                        body = ayah.english,
                        route = Routes.surah(ayah.surah)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordByWordSheet(ayahKey: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Word by word", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MutedText(ayahKey, Modifier.padding(top = 2.dp, bottom = 12.dp))
            WordByWordPanel(ayahKey)
        }
    }
}

@Composable
private fun JumpToAyahDialog(
    maxAyah: Int,
    surahName: String,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGo: () -> Unit
) {
    val valid = text.toIntOrNull()?.let { it in 1..maxAyah } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to ayah #") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MutedText("Jump within $surahName (1–$maxAyah)")
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Ayah number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGo, enabled = valid) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TafsirSheet(
    ayahKey: String,
    arabic: String,
    body: String,
    loading: Boolean,
    failed: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Tafsir Ibn Kathir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MutedText("Urdu · $ayahKey", Modifier.padding(top = 2.dp, bottom = 12.dp))
            if (arabic.isNotBlank()) {
                ArabicScriptText(arabic, Modifier.padding(bottom = 12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            when {
                loading -> MutedText("Loading…", Modifier.padding(top = 16.dp))
                failed != null -> MutedText("Tafsir unavailable: $failed", Modifier.padding(top = 16.dp))
                body.isBlank() -> MutedText("No tafsir for this ayah.", Modifier.padding(top = 16.dp))
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp)
                    ) {
                        UrduScriptText(body)
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahCard(
    ayah: Ayah,
    arabicFont: ScriptFont,
    surahName: String,
    hasTafsir: Boolean,
    tafsirReady: Boolean,
    showWords: Boolean,
    highlighted: Boolean = false,
    compact: Boolean = false,
    onTap: () -> Unit = {},
    onNote: () -> Unit,
    onTafsir: () -> Unit,
    onToggleWords: () -> Unit,
    onPlay: () -> Unit = {},
    onMemorized: () -> Unit = {},
    onShareVideo: () -> Unit = {},
    onOpenRelated: (String) -> Unit = {}
) {
    val memorizedKeys by HifzStore.memorized.collectAsState()
    val isMemorized = ayah.key in memorizedKeys
    val related = remember(ayah.key) { RelatedVerses.related(ayah.key) }
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(highlightColor) else Modifier)
            .clickable(onClick = onTap)
            .padding(if (highlighted) 8.dp else 0.dp)
    ) {
        if (compact) {
            NumberBadge(ayah.numberInSurah)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(ayah.numberInSurah)
                Box(Modifier.weight(1f))
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play ayah")
                }
                TextButton(onClick = onMemorized) {
                    Text(if (isMemorized) "Unmark" else "Memorize")
                }
                IconButton(
                    onClick = onTafsir,
                    enabled = tafsirReady && hasTafsir
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = "Tafsir",
                        tint = when {
                            !tafsirReady || !hasTafsir -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(onClick = onToggleWords) {
                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = if (showWords) "Hide word by word" else "Word by word",
                        tint = if (showWords) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
                ShareMenuButton {
                    ShareCard(
                        title = surahName,
                        kind = "Qur’an",
                        reference = ayah.key,
                        arabic = ayah.arabic(arabicFont),
                        english = ayah.english,
                        urdu = ayah.urdu
                    )
                }
                IconButton(onClick = onNote) { Icon(Icons.Outlined.PostAdd, contentDescription = "Add note") }
                BookmarkButton(
                    id = "ayah:${ayah.key}",
                    bookmark = {
                        Bookmark(
                            id = "ayah:${ayah.key}",
                            kind = "Qur’an",
                            title = "$surahName ${ayah.key}",
                            body = ayah.english,
                            route = Routes.surah(ayah.surah)
                        )
                    }
                )
            }
        }
        TripleScriptText(
            arabic = ayah.arabic(arabicFont),
            english = ayah.english,
            urdu = ayah.urdu,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (!compact) {
            if (related.isNotEmpty()) {
                Text(
                    "See also",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(related, key = { it }) { key ->
                        AssistChip(
                            onClick = { onOpenRelated(key) },
                            label = { Text(key) }
                        )
                    }
                }
            }
            if (tafsirReady && hasTafsir) {
                TextButton(onClick = onTafsir, modifier = Modifier.padding(start = 0.dp)) {
                    Text("Tafsir")
                }
            }
            TextButton(onClick = onShareVideo) { Text("Video") }
            if (showWords) {
                WordByWordPanel(ayah.key, Modifier.padding(top = 10.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }
}
