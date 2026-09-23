@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.LecturePlayerSession.SleepOption
import com.codefixr.beummati.player.SrtCue
import com.codefixr.beummati.player.SrtCueParser
import com.codefixr.beummati.player.SubtitleLang
import com.codefixr.beummati.player.formatTime
import com.codefixr.beummati.ui.NoteDialog
import com.codefixr.beummati.ui.ShareCard
import com.codefixr.beummati.ui.ShareMenuButton
import kotlinx.coroutines.delay
import kotlin.math.max

/** Muted body copy in the active skin — the player paints itself rather than following the theme. */
@Composable
private fun SkinMuted(text: String, skin: PlayerSkinColors, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = skin.inkSecondary)
}

/** Circular arrow with "15" overlaid — Material has no 15-second skip glyph. */
@Composable
private fun Skip15Icon(forward: Boolean, size: Int = 28) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = if (forward) "Forward 15 seconds" else "Back 15 seconds",
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer { scaleX = if (forward) -1f else 1f }
        )
        Text("15", fontSize = (size / 3.6f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = (size / 9).dp))
    }
}

// region Mini player

@Composable
fun MiniPlayerBar(onOpen: () -> Unit) {
    val state by LecturePlayerSession.state.collectAsState()
    val progress by LecturePlayerSession.progress.collectAsState()
    val cue by LecturePlayerSession.activeCue.collectAsState()
    val skin = playerSkinColors()
    val np = state.nowPlaying ?: return

    Surface(color = skin.bgTop, contentColor = skin.ink, modifier = Modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(
                progress = { (progress.position / max(progress.duration, 1.0)).toFloat().coerceIn(0f, 1f) },
                color = skin.accent,
                trackColor = skin.controlFill,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(np.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        cue?.text?.replace('\n', ' ') ?: np.seriesTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = skin.inkSecondary
                    )
                }
                IconButton(onClick = { LecturePlayerSession.skip(-15.0) }) { Skip15Icon(forward = false, size = 24) }
                IconButton(onClick = { LecturePlayerSession.togglePlay() }) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = skin.accent)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = skin.accent
                        )
                    }
                }
                IconButton(onClick = { LecturePlayerSession.skip(15.0) }) { Skip15Icon(forward = true, size = 24) }
                IconButton(onClick = { LecturePlayerSession.stopAndClear() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop")
                }
            }
        }
    }
}

// endregion

// region Full player

