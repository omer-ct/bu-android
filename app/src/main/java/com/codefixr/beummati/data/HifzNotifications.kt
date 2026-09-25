package com.codefixr.beummati.data

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codefixr.beummati.MainActivity
import com.codefixr.beummati.data.DeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily reminder when Hifz ayahs are due for review.
 */
object HifzNotifications {
    const val CHANNEL_ID = "beummati.hifz.review"
    internal const val ACTION_HIFZ = "com.codefixr.beummati.HIFZ_REMINDER"
    private const val PREFS = "beummati.hifzNotify"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val REQUEST_CODE = 5100
    private const val NOTIFICATION_ID = 5100

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _hour = MutableStateFlow(9)
    val hour: StateFlow<Int> = _hour.asStateFlow()

    private val _minute = MutableStateFlow(0)
    val minute: StateFlow<Int> = _minute.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _hour.value = prefs.getInt(KEY_HOUR, 9)
        _minute.value = prefs.getInt(KEY_MINUTE, 0)
        createChannel(app)
    }

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        reschedule(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        init(context)
        _hour.value = hour.coerceIn(0, 23)
        _minute.value = minute.coerceIn(0, 59)
        prefs.edit()
            .putInt(KEY_HOUR, _hour.value)
            .putInt(KEY_MINUTE, _minute.value)
            .apply()
        reschedule(context)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun reschedule(context: Context) {
        init(context)
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        cancel(app, alarms)
        if (!_enabled.value || !hasNotificationPermission(app)) return

        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(_hour.value, _minute.value))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, HifzAlarmReceiver::class.java).setAction(ACTION_HIFZ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun post(context: Context) {
        init(context)
        HifzStore.init(context)
        val due = HifzStore.stats.value.dueTodayCount
        if (due <= 0) {
            reschedule(context)
            return
        }
        if (!hasNotificationPermission(context)) return
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            DeepLinks.putRoute(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                "hifz"
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Hifz review")
            .setContentText(
                if (due == 1) "1 ayah is due today" else "$due ayahs are due today"
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        reschedule(context)
    }

    private fun cancel(app: Context, alarms: AlarmManager) {
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, HifzAlarmReceiver::class.java).setAction(ACTION_HIFZ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarms.cancel(pending)
    }

    private fun createChannel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hifz review",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Daily reminder for ayahs due for review" }
        runCatching { manager.createNotificationChannel(channel) }
    }
}

class HifzAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HifzNotifications.ACTION_HIFZ) return
        HifzNotifications.post(context)
    }
}
