package com.codefixr.beummati.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codefixr.beummati.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Local notifications for the five daily prayers — port of the iOS `PrayerNotifications`:
 * a ping at each prayer plus an optional heads-up a few minutes before.
 *
 * Every alarm targets the *next* occurrence of its prayer, so an alarm that fires re-arms
 * itself for tomorrow. That keeps the schedule alive on days without network, where the
 * cached times are only a minute or two off.
 */
object PrayerNotifications {
    const val CHANNEL_ID = "beummati.prayer.times"

    /** Minutes before a prayer for the heads-up ping; 0 turns it off. */
    val PRE_MINUTE_OPTIONS = listOf(0, 5, 10, 15, 20, 30)

    internal const val ACTION_PRAYER = "com.codefixr.beummati.PRAYER_ALARM"
    internal const val EXTRA_PRAYER = "prayer"
    internal const val EXTRA_TIME = "time"
    internal const val EXTRA_MINUTES_BEFORE = "minutesBefore"

    private const val TAG = "PrayerNotifications"
    private const val PREFS = "beummati.prayerNotify"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRE = "preMinutes"
    private const val DEFAULT_PRE_MINUTES = 10
    private const val REQUEST_BASE = 4200
    private const val NOTIFICATION_BASE = 4200
    /** Slack given to the OS when we may only use inexact alarms. */
    private const val INEXACT_WINDOW_MILLIS = 5 * 60_000L

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _preMinutes = MutableStateFlow(DEFAULT_PRE_MINUTES)
    val preMinutes: StateFlow<Int> = _preMinutes.asStateFlow()

