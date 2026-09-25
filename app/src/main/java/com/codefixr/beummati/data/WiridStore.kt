package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate

@Serializable
data class WiridDayLog(
    val completedIds: Set<String> = emptySet()
)

@Serializable
data class WiridSnapshot(
    /** dayKey → completed dua ids */
    val days: Map<String, WiridDayLog> = emptyMap(),
    val lastStreakDay: String = ""
)

/** Daily Hisn al-Muslim checklist with a gentle completion streak. */
object WiridStore {
    private const val PREFS = "beummati.wirid"
    private const val KEY_SNAPSHOT = "snapshot.v1"
    private const val STREAK_LOOKBACK = 365

    private lateinit var prefs: SharedPreferences

    private val _checklist = MutableStateFlow<List<WiridItem>>(emptyList())
    val checklist: StateFlow<List<WiridItem>> = _checklist.asStateFlow()

    private val _todayCompleted = MutableStateFlow<Set<String>>(emptySet())
    val todayCompleted: StateFlow<Set<String>> = _todayCompleted.asStateFlow()

    private var days: Map<String, WiridDayLog> = emptyMap()

    data class WiridItem(
        val id: String,
        val title: String,
        val categoryId: Int
    )

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        days = prefs.getString(KEY_SNAPSHOT, null)
            ?.let { raw -> runCatching { Catalogs.json.decodeFromString<WiridSnapshot>(raw) }.getOrNull() }
            ?.days
            ?: emptyMap()
        _checklist.value = buildChecklist()
        refreshToday()
    }

    private fun buildChecklist(): List<WiridItem> {
        val hisn = Catalogs.hisnAlMuslim
        val shortcutIds = setOf("morning", "evening", "sleep", "waking")
        val items = mutableListOf<WiridItem>()
        for (shortcut in hisn.shortcuts.filter { it.id in shortcutIds }) {
            for (catId in shortcut.categoryIds) {
                val cat = Catalogs.duaCategory(catId) ?: continue
                for (dua in cat.duas) {
                    items += WiridItem(
                        id = dua.id,
                        title = dua.english.ifBlank { dua.transliteration.ifBlank { cat.titleEn } },
                        categoryId = catId
                    )
                }
            }
        }
        if (items.isEmpty()) {
            hisn.categories.take(3).forEach { cat ->
                cat.duas.take(5).forEach { dua ->
                    items += WiridItem(
                        id = dua.id,
                        title = dua.english.ifBlank { cat.titleEn },
                        categoryId = cat.id
                    )
                }
            }
        }
        return items.distinctBy { it.id }
    }

    fun dayKey(date: LocalDate = LocalDate.now()): String = date.toString()

    fun toggle(id: String, date: LocalDate = LocalDate.now()) {
        val key = dayKey(date)
        val log = days[key] ?: WiridDayLog()
        val next = if (id in log.completedIds) log.completedIds - id else log.completedIds + id
        days = days.toMutableMap().apply {
            if (next.isEmpty()) remove(key) else put(key, WiridDayLog(next))
        }
        persist()
        refreshToday(date)
    }

    fun todayCompleted(date: LocalDate = LocalDate.now()): Set<String> =
        days[dayKey(date)]?.completedIds ?: emptySet()

    /**
     * Consecutive days (including today) with at least one wirid checked off.
     * An empty today does not break the streak yet.
     */
    fun gentleStreak(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var date = today
        repeat(STREAK_LOOKBACK) {
            val done = days[dayKey(date)]?.completedIds?.isNotEmpty() == true
            when {
                done -> streak++
                date == today -> Unit
                else -> return streak
            }
            date = date.minusDays(1)
        }
        return streak
    }

    private fun refreshToday(date: LocalDate = LocalDate.now()) {
        _todayCompleted.value = todayCompleted(date)
    }

    private fun persist() {
        val snapshot = WiridSnapshot(days = days)
        prefs.edit().putString(KEY_SNAPSHOT, Catalogs.json.encodeToString(snapshot)).apply()
    }
}
