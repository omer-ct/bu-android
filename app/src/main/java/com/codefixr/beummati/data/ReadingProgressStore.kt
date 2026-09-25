package com.codefixr.beummati.data

import android.content.Context

/**
 * Persists the user's last Qur'an reading position (`beummati.reading` prefs).
 *
 * **Intended caller:** `SurahScreen` (and similar) when the visible ayah changes or on pause.
 *
 * ```kotlin
 * // Save while reading
 * ReadingProgressStore.save(context, surah = 2, ayah = 255)
 *
 * // Read for navigation / widgets
 * val last = ReadingProgressStore.load(context)
 * if (last != null) nav.navigate(Routes.surah(last.surah, last.ayah))
 * ```
 */
object ReadingProgressStore {
    private const val PREFS = "beummati.reading"
    private const val KEY_SURAH = "lastSurah"
    private const val KEY_AYAH = "lastAyah"

    data class LastReading(
        val surah: Int,
        val ayah: Int,
    )

    fun save(context: Context, surah: Int, ayah: Int) {
        if (surah !in 1..114 || ayah < 1) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SURAH, surah)
            .putInt(KEY_AYAH, ayah)
            .apply()
    }

    fun load(context: Context): LastReading? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SURAH) || !prefs.contains(KEY_AYAH)) return null
        val surah = prefs.getInt(KEY_SURAH, -1)
        val ayah = prefs.getInt(KEY_AYAH, -1)
        if (surah !in 1..114 || ayah < 1) return null
        return LastReading(surah, ayah)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