    private val _prayers = MutableStateFlow(SalahName.entries.toSet())
    val prayers: StateFlow<Set<SalahName>> = _prayers.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _preMinutes.value = prefs.getInt(KEY_PRE, DEFAULT_PRE_MINUTES)
        _prayers.value = SalahName.entries.filter { prefs.getBoolean(prayerKey(it), true) }.toSet()
        createChannel(app)
    }

    // region Preferences

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        reschedule(context)
    }

    fun setPrayerEnabled(context: Context, name: SalahName, value: Boolean) {
        init(context)
        _prayers.value = if (value) _prayers.value + name else _prayers.value - name
        prefs.edit().putBoolean(prayerKey(name), value).apply()
        reschedule(context)
    }

    fun setPreMinutes(context: Context, minutes: Int) {
        init(context)
        _preMinutes.value = minutes.coerceAtLeast(0)
        prefs.edit().putInt(KEY_PRE, _preMinutes.value).apply()
        reschedule(context)
    }

    // endregion

    // region Permissions

    fun hasNotificationPermission(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** False when the user revoked "alarms & reminders", which forces inexact alarms. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    fun statusText(context: Context): String = when {
        !hasNotificationPermission(context) -> "Blocked — allow notifications for Be Ummati"
        !_enabled.value -> "Off"
        !canScheduleExact(context) -> "On · exact alarms are off, pings may drift a few minutes"
        else -> "On · scheduled from today’s times"
    }

    // endregion

    // region Scheduling

    /**
     * Rearms every prayer alarm from [day]. Called whenever [PrayerService] refreshes, when a
     * toggle changes, after boot, and by each alarm as it fires.
     */
    fun reschedule(context: Context, day: PrayerDay? = PrayerService.day.value) {
        val app = context.applicationContext
        init(app)
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return

        SalahName.entries.indices.forEach { index ->
            cancel(app, alarms, requestCode(index, pre = false))
            cancel(app, alarms, requestCode(index, pre = true))
        }
        if (!_enabled.value || day == null || !hasNotificationPermission(app)) return

        val pre = _preMinutes.value
        val now = LocalDateTime.now()
        SalahName.entries.forEachIndexed { index, name ->
            if (name !in _prayers.value) return@forEachIndexed
            val raw = day.time(name)
            val at = nextOccurrence(raw, now) ?: return@forEachIndexed
            schedule(app, alarms, requestCode(index, pre = false), at, name, raw, minutesBefore = 0)
            if (pre > 0) {
                val heads = at.minusMinutes(pre.toLong())
                if (heads.isAfter(now)) {
                    schedule(app, alarms, requestCode(index, pre = true), heads, name, raw, minutesBefore = pre)
                }
            }
        }
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        SalahName.entries.indices.forEach { index ->
            cancel(app, alarms, requestCode(index, pre = false))
            cancel(app, alarms, requestCode(index, pre = true))
        }
        NotificationManagerCompat.from(app).let { manager ->
            SalahName.entries.indices.forEach { index ->
                manager.cancel(NOTIFICATION_BASE + index * 2)
                manager.cancel(NOTIFICATION_BASE + index * 2 + 1)
            }
        }
    }

    private fun schedule(
        app: Context,
        alarms: AlarmManager,
        requestCode: Int,
        at: LocalDateTime,
        name: SalahName,
        time: String,
        minutesBefore: Int
    ) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = alarmIntent(app).apply {
            putExtra(EXTRA_PRAYER, name.name)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        }
        val pending = PendingIntent.getBroadcast(
            app,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (canScheduleExact(app)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pending)
            }
        }.onFailure { Log.w(TAG, "Couldn’t arm the ${name.title} alarm", it) }
    }

    private fun cancel(app: Context, alarms: AlarmManager, requestCode: Int) {
        val pending = PendingIntent.getBroadcast(
            app,
            requestCode,
            alarmIntent(app),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun alarmIntent(app: Context): Intent =
        Intent(app, PrayerAlarmReceiver::class.java).setAction(ACTION_PRAYER)

    private fun requestCode(index: Int, pre: Boolean): Int = REQUEST_BASE + index * 2 + if (pre) 1 else 0

    private fun nextOccurrence(raw: String, from: LocalDateTime): LocalDateTime? {
        val time = parseTime(raw) ?: return null
        val today = LocalDateTime.of(from.toLocalDate(), time)
        return if (today.isAfter(from)) today else today.plusDays(1)
    }

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw.trim().take(5), timeFormat) }.getOrNull()

    private fun prayerKey(name: SalahName) = "prayer.${name.name.lowercase()}"

    // endregion

    // region Notifications

    internal fun post(context: Context, name: SalahName, time: String, minutesBefore: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val index = SalahName.entries.indexOf(name).coerceAtLeast(0)
        val title = if (minutesBefore > 0) "${name.title} in $minutesBefore min" else "${name.title} · $time"
        val body = if (minutesBefore > 0) {
            "Wrap up and get ready for ${name.title}."
        } else {
            "It’s time for ${name.title}."
        }
        val open = PendingIntent.getActivity(
            context,
            requestCode(index, pre = minutesBefore > 0),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            manager.notify(NOTIFICATION_BASE + index * 2 + if (minutesBefore > 0) 1 else 0, notification)
        }.onFailure { Log.w(TAG, "Couldn’t post the ${name.title} notification", it) }
    }

    private fun createChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer times",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for Fajr, Dhuhr, Asr, Maghrib and Isha"
            setShowBadge(false)
        }
        runCatching { manager.createNotificationChannel(channel) }
            .onFailure { Log.w(TAG, "Couldn’t create the prayer channel", it) }
    }

    // endregion
}

/** Fires at each prayer (and its heads-up), then rolls the schedule forward. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerNotifications.ACTION_PRAYER) return
        Catalogs.init(context)
        PrayerService.init(context)
        PrayerNotifications.init(context)

        val name = intent.getStringExtra(PrayerNotifications.EXTRA_PRAYER)
            ?.let { raw -> SalahName.entries.firstOrNull { it.name == raw } }
        if (name != null) {
            PrayerNotifications.post(
                context = context,
                name = name,
                time = intent.getStringExtra(PrayerNotifications.EXTRA_TIME).orEmpty(),
                minutesBefore = intent.getIntExtra(PrayerNotifications.EXTRA_MINUTES_BEFORE, 0)
            )
        }
        // Alarms point at the next occurrence, so this moves the one that just fired to tomorrow.
        PrayerNotifications.reschedule(context)
        // Best effort: pull fresh times for the new day. Cached times keep the alarms honest if it fails.
        PrayerService.refresh(context)
    }
}

/** Alarms are dropped on reboot, install and clock changes — put them back. */
class PrayerBootReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        Catalogs.init(context)
        PrayerService.init(context)
        PrayerNotifications.reschedule(context)
        HifzNotifications.init(context)
        HifzNotifications.reschedule(context)
    }
}
