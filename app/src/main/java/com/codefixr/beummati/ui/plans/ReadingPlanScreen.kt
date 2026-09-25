package com.codefixr.beummati.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.ReadingPlanKind
import com.codefixr.beummati.data.ReadingPlanStore
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.routeFor

@Composable
fun ReadingPlanScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val active by ReadingPlanStore.active.collectAsState()
    val activeKind = active?.kind?.let { name -> ReadingPlanKind.entries.firstOrNull { it.name == name } }
    val todayTask = ReadingPlanStore.todayTask()
    val todayDone = ReadingPlanStore.isTodayDone()

    ScreenScaffold(title = "Reading plans", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeKind != null && todayTask != null) {
                item { SectionHeader("Today’s task") }
                item {
                    ContentCard {
                        Text(todayTask.title, fontWeight = FontWeight.SemiBold)
                        MutedText(todayTask.subtitle, modifier = Modifier.padding(top = 4.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { navigate(routeFor(todayTask.destination)) }
                            ) { Text("Open") }
                            Spacer(Modifier.width(8.dp))
                            if (todayDone) {
                                MutedText("Done for today")
                            } else {
                                TextButton(onClick = { ReadingPlanStore.markTodayDone() }) {
                                    Text("Mark done")
                                }
                            }
                        }
                        TextButton(
                            onClick = { ReadingPlanStore.clear() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) { Text("Stop plan") }
                    }
                }
            }

            item { SectionHeader("Plans") }
            items(ReadingPlanKind.entries) { kind ->
                val isActive = activeKind == kind
                ContentCard {
                    Text(kind.title, fontWeight = FontWeight.SemiBold)
                    MutedText(kind.blurb, modifier = Modifier.padding(top = 4.dp))
                    if (kind.lengthDays != Int.MAX_VALUE) {
                        MutedText("${kind.lengthDays} days", modifier = Modifier.padding(top = 2.dp))
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        FilledTonalButton(
                            onClick = { ReadingPlanStore.start(kind) },
                            enabled = !isActive
                        ) {
                            Text(if (isActive) "Active" else "Start")
                        }
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { ReadingPlanStore.start(kind) }) { Text("Restart") }
                        }
                    }
                }
            }

            item {
                MutedText(
                    "Plans remember your start date and checked-off days on this device.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }
        }
    }
}
