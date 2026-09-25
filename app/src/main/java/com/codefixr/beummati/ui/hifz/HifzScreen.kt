package com.codefixr.beummati.ui.hifz

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Ayah
import com.codefixr.beummati.data.HifzQuizMode
import com.codefixr.beummati.data.HifzStore
import com.codefixr.beummati.data.QuranApi
import com.codefixr.beummati.data.Surah
import com.codefixr.beummati.player.QuranAyahPlayer
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.EnglishScriptText
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.Routes
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.UrduScriptText

@Composable
fun HifzScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val dueToday by HifzStore.dueToday.collectAsState()
    val stats by HifzStore.stats.collectAsState()
    val quizMode by HifzStore.quizMode.collectAsState()
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var reviewAyah by remember { mutableStateOf<Ayah?>(null) }
    var reviewLoading by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    val reviewKey = dueToday.getOrNull(queueIndex.coerceIn(0, (dueToday.size - 1).coerceAtLeast(0)))
        ?.takeIf { dueToday.isNotEmpty() }

    LaunchedEffect(Unit) {
        surahs = runCatching { QuranApi.surahs() }.getOrDefault(emptyList())
    }

    LaunchedEffect(dueToday) {
        if (dueToday.isEmpty()) {
            queueIndex = 0
        } else if (queueIndex >= dueToday.size) {
            queueIndex = dueToday.lastIndex
        }
    }

    LaunchedEffect(reviewKey, quizMode) {
        revealed = false
        reviewAyah = null
        QuranAyahPlayer.stop()
        val key = reviewKey ?: return@LaunchedEffect
        reviewLoading = true
        reviewAyah = runCatching { QuranApi.ayah(key) }.getOrNull()
        reviewLoading = false
        if (quizMode == HifzQuizMode.AUDIO_ONLY) {
            reviewAyah?.let { ayah ->
                QuranAyahPlayer.playSurah(
                    context = context,
                    detailAyahs = listOf(ayah),
                    startAyah = ayah.numberInSurah,
                    playTranslation = false,
                    repeatCount = 1
                )
            }
        }
    }

    ScreenScaffold(title = "Hifz", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ContentCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MutedText("MEMORIZED")
                            Text("${stats.memorizedCount} ayahs", fontWeight = FontWeight.SemiBold)
                            if (stats.streakDays > 0) {
                                MutedText("${stats.streakDays}-day streak", Modifier.padding(top = 2.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MutedText("DUE TODAY")
                            Text(
                                "${stats.dueTodayCount}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    MutedText(
                        "Today’s goal: ${stats.markedTodayCount} / ${stats.dailyGoal} new · " +
                            if (stats.weakCount > 0) "${stats.weakCount} weak" else "none weak",
                        Modifier.padding(top = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = {
                            (stats.markedTodayCount.toFloat() / stats.dailyGoal.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { goal ->
                            FilterChip(
                                selected = stats.dailyGoal == goal,
                                onClick = { HifzStore.setDailyGoal(goal) },
                                label = { Text("$goal/day") }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Review mode") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HifzQuizMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = quizMode == mode,
                            onClick = { HifzStore.setQuizMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, HifzQuizMode.entries.size),
                            label = { Text(mode.label, maxLines = 1) }
                        )
                    }
                }
                MutedText(quizMode.blurb, Modifier.padding(top = 4.dp))
            }

            item { SectionHeader("Due today") }

            if (dueToday.isEmpty()) {
                item {
                    ContentCard {
                        MutedText("Nothing due — mark ayahs memorized from the Qur’an reader or batch a surah below.")
                    }
                }
            } else {
                item {
                    ReviewQuizCard(
                        key = reviewKey,
                        index = queueIndex,
                        total = dueToday.size,
                        ayah = reviewAyah,
                        loading = reviewLoading,
                        revealed = revealed,
                        mode = quizMode,
                        isWeak = reviewKey?.let { HifzStore.isWeak(it) } == true,
                        onReveal = { revealed = true },
                        onPlayAudio = {
                            reviewAyah?.let {
                                QuranAyahPlayer.playSurah(
                                    context = context,
                                    detailAyahs = listOf(it),
                                    startAyah = it.numberInSurah,
                                    playTranslation = false,
                                    repeatCount = 1
                                )
                            }
                        },
                        onRemembered = {
                            reviewKey?.let { HifzStore.recordReview(it, remembered = true) }
                            revealed = false
                            if (queueIndex < dueToday.lastIndex) queueIndex++
                        },
                        onForgot = {
                            reviewKey?.let { HifzStore.recordReview(it, remembered = false) }
                            revealed = false
                        },
                        onFlagWeak = {
                            reviewKey?.let { HifzStore.flagWeak(it) }
                            Toast.makeText(context, "Flagged weak — due tomorrow", Toast.LENGTH_SHORT).show()
                        },
                        onUnmark = {
                            reviewKey?.let { HifzStore.unmark(it) }
                            Toast.makeText(context, "Removed from memorized", Toast.LENGTH_SHORT).show()
                        },
                        onPrev = {
                            if (queueIndex > 0) {
                                queueIndex--
                                revealed = false
                            }
                        },
                        onNext = {
                            if (queueIndex < dueToday.lastIndex) {
                                queueIndex++
                                revealed = false
                            }
                        },
                        onOpen = {
                            reviewKey?.let { key ->
                                val s = key.substringBefore(':').toIntOrNull() ?: return@let
                                val a = key.substringAfter(':').toIntOrNull()
                                navigate(Routes.surah(s, a))
                            }
                        }
                    )
                }
                if (dueToday.size > 1) {
                    item {
                        ContentCard {
                            MutedText("Queue (${dueToday.size})")
                            dueToday.forEachIndexed { i, key ->
                                Text(
                                    buildString {
                                        append(if (i == queueIndex) "▸ " else "  ")
                                        append(key)
                                        if (HifzStore.isWeak(key)) append(" · weak")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (i == queueIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            queueIndex = i
                                            revealed = false
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Browse by surah") }
            items(surahs, key = { it.number }) { surah ->
                val progress = HifzStore.progressForSurah(surah.number, surah.numberOfAyahs)
                val count = HifzStore.memorizedInSurah(surah.number)
                ContentCard {
                    Text(
                        surah.englishName.ifBlank { surah.name },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { navigate(Routes.surah(surah.number)) }
                    )
                    MutedText("$count / ${surah.numberOfAyahs} ayahs memorized")
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = {
                            HifzStore.markSurahMemorized(surah.number, surah.numberOfAyahs)
                            Toast.makeText(context, "Marked ${surah.englishName}", Toast.LENGTH_SHORT).show()
                        }) { Text("Mark all") }
                        TextButton(
                            onClick = {
                                HifzStore.unmarkSurah(surah.number)
                                Toast.makeText(context, "Cleared ${surah.englishName}", Toast.LENGTH_SHORT).show()
                            },
                            enabled = count > 0
                        ) { Text("Clear") }
                        TextButton(onClick = { navigate(Routes.surah(surah.number)) }) { Text("Open") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewQuizCard(
    key: String?,
    index: Int,
    total: Int,
    ayah: Ayah?,
    loading: Boolean,
    revealed: Boolean,
    mode: HifzQuizMode,
    isWeak: Boolean,
    onReveal: () -> Unit,
    onPlayAudio: () -> Unit,
    onRemembered: () -> Unit,
    onForgot: () -> Unit,
    onFlagWeak: () -> Unit,
    onUnmark: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    ContentCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MutedText(buildString {
                append(key ?: "—")
                if (total > 0) append(" · ${index + 1}/$total")
                if (isWeak) append(" · weak")
            })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPrev, enabled = index > 0) { Text("Prev") }
            TextButton(onClick = onNext, enabled = index < total - 1) { Text("Next") }
        }
        when {
            loading -> CircularProgressIndicator(Modifier.padding(vertical = 16.dp))
            ayah == null -> MutedText("Couldn’t load this ayah. Check connection or offline Qur’an pack.")
            else -> {
                when (mode) {
                    HifzQuizMode.SHOW_ARABIC -> {
                        ArabicScriptText(ayah.arabic, Modifier.padding(vertical = 8.dp))
                        if (!revealed) {
                            FilledTonalButton(onClick = onReveal, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Reveal translation")
                            }
                        }
                    }
                    HifzQuizMode.HIDE_ARABIC -> {
                        if (!revealed) {
                            MutedText("Recite from memory, then reveal.", Modifier.padding(vertical = 12.dp))
                            FilledTonalButton(onClick = onReveal) { Text("Reveal ayah") }
                        } else {
                            ArabicScriptText(ayah.arabic, Modifier.padding(vertical = 8.dp))
                        }
                    }
                    HifzQuizMode.AUDIO_ONLY -> {
                        FilledTonalButton(onClick = onPlayAudio, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Play audio again")
                        }
                        if (!revealed) {
                            MutedText("Listen, then reveal the text.", Modifier.padding(vertical = 8.dp))
                            FilledTonalButton(onClick = onReveal) { Text("Reveal ayah") }
                        } else {
                            ArabicScriptText(ayah.arabic, Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                if (revealed) {
                    if (ayah.english.isNotBlank()) {
                        EnglishScriptText(ayah.english, Modifier.padding(bottom = 4.dp))
                    }
                    if (ayah.urdu.isNotBlank()) {
                        UrduScriptText(ayah.urdu, Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onRemembered, enabled = ayah != null) { Text("Remembered") }
            TextButton(onClick = onForgot, enabled = ayah != null) { Text("Forgot") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onFlagWeak, enabled = key != null) { Text("Mark weak") }
            TextButton(onClick = onUnmark, enabled = key != null) { Text("Unmark") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}
