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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codefixr.beummati.data.AppAppearance
import com.codefixr.beummati.data.AppThemeKind
import com.codefixr.beummati.data.AsrSchool
import com.codefixr.beummati.data.BackupStore
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.HifzNotifications
import com.codefixr.beummati.data.HifzStore
import com.codefixr.beummati.data.PlayerSkin
import com.codefixr.beummati.data.PrayerCalcMethod
import com.codefixr.beummati.data.PrayerNotifications
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.SalahName
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.ShareTemplate
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.ui.ArabicScriptText
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.RtlText
import com.codefixr.beummati.ui.ScreenScaffold
import com.codefixr.beummati.ui.SectionHeader
import com.codefixr.beummati.ui.ShareTemplateTile
import com.codefixr.beummati.ui.player.skinColors
import com.codefixr.beummati.ui.theme.palette
import kotlin.math.roundToInt

private const val SAMPLE_ARABIC = "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenOffline: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenDhikr: () -> Unit = {}
) {
    val context = LocalContext.current
    val appearance by SettingsStore.appearance.collectAsState()
    val themeKind by SettingsStore.themeKind.collectAsState()
    val arabicSize by SettingsStore.arabicFontSize.collectAsState()
    val arabicFont by SettingsStore.arabicFont.collectAsState()
    val readingLanguage by SettingsStore.readingLanguage.collectAsState()
    val showUrdu by SettingsStore.showUrdu.collectAsState()
    val showTransliteration by SettingsStore.showTransliteration.collectAsState()
    val playerSkin by SettingsStore.playerSkin.collectAsState()
    val shareTemplate by SettingsStore.shareTemplate.collectAsState()
    val defaultRate by SettingsStore.defaultRate.collectAsState()

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Appearance") }
            item {
                ContentCard {
                    Text("Mode", fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        AppAppearance.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = appearance == option,
                                onClick = { SettingsStore.setAppearance(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppAppearance.entries.size),
                                label = { Text(option.label) }
                            )
                        }
                    }
                    MutedText(
                        "System follows your phone. Light themes switch to Midnight when Dark is on.",
                        Modifier.padding(top = 8.dp)
                    )
                }
            }
            item { SectionHeader("Theme") }
            item {
                ContentCard {
                    MutedText("Colour palettes for the whole app — same set as iOS.")
                    AppThemeKind.entries.forEach { kind ->
                        ThemeKindRow(
                            kind = kind,
                            selected = kind == themeKind,
                            onClick = { SettingsStore.setThemeKind(kind) }
                        )
                    }
                }
            }

            item { SectionHeader("Player skin") }
            item {
                ContentCard {
                    MutedText("The lecture player and the mini bar keep this palette whatever the app theme is.")
                    PlayerSkin.entries.forEach { skin ->
                        PlayerSkinRow(
                            skin = skin,
                            selected = skin == playerSkin,
                            onClick = { SettingsStore.setPlayerSkin(skin) }
                        )
                    }
                }
            }

            item { SectionHeader("Playback") }
            item {
                ContentCard {
                    Text("Default speed", fontWeight = FontWeight.Medium)
                    MutedText("Applied now and to lectures you open later")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(SettingsStore.RATE_OPTIONS) { rate ->
                            FilterChip(
                                selected = rate == defaultRate,
                                onClick = {
                                    SettingsStore.setDefaultRate(rate)
                                    LecturePlayerSession.setRate(rate)
                                },
                                label = { Text(formatRate(rate)) }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Share design") }
            item {
                ContentCard {
                    Text(shareTemplate.label, fontWeight = FontWeight.Medium)
                    MutedText("Used when you share a card as an image. You can still pick a one-off design from any share menu.")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        items(ShareTemplate.entries, key = { it.name }) { template ->
                            ShareTemplateTile(
                                template = template,
                                selected = template == shareTemplate,
                                onClick = { SettingsStore.setShareTemplate(template) }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Prayer notifications") }
            item { PrayerNotificationCard(context) }

            item { SectionHeader("Prayer times") }
            item { PrayerTimesCard(context) }

            item { SectionHeader("Reminders & prayer") }
            item {
                ContentCard(onClick = onOpenCalendar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Islamic calendar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MutedText("Hijri dates for this Gregorian month")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            item { SectionHeader("Practice") }
            item {
                ContentCard(onClick = onOpenDhikr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dhikr counter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MutedText("سبحان الله · الحمد لله · الله أكبر · لا إله إلا الله")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            item { SectionHeader("Hifz reminders") }
            item { HifzReminderCard(context) }

            item { SectionHeader("Backup") }
            item { BackupCard(context) }

            item { SectionHeader("Reading") }
            item {
                ContentCard(onClick = onOpenReading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MutedText("Languages, translations, fonts, sizes and alignment")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                    MutedText(
                        listOf(
                            readingLanguage.label,
                            "${arabicSize.roundToInt()} pt Arabic",
                            arabicFont.label
                        ).joinToString(" · "),
                        Modifier.padding(top = 8.dp)
                    )
                    ArabicScriptText(SAMPLE_ARABIC, Modifier.padding(top = 8.dp))
                }
            }
            item {
                ContentCard(onClick = onOpenOffline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Offline data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MutedText("Download Qur’an, tafsīr & hadith for use without internet")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item {
                ContentCard {
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

            item { SectionHeader("About") }
            item {
                val version = remember(context) {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull().orEmpty()
                }
                ContentCard {
                    Text("Be Ummati", fontWeight = FontWeight.Medium)
                    MutedText("Version $version")
                    MutedText("Soldier of Allah · @beummati", Modifier.padding(top = 4.dp))
                }
            }
            item {
                ContentCard(onClick = onOpenPrivacy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            MutedText("How Be Ummati handles your data")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeKindRow(kind: AppThemeKind, selected: Boolean, onClick: () -> Unit) {
    val palette = kind.palette()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.parchment),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(palette.teal))
                Box(Modifier.size(12.dp).clip(CircleShape).background(palette.brass))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(kind.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            MutedText(kind.blurb)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun PlayerSkinRow(skin: PlayerSkin, selected: Boolean, onClick: () -> Unit) {
    val colors = skinColors(skin)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(colors.accent))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(skin.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            MutedText(skin.blurb)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun formatRate(rate: Float): String =
    (if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString().trimEnd('0')) + "×"

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

/** Hifz progress, bookmarks and notes as one JSON file the reader can keep or move phones with. */
@Composable
private fun BackupCard(context: Context) {
    var message by remember { mutableStateOf("") }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val summary = uri
            ?.let { BackupStore.readText(context, it) }
            ?.let { BackupStore.import(context, it) }
        message = when {
            uri == null -> ""
            summary == null -> "That file isn’t a Be Ummati backup."
            summary.hifzRestored ->
                "Restored ${summary.bookmarks} bookmarks, ${summary.notes} notes, and Hifz progress."
            else -> "Restored ${summary.bookmarks} bookmarks and ${summary.notes} notes."
        }
    }

    ContentCard {
        Text("Backup & restore", fontWeight = FontWeight.Medium)
        MutedText("One JSON file with your hifz progress, bookmarks and notes. Restoring replaces what is on this phone.")
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val file = BackupStore.exportAndShare(context)
                message = if (file == null) "Couldn’t write the backup." else "Exported ${file.name}"
            }) {
                Text("Export")
            }
            TextButton(onClick = { pickBackup.launch("*/*") }) { Text("Restore") }
        }
        if (message.isNotBlank()) MutedText(message)
    }
}

/**
 * Calculation authority, Asr school and an optional pinned city. Each change refetches
 * today's times from AlAdhan, so the card also shows [PrayerService.status].
 */
@Composable
private fun PrayerTimesCard(context: Context) {
    val method by PrayerService.method.collectAsState()
    val school by PrayerService.asrSchool.collectAsState()
    val city by PrayerService.cityName.collectAsState()
    val status by PrayerService.status.collectAsState()
    var methodOpen by remember { mutableStateOf(false) }

    ContentCard {
        MutedText(status)

        ExposedDropdownMenuBox(
            expanded = methodOpen,
            onExpandedChange = { methodOpen = it },
            modifier = Modifier.padding(top = 10.dp)
        ) {
            OutlinedTextField(
                value = method.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Calculation method") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodOpen) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = methodOpen, onDismissRequest = { methodOpen = false }) {
                PrayerCalcMethod.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            PrayerService.setMethod(context, option)
                            methodOpen = false
                        }
                    )
                }
            }
        }

        Text("Asr", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            AsrSchool.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = school == option,
                    onClick = { PrayerService.setAsrSchool(context, option) },
                    shape = SegmentedButtonDefaults.itemShape(index, AsrSchool.entries.size),
                    label = { Text(option.label) }
                )
            }
        }
        MutedText("Hanafi puts Asr later, at twice the shadow length.", Modifier.padding(top = 6.dp))

        Text("Location", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            item {
                FilterChip(
                    selected = city.isBlank(),
                    onClick = { PrayerService.clearManualCity(context) },
                    label = { Text("Use GPS") }
                )
            }
            items(PrayerService.CITY_PRESETS, key = { it.name }) { preset ->
                FilterChip(
                    selected = city == preset.name,
                    onClick = {
                        PrayerService.setManualCity(context, preset.name, preset.latitude, preset.longitude)
                    },
                    label = { Text(preset.name) }
                )
            }
        }
    }
}

@Composable
private fun HifzReminderCard(context: Context) {
    val enabled by HifzNotifications.enabled.collectAsState()
    val hour by HifzNotifications.hour.collectAsState()
    val minute by HifzNotifications.minute.collectAsState()
    val due by HifzStore.stats.collectAsState()
    var permissionTick by remember { mutableIntStateOf(0) }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionTick++
        HifzNotifications.setEnabled(context, granted)
    }
    ContentCard {
        ToggleRow(
            title = "Daily Hifz reminder",
            subtitle = if (due.dueTodayCount > 0) {
                "${due.dueTodayCount} due today · ${"%02d:%02d".format(hour, minute)}"
            } else {
                "Ping when ayahs are due · ${"%02d:%02d".format(hour, minute)}"
            },
            checked = enabled,
            onChange = { wanted ->
                if (wanted && !HifzNotifications.hasNotificationPermission(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                } else {
                    HifzNotifications.setEnabled(context, wanted)
                }
            }
        )
        if (enabled) {
            Text("Reminder time", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(listOf(6 to 0, 8 to 0, 9 to 0, 12 to 0, 18 to 0, 21 to 0)) { (h, m) ->
                    FilterChip(
                        selected = hour == h && minute == m,
                        onClick = { HifzNotifications.setTime(context, h, m) },
                        label = { Text("%02d:%02d".format(h, m)) }
                    )
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
