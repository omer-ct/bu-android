package com.codefixr.beummati.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.Http
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CalendarDayRow(val gregorian: String, val hijri: String)

@Composable
fun IslamicCalendarScreen(onBack: () -> Unit) {
    val prayerDay by PrayerService.day.collectAsState()
    var rows by remember { mutableStateOf<List<CalendarDayRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { loadMonth() }
                .onSuccess { rows = it }
                .onFailure { error = it.message ?: "Could not load calendar" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    ScreenScaffold(title = "Calendar", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Today") }
            item {
                ContentCard {
                    val hijri = prayerDay?.hijriDate?.takeIf { it.isNotBlank() }
                    if (hijri != null) {
                        Text(
                            hijri,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val weekday = prayerDay?.hijriWeekday.orEmpty()
                        val gregorian = prayerDay?.gregorian.orEmpty()
                        MutedText(
                            listOf(weekday, gregorian).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    } else {
                        MutedText("Hijri date appears once prayer times load.")
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("This month", Modifier.weight(1f))
                    TextButton(onClick = { reload() }, enabled = !loading) { Text("Refresh") }
                }
            }
            item {
                ContentCard {
                    when {
                        loading -> {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                MutedText("Converting via Aladhan…", Modifier.padding(top = 12.dp))
                            }
                        }
                        error != null -> MutedText(error!!)
                        rows.isEmpty() -> MutedText("No days loaded.")
                        else -> {
                            rows.forEachIndexed { index, row ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        row.gregorian,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        row.hijri,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadMonth(): List<CalendarDayRow> = withContext(Dispatchers.IO) {
    val month = YearMonth.now()
    val apiFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val showFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    coroutineScope {
        (1..month.lengthOfMonth()).map { day ->
            async {
                val date = month.atDay(day)
                val key = date.format(apiFmt)
                val hijri = fetchHijri(key) ?: return@async null
                CalendarDayRow(date.format(showFmt), hijri)
            }
        }.awaitAll().filterNotNull()
    }
}

private suspend fun fetchHijri(date: String): String? = runCatching {
    val text = Http.getText("https://api.aladhan.com/v1/gToH/$date")
    val root = Catalogs.json.parseToJsonElement(text).jsonObject
    val data = root["data"]?.jsonObject ?: return null
    val hijri = data["hijri"]?.jsonObject ?: return null
    val day = hijri["day"]?.jsonPrimitive?.content ?: return null
    val monthEn = hijri["month"]?.jsonObject?.get("en")?.jsonPrimitive?.content ?: return null
    val year = hijri["year"]?.jsonPrimitive?.content ?: return null
    "$day $monthEn $year"
}.getOrNull()
