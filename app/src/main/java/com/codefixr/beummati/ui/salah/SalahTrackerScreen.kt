package com.codefixr.beummati.ui.salah

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.SalahDayLog
import com.codefixr.beummati.data.SalahName
import com.codefixr.beummati.data.SalahStatus
import com.codefixr.beummati.data.SalahTracker
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SalahTrackerScreen(onBack: () -> Unit) {
    val logs by SalahTracker.logs.collectAsState()
    val prayerDay by PrayerService.day.collectAsState()
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    val log = logs[SalahTracker.dayKey(selected)] ?: SalahDayLog()
    val streak = remember(logs, today) { SalahTracker.gentleStreak(today) }
    val week = remember(logs, today) { SalahTracker.week(today) }
    val dayLabel = remember(selected) { selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }

    ScreenScaffold(title = "Salah", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streak > 0) {
                            Icon(
                                Icons.Filled.Whatshot,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${log.fulfilledCount} of 5 fulfilled${if (selected == today) " today" else ""}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (streak == 0) "Gentle streak — log at least one prayer" else "$streak-day gentle streak",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "${log.fulfilledCount}/5",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    MutedText("A day counts once you log at least one prayer.", modifier = Modifier.padding(top = 8.dp))
                }
            }

            item { SectionHeader("Last 7 days") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { (date, dayLog) ->
                        WeekCell(
                            date = date,
                            log = dayLog,
                            isSelected = date == selected,
                            onClick = { selected = date },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SectionHeader(if (selected == today) "Today" else dayLabel)
                MutedText("Tap a prayer to cycle prayed → made up → missed.")
            }
            items(SalahName.entries.size) { index ->
                val name = SalahName.entries[index]
                val time = prayerDay?.obligatory?.firstOrNull { it.first == name.title }?.second
                PrayerRow(
                    name = name,
                    status = log[name],
                    time = time.takeIf { selected == today },
                    onClick = { SalahTracker.cycle(name, selected) }
                )
            }

            item {
                MutedText(
                    "${log.fulfilledCount} of 5 fulfilled${if (selected == today) " today" else ""}",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekCell(
    date: LocalDate,
    log: SalahDayLog,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filled = log.fulfilledCount
    val tint = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEE")),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$filled",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    filled >= 5 -> tint
                    filled > 0 -> tint.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                }
            )
        }
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PrayerRow(name: SalahName, status: SalahStatus, time: String?, onClick: () -> Unit) {
    val (icon, tint) = statusIcon(status)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = status.label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name.title, fontWeight = FontWeight.Medium)
            MutedText(status.label, maxLines = 1)
        }
        if (!time.isNullOrBlank()) {
            Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun statusIcon(status: SalahStatus): Pair<ImageVector, Color> = when (status) {
    SalahStatus.NONE -> Icons.Outlined.Circle to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    SalahStatus.PRAYED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
    SalahStatus.QADA -> Icons.Filled.Replay to MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    SalahStatus.MISSED -> Icons.Outlined.Close to MaterialTheme.colorScheme.error
}
