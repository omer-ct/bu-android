package com.codefixr.beummati.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.OfflinePackId
import com.codefixr.beummati.data.OfflinePackState
import com.codefixr.beummati.data.OfflinePackStatus
import com.codefixr.beummati.data.OfflinePacks
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlin.math.roundToInt

/** First-launch chooser — pick packs to keep on device for offline / fast reading. */
@Composable
fun OfflineSetupScreen(onFinished: () -> Unit) {
    val packs by OfflinePacks.packs.collectAsState()
    val busy by OfflinePacks.busy.collectAsState()
    var selected by remember {
        mutableStateOf(setOf(OfflinePackId.QURAN, OfflinePackId.HADITH))
    }
    var awaitingDownload by remember { mutableStateOf(false) }

    LaunchedEffect(busy, awaitingDownload, packs) {
        if (!awaitingDownload || busy) return@LaunchedEffect
        val failed = packs.any { it.status == OfflinePackStatus.FAILED }
        val pending = selected.any { id ->
            packs.none { it.id == id && it.status == OfflinePackStatus.READY }
        }
        if (!failed && !pending) {
            OfflinePacks.markSetupDone()
            onFinished()
        } else if (failed) {
            awaitingDownload = false
        }
    }

    fun finish() {
        OfflinePacks.markSetupDone()
        onFinished()
    }

    ScreenScaffold(title = "Offline libraries") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Save libraries on this phone",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            MutedText(
                "Hisn al-Muslim, Sahaba stories and scholar quotes are already in the app. " +
                    "Download the rest once — then Qur’an and Hadith work without internet."
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(packs, key = { it.id.name }) { pack ->
                    PackChoiceRow(
                        pack = pack,
                        checked = pack.id in selected || pack.status == OfflinePackStatus.READY,
                        enabled = !busy && pack.status != OfflinePackStatus.READY,
                        onToggle = {
                            selected = if (pack.id in selected) selected - pack.id else selected + pack.id
                        }
                    )
                }
                item {
                    ContentCard {
                        Text("Already included", fontWeight = FontWeight.SemiBold)
                        MutedText(
                            "Hisn al-Muslim · Sahaba · Scholars — no download needed.",
                            Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (busy) {
                val active = packs.firstOrNull { it.status == OfflinePackStatus.DOWNLOADING }
                if (active != null) {
                    MutedText("${active.id.label}: ${active.detail.ifBlank { "Downloading…" }}")
                    LinearProgressIndicator(
                        progress = { active.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(
                    onClick = {
                        OfflinePacks.cancel()
                        awaitingDownload = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel") }
            } else {
                Button(
                    onClick = {
                        val toFetch = selected.filter { id ->
                            packs.none { it.id == id && it.status == OfflinePackStatus.READY }
                        }
                        if (toFetch.isEmpty()) {
                            finish()
                        } else {
                            awaitingDownload = true
                            OfflinePacks.download(toFetch)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selected.isEmpty()) "Continue without downloads" else "Download selected")
                }
                TextButton(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Skip for now") }
            }
        }
    }
}

@Composable
fun OfflineDataScreen(onBack: () -> Unit) {
    val packs by OfflinePacks.packs.collectAsState()
    val busy by OfflinePacks.busy.collectAsState()

    ScreenScaffold(title = "Offline data", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("On this device") }
            item {
                MutedText(
                    "Download packs for offline use, or remove them to free space. " +
                        "Hisn al-Muslim and library texts stay in the app."
                )
            }
            items(packs, key = { it.id.name }) { pack ->
                PackManageCard(pack = pack, busy = busy)
            }
            if (busy) {
                item {
                    OutlinedButton(
                        onClick = { OfflinePacks.cancel() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel download") }
                }
            }
        }
    }
}

@Composable
private fun PackChoiceRow(
    pack: OfflinePackState,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ContentCard(onClick = { if (enabled) onToggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(pack.id.label, fontWeight = FontWeight.SemiBold)
                MutedText(pack.id.blurb, Modifier.padding(top = 2.dp))
                MutedText(pack.id.sizeHint + statusSuffix(pack), Modifier.padding(top = 4.dp))
            }
        }
        if (pack.status == OfflinePackStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { pack.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            if (pack.detail.isNotBlank()) MutedText(pack.detail, Modifier.padding(top = 4.dp))
        }
        if (pack.status == OfflinePackStatus.FAILED && pack.error != null) {
            Text(pack.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PackManageCard(pack: OfflinePackState, busy: Boolean) {
    ContentCard {
        Text(pack.id.label, fontWeight = FontWeight.SemiBold)
        MutedText(pack.id.blurb, Modifier.padding(top = 2.dp))
        MutedText(pack.id.sizeHint + statusSuffix(pack), Modifier.padding(top = 4.dp))
        when (pack.status) {
            OfflinePackStatus.DOWNLOADING -> {
                LinearProgressIndicator(
                    progress = { pack.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
                MutedText(
                    pack.detail.ifBlank { "${(pack.progress * 100).roundToInt()}%" },
                    Modifier.padding(top = 4.dp)
                )
            }
            OfflinePackStatus.READY -> {
                OutlinedButton(
                    onClick = { OfflinePacks.remove(pack.id) },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Remove from phone") }
            }
            OfflinePackStatus.NOT_DOWNLOADED, OfflinePackStatus.FAILED -> {
                if (pack.error != null) {
                    Text(pack.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
                Button(
                    onClick = { OfflinePacks.download(listOf(pack.id)) },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Download") }
            }
        }
    }
}

private fun statusSuffix(pack: OfflinePackState): String = when (pack.status) {
    OfflinePackStatus.READY -> " · Ready"
    OfflinePackStatus.DOWNLOADING -> " · Downloading"
    OfflinePackStatus.FAILED -> " · Failed"
    OfflinePackStatus.NOT_DOWNLOADED -> ""
}