@Composable
fun LecturePlayerScreen(onBack: () -> Unit) {
    val state by LecturePlayerSession.state.collectAsState()
    val progress by LecturePlayerSession.progress.collectAsState()
    val cue by LecturePlayerSession.activeCue.collectAsState()
    val cues by LecturePlayerSession.cues.collectAsState()
    val downloads by LecturePlayerSession.downloads.collectAsState()
    val skin = playerSkinColors()
    var showTranscript by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val np = state.nowPlaying
    LaunchedEffect(np) { if (np == null) onBack() }
    if (np == null) return

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(skin.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = skin.ink,
                    navigationIconContentColor = skin.ink,
                    actionIconContentColor = skin.ink
                ),
                title = {
                    Column {
                        Text(
                            np.seriesTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = skin.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(np.chapterTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimise") }
                },
                actions = {
                    ShareMenuButton {
                        val line = cue?.text.orEmpty()
                        val rtl = line.isNotBlank() && SrtCueParser.isPrimarilyRtl(line)
                        ShareCard(
                            title = np.chapterTitle,
                            kind = np.seriesTitle,
                            reference = formatTime(progress.position),
                            arabic = if (rtl) line else "",
                            english = if (rtl) "" else line
                        )
                    }
                    IconButton(onClick = { noteDraft = LecturePlayerSession.momentSnapshot() }) {
                        Icon(Icons.Outlined.PostAdd, contentDescription = "Save moment as note")
                    }
                    DownloadButton(
                        progress = downloads[np.track.id],
                        skin = skin,
                        onDownload = { LecturePlayerSession.download(np.track) },
                        onRemove = { LecturePlayerSession.removeDownload(np.track) }
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Subtitle / transcript area
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (showTranscript && cues.isNotEmpty()) {
                    TranscriptList(cues = cues, active = cue, skin = skin)
                } else {
                    SubtitlePanel(cue = cue, hasCues = cues.isNotEmpty(), error = state.error, skin = skin)
                }
            }

            // Controls
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item { Scrubber(position = progress.position, duration = progress.duration, skin = skin) }
                item {
                    Transport(
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        hasNext = state.queue.isNotEmpty(),
                        skin = skin
                    )
                }
                item { OptionsRow(rate = state.rate, sleep = state.sleep, sleepEndsAt = state.sleepEndsAtMillis, skin = skin) }
                item {
                    SubtitleControls(
                        label = state.subtitleLabel,
                        langs = state.availableLangs,
                        lang = state.subtitleLang,
                        offset = state.syncOffset,
                        hasCues = cues.isNotEmpty(),
                        showTranscript = showTranscript,
                        skin = skin,
                        onToggleTranscript = { showTranscript = !showTranscript }
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Volume boost (+15 dB)", style = MaterialTheme.typography.bodyMedium, color = skin.ink)
                            SkinMuted("For quiet archive recordings like Book of Jihad", skin)
                        }
                        Switch(
                            checked = state.boostEnabled,
                            onCheckedChange = { LecturePlayerSession.setBoost(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = skin.onAccent,
                                checkedTrackColor = skin.accent,
                                uncheckedTrackColor = skin.controlFill,
                                uncheckedBorderColor = skin.inkSecondary
                            )
                        )
                    }
                }
                if (state.queue.isNotEmpty()) {
                    item {
                        Text(
                            "UP NEXT · ${state.queue.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = skin.accent,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(state.queue.take(20), key = { it.track.id }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { LecturePlayerSession.playFromQueue(item) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = skin.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = skin.ink)
                        }
                        HorizontalDivider(color = skin.inkSecondary.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    noteDraft?.let { (title, body, ref) ->
        NoteDialog(
            initialTitle = title,
            initialBody = body,
            heading = "Save moment",
            onDismiss = { noteDraft = null },
            onSave = { t, b ->
                SavedStore.addNote(t, b, ref = ref)
                noteDraft = null
            }
        )
    }
}

@Composable
private fun SubtitlePanel(cue: SrtCue?, hasCues: Boolean, error: String?, skin: PlayerSkinColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(
                "Playback error: $error",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            cue != null -> {
                val rtl = SrtCueParser.isPrimarilyRtl(cue.text)
                Text(
                    cue.text,
                    style = TextStyle(
                        fontSize = if (rtl) 26.sp else 22.sp,
                        lineHeight = if (rtl) 42.sp else 32.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Content,
                        color = skin.ink
                    )
                )
            }
            hasCues -> SkinMuted("…", skin)
            else -> SkinMuted("No subtitles for this lecture", skin)
        }
    }
}

@Composable
private fun TranscriptList(cues: List<SrtCue>, active: SrtCue?, skin: PlayerSkinColors) {
    val listState = rememberLazyListState()
    val activeIndex = active?.let { a -> cues.indexOfFirst { it.id == a.id } } ?: -1
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(max(0, activeIndex - 2))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(cues, key = { _, c -> c.id }) { i, c ->
            val isActive = i == activeIndex
            val rtl = SrtCueParser.isPrimarilyRtl(c.text)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { LecturePlayerSession.seekTo(c.start + 0.01) }
                    .background(
                        if (isActive) skin.accent.copy(alpha = 0.16f) else Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                Text(
                    formatTime(c.start),
                    style = MaterialTheme.typography.labelSmall,
                    color = skin.accent,
                    modifier = Modifier.width(52.dp).padding(top = 2.dp)
                )
                Text(
                    c.text,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontSize = if (rtl) 18.sp else 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Content,
                        color = if (isActive) skin.ink else skin.inkSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun Scrubber(position: Double, duration: Double, skin: PlayerSkinColors) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val maxValue = max(duration, 1.0).toFloat()
    val shown = if (dragging) dragValue else position.toFloat().coerceIn(0f, maxValue)
    Column {
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                LecturePlayerSession.seekTo(dragValue.toDouble())
                dragging = false
            },
            valueRange = 0f..maxValue,
            colors = SliderDefaults.colors(
                thumbColor = skin.accent,
                activeTrackColor = skin.accent,
                inactiveTrackColor = skin.controlFill
            )
        )
        Row {
            SkinMuted(formatTime(shown.toDouble()), skin)
            Spacer(Modifier.weight(1f))
            SkinMuted("-" + formatTime(max(0.0, duration - shown)), skin)
        }
    }
}

@Composable
private fun Transport(isPlaying: Boolean, isBuffering: Boolean, hasNext: Boolean, skin: PlayerSkinColors) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val skipColors = IconButtonDefaults.iconButtonColors(
            contentColor = skin.ink,
            disabledContentColor = skin.inkSecondary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.size(48.dp))
        IconButton(
            onClick = { LecturePlayerSession.skip(-15.0) },
            colors = skipColors,
            modifier = Modifier.size(56.dp)
        ) { Skip15Icon(forward = false, size = 34) }
        FilledIconButton(
            onClick = { LecturePlayerSession.togglePlay() },
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = skin.accent,
                contentColor = skin.onAccent
            )
        ) {
            if (isBuffering && !isPlaying) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = skin.onAccent)
            } else {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        IconButton(
            onClick = { LecturePlayerSession.skip(15.0) },
            colors = skipColors,
            modifier = Modifier.size(56.dp)
        ) { Skip15Icon(forward = true, size = 34) }
        IconButton(
            onClick = { LecturePlayerSession.playNextInQueue() },
            enabled = hasNext,
            colors = skipColors,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next lecture")
        }
    }
}

@Composable
private fun OptionsRow(rate: Float, sleep: SleepOption, sleepEndsAt: Long?, skin: PlayerSkinColors) {
    var rateMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        while (sleepEndsAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val chipColors = AssistChipDefaults.assistChipColors(
        containerColor = skin.controlFill,
        labelColor = skin.ink,
        leadingIconContentColor = skin.accent
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            AssistChip(
                onClick = { rateMenu = true },
                colors = chipColors,
                border = null,
                label = { Text(formatRate(rate)) },
                leadingIcon = { Icon(Icons.Outlined.Speed, contentDescription = "Playback speed", modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = rateMenu, onDismissRequest = { rateMenu = false }) {
                LecturePlayerSession.rates.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(formatRate(r), fontWeight = if (r == rate) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            LecturePlayerSession.setRate(r)
                            rateMenu = false
                        }
                    )
                }
            }
        }
        Box {
            val label = when {
                sleepEndsAt != null -> "Sleep " + formatTime(max(0L, sleepEndsAt - now) / 1000.0)
                sleep == SleepOption.END_OF_LECTURE -> "Sleep · end"
                else -> "Sleep"
            }
            AssistChip(
                onClick = { sleepMenu = true },
                colors = chipColors,
                border = null,
                label = { Text(label) },
                leadingIcon = { Icon(Icons.Outlined.NightsStay, contentDescription = "Sleep timer", modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                SleepOption.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label, fontWeight = if (opt == sleep) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            LecturePlayerSession.setSleep(opt)
                            sleepMenu = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatRate(rate: Float): String =
    (if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString().trimEnd('0')) + "×"

@Composable
private fun SubtitleControls(
    label: String,
    langs: List<SubtitleLang>,
    lang: SubtitleLang,
    offset: Double,
    hasCues: Boolean,
    showTranscript: Boolean,
    skin: PlayerSkinColors,
    onToggleTranscript: () -> Unit
) {
    val nudge = ButtonDefaults.outlinedButtonColors(contentColor = skin.ink)
    val nudgeBorder = BorderStroke(1.dp, skin.inkSecondary.copy(alpha = 0.5f))
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subtitles", style = MaterialTheme.typography.titleSmall, color = skin.ink, modifier = Modifier.weight(1f))
            if (hasCues) {
                TextButton(
                    onClick = onToggleTranscript,
                    colors = ButtonDefaults.textButtonColors(contentColor = skin.accent)
                ) {
                    Text(if (showTranscript) "Hide transcript" else "Transcript")
                }
            }
        }
        if (label.isNotBlank()) SkinMuted(label, skin)
        if (langs.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(langs, key = { it.name }) { l ->
                    FilterChip(
                        selected = l == lang,
                        onClick = { LecturePlayerSession.setSubtitleLang(l) },
                        label = { Text(l.label) },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = skin.controlFill,
                            labelColor = skin.ink,
                            selectedContainerColor = skin.accent,
                            selectedLabelColor = skin.onAccent
                        )
                    )
                }
            }
        }
        if (hasCues) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                SkinMuted("Sync", skin, modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(
                    onClick = { LecturePlayerSession.nudgeSync(-1.0) },
                    colors = nudge,
                    border = nudgeBorder,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("−1s") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { LecturePlayerSession.nudgeSync(-0.25) },
                    colors = nudge,
                    border = nudgeBorder,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("−¼") }
                Text(
                    "%+.2fs".format(offset),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = skin.ink,
                    modifier = Modifier.width(64.dp).clickable { LecturePlayerSession.resetSync() }
                )
                OutlinedButton(
                    onClick = { LecturePlayerSession.nudgeSync(0.25) },
                    colors = nudge,
                    border = nudgeBorder,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("+¼") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { LecturePlayerSession.nudgeSync(1.0) },
                    colors = nudge,
                    border = nudgeBorder,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("+1s") }
            }
            SkinMuted("Positive shows lines later. Saved per series · tap value to reset.", skin)
        }
    }
}

@Composable
private fun DownloadButton(progress: Float?, skin: PlayerSkinColors, onDownload: () -> Unit, onRemove: () -> Unit) {
    when {
        progress == null -> IconButton(onClick = onDownload) {
            Icon(Icons.Outlined.FileDownload, contentDescription = "Download for offline", tint = skin.ink)
        }
        progress >= 1f -> IconButton(onClick = onRemove) {
            Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded — tap to remove", tint = skin.accent)
        }
        else -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                color = skin.accent,
                trackColor = skin.controlFill,
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

// endregion
