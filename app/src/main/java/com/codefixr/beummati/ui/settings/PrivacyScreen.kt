package com.codefixr.beummati.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Privacy", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Be Ummati is built to stay on your device.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                ContentCard {
                    SectionHeader("What we store on this phone")
                    MutedText(
                        "Reading progress, salah logs, hifz marks, wirid checklists, bookmarks, notes, " +
                            "theme preferences, and optional offline Qur’an / hadith / audio packs. " +
                            "These stay in app-private storage.",
                        Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                ContentCard {
                    SectionHeader("Network")
                    MutedText(
                        "When online, the app may fetch Qur’an text, tafsir, hadith, prayer times, " +
                            "and ayah audio (everyayah.com and related public APIs). " +
                            "Downloaded packs are cached for offline use.",
                        Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                ContentCard {
                    SectionHeader("Location")
                    MutedText(
                        "Precise location is used only for prayer times and qibla, and only with your permission. " +
                            "Coordinates are not uploaded to Be Ummati servers.",
                        Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                ContentCard {
                    SectionHeader("Accounts & ads")
                    MutedText(
                        "Be Ummati does not require an account and does not show third-party ads. " +
                            "We do not sell your personal data.",
                        Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                ContentCard {
                    SectionHeader("Sharing")
                    MutedText(
                        "Share cards and ayah videos are created on-device. You choose where to send them " +
                            "through the system share sheet.",
                        Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                MutedText(
                    "For questions: @beummati · Soldier of Allah. " +
                        "Replace this screen with your published privacy-policy URL before Play Store release if required."
                )
            }
        }
    }
}
