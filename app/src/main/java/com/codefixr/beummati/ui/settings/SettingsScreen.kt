@file:OptIn(ExperimentalMaterial3Api::class)

package com.codefixr.beummati.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codefixr.beummati.data.AppAppearance
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.PrayerNotifications
import com.codefixr.beummati.data.SalahName
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import kotlin.math.roundToInt

private const val SAMPLE_ARABIC = "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appearance by SettingsStore.appearance.collectAsState()
    val arabicSize by SettingsStore.arabicFontSize.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()
    val showTransliteration by SettingsStore.showTransliteration.collectAsState()

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Prayer notifications") }
            item { PrayerNotificationCard(context) }

            item { SectionHeader("Theme") }
            item {
                ContentCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppAppearance.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { SettingsStore.setAppearance(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppAppearance.entries.size),
                                label = { Text(option.label) }
                            )
                        }
                    }
                    MutedText("Light and dark keep the same parchment and brass palette.", Modifier.padding(top = 8.dp))
                }
            }

            item { SectionHeader("Reading") }
            item {
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Arabic size", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${arabicSize.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = arabicSize,
                        onValueChange = { SettingsStore.setArabicFontSize(it) },
                        valueRange = SettingsStore.MIN_ARABIC_SIZE..SettingsStore.MAX_ARABIC_SIZE,
                        steps = 7
                    )
                    RtlText(SAMPLE_ARABIC, fontSize = 24)
                    Spacer(Modifier.padding(top = 8.dp))
                    ToggleRow(
                        title = "Show Urdu",
                        subtitle = "Urdu translations in hadith, stories and reminders",
                        checked = showUrdu,
                        onChange = { SettingsStore.setShowUrdu(it) }
                    )
                    ToggleRow(
                        title = "Show transliteration",
                        subtitle = "Latin transliteration under duas",
                        checked = showTransliteration,
                        onChange = { SettingsStore.setShowTransliteration(it) }
                    )
                    TextButton(onClick = { SettingsStore.resetReading() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Reset reading settings")
                    }
                }
            }

            item { SectionHeader("Content") }
            item {
                ContentCard {
                    Text("Offline packs", fontWeight = FontWeight.Medium)
                    MutedText(
                        if (Catalogs.hasOfflineTafsir) {
                            "Urdu Tafsir Ibn Kathir is bundled — surahs read it from assets with no network."
                        } else {
                            "Urdu Tafsir loads from the network and is cached per surah."
                        }
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("Attribution", fontWeight = FontWeight.Medium)
                    MutedText(Catalogs.audio.attribution.orEmpty())
                    MutedText(Catalogs.hisnAlMuslim.source.orEmpty())
                }
            }
        }
    }
}

/**
 * Master switch, per-prayer switches and the heads-up window. Enabling asks for
 * POST_NOTIFICATIONS first; [PrayerNotifications] arms the alarms from the cached day.
 */
@Composable
private fun PrayerNotificationCard(context: Context) {
    val enabled by PrayerNotifications.enabled.collectAsState()
    val prayers by PrayerNotifications.prayers.collectAsState()
    val preMinutes by PrayerNotifications.preMinutes.collectAsState()
    // Notification and exact-alarm grants change outside the app, so re-read them on resume.
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val status = remember(enabled, permissionTick) { PrayerNotifications.statusText(context) }
    val canScheduleExact = remember(permissionTick) { PrayerNotifications.canScheduleExact(context) }

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionTick++
        PrayerNotifications.setEnabled(context, granted)
    }

    ContentCard {
        ToggleRow(
            title = "Notify me at prayer times",
            subtitle = status,
            checked = enabled,
            onChange = { wanted ->
                if (wanted && !PrayerNotifications.hasNotificationPermission(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                } else {
                    PrayerNotifications.setEnabled(context, wanted)
                }
            }
        )

        if (enabled) {
            SalahName.entries.forEach { name ->
                ToggleRow(
                    title = name.title,
                    subtitle = "",
                    checked = name in prayers,
                    onChange = { PrayerNotifications.setPrayerEnabled(context, name, it) }
                )
            }

            Text("Heads-up before each prayer", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(PrayerNotifications.PRE_MINUTE_OPTIONS) { minutes ->
                    FilterChip(
                        selected = minutes == preMinutes,
                        onClick = { PrayerNotifications.setPreMinutes(context, minutes) },
                        label = { Text(if (minutes == 0) "Off" else "$minutes min") }
                    )
                }
            }

            if (!canScheduleExact) {
                MutedText(
                    "Android is holding these to inexact alarms, so a ping can land a few minutes late.",
                    Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = {
                    openExactAlarmSettings(context)
                    permissionTick++
                }) {
                    Text("Allow exact alarms")
                }
            }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) MutedText(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
