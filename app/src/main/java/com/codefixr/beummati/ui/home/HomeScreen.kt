package com.codefixr.beummati.ui.home

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.formatTime
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.Tab
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
    val state by LecturePlayerSession.state.collectAsState()
    val progress by LecturePlayerSession.progress.collectAsState()
    val last by LecturePlayerSession.lastSession.collectAsState()
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    val hijri = remember { hijriToday() }
    val quote = remember {
        Catalogs.scholarQuotes.takeIf { it.isNotEmpty() }?.let { it[LocalDate.now().dayOfYear % it.size] }
    }
    val shortcuts = remember { Catalogs.hisnAlMuslim.shortcuts }

    ScreenScaffold(title = "Today") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(today, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (hijri.isNotEmpty()) MutedText(hijri)
            }

            item { PrayerStubCard() }

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

            if (quote != null) {
                item { SectionHeader("Reflection") }
                item {
                    ContentCard(onClick = { onSwitchTab(Tab.SCHOLARS) }) {
                        if (quote.title.isNotBlank()) Text(quote.title, fontWeight = FontWeight.SemiBold)
                        Text("“${quote.english}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                        MutedText("— ${quote.author}${if (quote.reference.isNotBlank()) " · ${quote.reference}" else ""}")
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

@Composable
private fun PrayerStubCard() {
    ContentCard {
        Text("Prayer times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        MutedText("Location-based times are coming to Android soon.")
        Spacer(Modifier.padding(top = 8.dp))
        listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha").forEachIndexed { i, name ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(name, modifier = Modifier.weight(1f))
                Text("—", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

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
