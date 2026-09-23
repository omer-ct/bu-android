package com.codefixr.beummati.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codefixr.beummati.data.CatalogRepository
import com.codefixr.beummati.data.LibrarySeries
import com.codefixr.beummati.player.LecturePlayer

@Composable
fun BeUmmatiApp() {
    val context = LocalContext.current
    val repo = remember { CatalogRepository(context) }
    val player = remember { LecturePlayer(context) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val nav = rememberNavController()
    val audioSeriesIds = remember {
        repo.audio.tracks.map { it.seriesId }.toSet()
    }
    val lectureSeries = remember {
        repo.library.series.filter { it.id in audioSeriesIds && it.chapters.isNotEmpty() }
    }

    Scaffold(
        bottomBar = { MiniPlayerBar(player) }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    series = lectureSeries,
                    attribution = repo.audio.attribution.orEmpty(),
                    onOpen = { nav.navigate("series/${it.id}") }
                )
            }
            composable(
                route = "series/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val series = lectureSeries.firstOrNull { it.id == id }
                if (series == null) {
                    Text("Series not found", modifier = Modifier.padding(24.dp))
                } else {
                    SeriesScreen(
                        series = series,
                        onBack = { nav.popBackStack() },
                        onPlay = { chapterId, title ->
                            val track = repo.trackFor(series.id, chapterId)
                            if (track != null) {
                                player.play(track.audioUrl, title, series.title)
                            }
                        },
                        hasAudio = { chapterId ->
                            repo.trackFor(series.id, chapterId) != null
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    series: List<LibrarySeries>,
    attribution: String,
    onOpen: (LibrarySeries) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Be Ummati", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Lectures",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Stream from archive.org · same catalogs as iOS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(8.dp))
            }
            items(series, key = { it.id }) { s ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(s) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!s.subtitle.isNullOrBlank()) {
                            Text(
                                s.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            "${s.chapters.count { true }} chapters",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (attribution.isNotBlank()) {
                item {
                    Text(
                        attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesScreen(
    series: LibrarySeries,
    onBack: () -> Unit,
    onPlay: (chapterId: String, title: String) -> Unit,
    hasAudio: (chapterId: String) -> Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(series.chapters, key = { it.id }) { ch ->
                val playable = hasAudio(ch.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = playable) { onPlay(ch.id, ch.title) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ch.title, fontWeight = FontWeight.Medium)
                        Text(
                            if (playable) "Tap to play" else "No audio mapped yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (playable) 0.55f else 0.35f
                            )
                        )
                    }
                    if (playable) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(player: LecturePlayer) {
    val playing by player.isPlaying.collectAsState()
    val title by player.title.collectAsState()
    val subtitle by player.subtitle.collectAsState()
    if (title.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = { player.skip(-10) }) {
            Icon(Icons.Default.Replay10, contentDescription = "Back 10s")
        }
        IconButton(onClick = { player.toggle() }) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause"
            )
        }
        IconButton(onClick = { player.skip(10) }) {
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
        }
    }
}
