package com.codefixr.beummati.ui.home

import android.Manifest
import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.Destination
import com.codefixr.beummati.data.PrayerDay
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.ReminderItem
import com.codefixr.beummati.data.ReminderLane
import com.codefixr.beummati.data.ReminderStore
import com.codefixr.beummati.data.SalahTracker
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.formatTime
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.Tab
import com.codefixr.beummati.ui.routeFor
import com.codefixr.beummati.ui.shareText
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val hijriMonths = listOf(
    "Muharram", "Safar", "Rabi‘ al-Awwal", "Rabi‘ al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
    "Rajab", "Sha‘ban", "Ramadan", "Shawwal", "Dhu al-Qa‘dah", "Dhu al-Hijjah"
)

private fun hijriToday(): String = runCatching {
    val cal = IslamicCalendar()
    cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = hijriMonths.getOrElse(cal.get(Calendar.MONTH)) { "" }
    val year = cal.get(Calendar.YEAR)
    "$day $month $year AH"
}.getOrDefault("")

@Composable
fun HomeScreen(navigate: (String) -> Unit, onSwitchTab: (Tab) -> Unit) {
    val context = LocalContext.current
    val state by LecturePlayerSession.state.collectAsState()
    val progress by LecturePlayerSession.progress.collectAsState()
    val last by LecturePlayerSession.lastSession.collectAsState()
    val prayerDay by PrayerService.day.collectAsState()
    val prayerStatus by PrayerService.status.collectAsState()
    val coords by PrayerService.coordinates.collectAsState()
    val salahLogs by SalahTracker.logs.collectAsState()
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    val hijri = remember { hijriToday() }
    val shortcuts = remember { Catalogs.hisnAlMuslim.shortcuts }
    val salahStreak = remember(salahLogs) { SalahTracker.gentleStreak() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { PrayerService.refresh(context, force = true) }

    LaunchedEffect(Unit) {
        if (PrayerService.hasLocationPermission(context)) {
            PrayerService.refresh(context)
        } else {
            PrayerService.refresh(context)
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        ReminderStore.ensureLoaded()
    }

    val openDestination: (Destination) -> Unit = { destination ->
        val route = routeFor(destination)
        if (route == Tab.SCHOLARS.route) onSwitchTab(Tab.SCHOLARS) else navigate(route)
    }

    ScreenScaffold(
        title = "Today",
        actions = {
            IconButton(onClick = { navigate(Routes.SEARCH) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
            IconButton(onClick = { navigate(Routes.SETTINGS) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(today, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val hijriLabel = prayerDay?.hijriDate?.takeIf { it.isNotBlank() }?.let { "$it AH" } ?: hijri
                if (hijriLabel.isNotEmpty()) MutedText(hijriLabel)
            }

            item {
                PrayerCard(
                    day = prayerDay,
                    status = prayerStatus,
                    isFallbackLocation = coords.isFallback,
                    onQibla = { navigate(Routes.QIBLA) },
                    onRefresh = { PrayerService.refresh(context, force = true) },
                    onAllowLocation = {
                        locationPermission.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                )
            }

            item {
                SalahStrip(
                    fulfilled = salahLogs[SalahTracker.dayKey()]?.fulfilledCount ?: 0,
                    streak = salahStreak,
                    onOpen = { navigate(Routes.SALAH) }
                )
            }

            item {
                val np = state.nowPlaying
                when {
                    np != null -> ContinueCard(
                        title = np.chapterTitle,
                        subtitle = "${np.seriesTitle} · ${formatTime(progress.position)} / ${formatTime(progress.duration)}",
                        playing = state.isPlaying,
                        onToggle = { LecturePlayerSession.togglePlay() },
                        onOpen = { navigate(Routes.PLAYER) }
                    )
                    last != null -> {
                        val session = last!!
                        val series = Catalogs.series(session.seriesId)
                        val chapter = Catalogs.chapter(session.seriesId, session.chapterId)
                        if (series != null && chapter != null) {
                            ContinueCard(
                                title = chapter.title,
                                subtitle = "${series.title} · resume at ${formatTime(session.timeSeconds)}",
                                playing = false,
                                onToggle = { LecturePlayerSession.resumeLastSession() },
                                onOpen = {
                                    if (LecturePlayerSession.resumeLastSession()) navigate(Routes.PLAYER)
                                }
                            )
                        }
                    }
                }
            }

            if (state.streakDays > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${state.streakDays}-day listening streak", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Today’s reminders", Modifier.weight(1f))
                    TextButton(onClick = { ReminderStore.refreshAll() }) { Text("Shuffle all") }
                }
            }
            items(ReminderLane.entries.size) { index ->
                val lane = ReminderLane.entries[index]
                ReminderLaneCard(lane = lane, onOpen = openDestination)
            }

            item { SectionHeader("Lecture series") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Catalogs.lectureSeries, key = { it.id }) { s ->
                        Card(
                            onClick = { navigate(Routes.series(s.id)) },
                            modifier = Modifier.width(200.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.padding(top = 6.dp))
                                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                MutedText("${s.chapters.size} lectures", maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (shortcuts.isNotEmpty()) {
                item { SectionHeader("Adhkar") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shortcuts, key = { it.id }) { sc ->
                            AssistChip(
                                onClick = {
                                    val first = sc.categoryIds.firstOrNull()
                                    navigate(if (first != null) Routes.duaCategory(first) else Routes.DUAS)
                                },
                                label = { Text(sc.title) },
                                leadingIcon = {
                                    Icon(
                                        when (sc.id) {
                                            "morning" -> Icons.Outlined.WbSunny
                                            "evening", "sleep" -> Icons.Outlined.NightsStay
                                            else -> Icons.Outlined.AutoStories
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Explore") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { onSwitchTab(Tab.QURAN) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Qur’an")
                    }
                    FilledTonalButton(onClick = { onSwitchTab(Tab.HADITH) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hadith")
                    }
                }
            }

            item {
                MutedText(
                    Catalogs.audio.attribution.orEmpty(),
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }
        }
    }
}

// region Prayer + salah

@Composable
private fun PrayerCard(
    day: PrayerDay?,
    status: String,
    isFallbackLocation: Boolean,
    onQibla: () -> Unit,
    onRefresh: () -> Unit,
    onAllowLocation: () -> Unit
) {
    // Re-evaluate twice a minute so the countdown stays honest.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val next = remember(day, tick) { PrayerService.nextPrayer(day) }
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    next?.let { "Next — ${it.name}" } ?: "Prayer times",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    next?.countdown ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                MutedText(
                    buildList {
                        next?.let { add(if (it.isTomorrow) "${it.time} tomorrow" else it.time) }
                        add(status)
                    }.joinToString(" · ")
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh prayer times")
            }
        }

        Spacer(Modifier.padding(top = 6.dp))
        if (day == null) {
            listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha").forEachIndexed { i, name ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text("—", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                day.all.forEach { (name, time) ->
                    val isNext = next?.name == name
                    Surface(
                        color = if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                name.take(3),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                time,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onQibla, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Qibla")
            }
            if (isFallbackLocation) {
                FilledTonalButton(onClick = onAllowLocation, modifier = Modifier.weight(1f)) {
                    Text("Use my location", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SalahStrip(fulfilled: Int, streak: Int, onOpen: () -> Unit) {
    ContentCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MutedText("SALAH")
                Text(
                    if (streak == 0) "Log today’s prayers" else "$streak-day gentle streak",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "$fulfilled/5",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// endregion

// region Reminder lanes

private fun laneIcon(lane: ReminderLane): ImageVector = when (lane) {
    ReminderLane.QURAN -> Icons.AutoMirrored.Outlined.MenuBook
    ReminderLane.HADITH -> Icons.Outlined.AutoStories
    ReminderLane.SERIES -> Icons.Filled.Headphones
    ReminderLane.QUOTES -> Icons.Outlined.FormatQuote
    ReminderLane.DHIKR -> Icons.Outlined.Spa
}

@Composable
private fun ReminderLaneCard(lane: ReminderLane, onOpen: (Destination) -> Unit) {
    val context = LocalContext.current
    val items by ReminderStore.items.collectAsState()
    val loading by ReminderStore.loading.collectAsState()
    val errors by ReminderStore.errors.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()

    val item: ReminderItem? = items[lane]
    val isLoading = lane in loading && item == null

    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(laneIcon(lane), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                lane.title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(Modifier.weight(1f))
            if (item != null) MutedText(item.ref, maxLines = 1)
        }

        when {
            isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp))
            }
            item == null -> {
                MutedText(errors[lane] ?: "Tap Next for a ${lane.title.lowercase()} reminder.", modifier = Modifier.padding(vertical = 6.dp))
                TextButton(onClick = { ReminderStore.refresh(lane) }) { Text("Next") }
            }
            else -> {
                if (item.title.isNotBlank()) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                if (item.arabic.isNotBlank()) RtlText(item.arabic, fontSize = 22, modifier = Modifier.padding(vertical = 6.dp))
                if (item.english.isNotBlank()) {
                    Text(item.english, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                if (showUrdu && item.urdu.isNotBlank()) RtlText(item.urdu, fontSize = 18, modifier = Modifier.padding(top = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    FilledTonalButton(onClick = { onOpen(item.destination) }) { Text("Open") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { ReminderStore.refresh(lane) }) { Text("Next") }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { shareText(context, item.shareBody()) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                }
            }
        }
    }
}

// endregion

@Composable
private fun ContinueCard(
    title: String,
    subtitle: String,
    playing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    ContentCard(onClick = onOpen) {
        MutedText("CONTINUE LISTENING")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MutedText(subtitle, maxLines = 1)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
