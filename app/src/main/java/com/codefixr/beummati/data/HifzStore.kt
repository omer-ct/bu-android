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

/** Spaced-repetition intervals after a successful review (days). */
private val REVIEW_INTERVALS = intArrayOf(1, 3, 7, 14, 30)

@Serializable
data class AyahReviewEntry(
    val stage: Int = 0,
    /** ISO date when this ayah is due next; empty means not scheduled. */
    val nextDue: String = ""
)

@Serializable
data class HifzSnapshot(
    val memorized: Set<String> = emptySet(),
    val weak: Set<String> = emptySet(),
    val reviews: Map<String, AyahReviewEntry> = emptyMap(),
    /** ISO dates on which at least one successful review or new mark happened. */
    val activityDays: Set<String> = emptySet(),
    val dailyGoal: Int = 5,
    /** How many ayahs newly marked memorized today (ISO date → count). */
    val markedToday: Map<String, Int> = emptyMap()
)

data class HifzStats(
    val memorizedCount: Int,
    val weakCount: Int,
    val dueTodayCount: Int,
    val reviewedThisWeek: Int = 0,
    val streakDays: Int = 0,
    val dailyGoal: Int = 5,
    val markedTodayCount: Int = 0
)

enum class HifzQuizMode(val label: String, val blurb: String) {
    HIDE_ARABIC("Hide Arabic", "Recall the ayah, then reveal"),
    AUDIO_ONLY("Audio only", "Listen, then reveal text"),
    SHOW_ARABIC("Show Arabic", "Translation recall (classic)")
}

/** Memorization progress with gentle spaced repetition, goals, and streaks. */
object HifzStore {
    private const val PREFS = "beummati.hifz"
    private const val KEY_SNAPSHOT = "snapshot.v1"
    private const val KEY_QUIZ_MODE = "quizMode"
    private const val KEY_FIRST_HINT_SHOWN = "firstHintShown"

    private lateinit var prefs: SharedPreferences

    private val _memorized = MutableStateFlow<Set<String>>(emptySet())
    val memorized: StateFlow<Set<String>> = _memorized.asStateFlow()

    private val _weak = MutableStateFlow<Set<String>>(emptySet())
    val weakKeys: StateFlow<Set<String>> = _weak.asStateFlow()

    private val _dueToday = MutableStateFlow<List<String>>(emptyList())
    val dueToday: StateFlow<List<String>> = _dueToday.asStateFlow()

    private val _stats = MutableStateFlow(HifzStats(0, 0, 0))
    val stats: StateFlow<HifzStats> = _stats.asStateFlow()

    private val _quizMode = MutableStateFlow(HifzQuizMode.HIDE_ARABIC)
    val quizMode: StateFlow<HifzQuizMode> = _quizMode.asStateFlow()

    private var reviews: Map<String, AyahReviewEntry> = emptyMap()
    private var activityDays: Set<String> = emptySet()
    private var dailyGoal: Int = 5
    private var markedToday: Map<String, Int> = emptyMap()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadFromPrefs()
        _quizMode.value = HifzQuizMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_QUIZ_MODE, null) }
            ?: HifzQuizMode.HIDE_ARABIC
    }

    /** Re-read prefs after an external restore (e.g. [BackupStore.import]). */
    fun reload() {
        if (!::prefs.isInitialized) return
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_SNAPSHOT, null)
        val snapshot = raw?.let { runCatching { Catalogs.json.decodeFromString<HifzSnapshot>(it) }.getOrNull() }
            ?: HifzSnapshot()
        applySnapshot(snapshot)
    }

    private fun applySnapshot(snapshot: HifzSnapshot) {
        _memorized.value = snapshot.memorized
        _weak.value = snapshot.weak
        reviews = snapshot.reviews
        activityDays = snapshot.activityDays
        dailyGoal = snapshot.dailyGoal.coerceIn(1, 50)
        markedToday = snapshot.markedToday
        refreshDerived()
    }

    fun exportSnapshotJson(): String =
        Catalogs.json.encodeToString(currentSnapshot())

    fun importSnapshotJson(json: String) {
        val snapshot = Catalogs.json.decodeFromString<HifzSnapshot>(json)
        applySnapshot(snapshot)
        persist()
    }

    fun setQuizMode(mode: HifzQuizMode) {
        _quizMode.value = mode
        prefs.edit().putString(KEY_QUIZ_MODE, mode.name).apply()
    }

    fun setDailyGoal(goal: Int) {
        dailyGoal = goal.coerceIn(1, 50)
        persist()
        refreshDerived()
    }

    fun markMemorized(key: String) {
        if (key.isBlank()) return
        val wasNew = key !in _memorized.value
        _memorized.value = _memorized.value + key
        _weak.value = _weak.value - key
        val today = LocalDate.now()
        reviews = reviews.toMutableMap().apply {
            put(key, AyahReviewEntry(stage = 0, nextDue = today.plusDays(REVIEW_INTERVALS[0].toLong()).toString()))
        }
        if (wasNew) bumpMarkedToday(today)
        noteActivity(today)
        persist()
        refreshDerived()
    }

    fun unmark(key: String) {
        if (key.isBlank()) return
        _memorized.value = _memorized.value - key
        _weak.value = _weak.value - key
        reviews = reviews.toMutableMap().apply { remove(key) }
        persist()
        refreshDerived()
    }

    /** Keep memorized, but flag for more frequent review. */
    fun flagWeak(key: String) {
        if (key.isBlank() || key !in _memorized.value) return
        _weak.value = _weak.value + key
        val today = LocalDate.now()
        reviews = reviews.toMutableMap().apply {
            put(key, AyahReviewEntry(stage = 0, nextDue = today.plusDays(1).toString()))
        }
        persist()
        refreshDerived()
    }

    /** Removes from memorized list (legacy name used by old UI). */
    fun markWeak(key: String) = unmark(key)

    fun markSurahMemorized(surah: Int, ayahCount: Int) {
        if (surah !in 1..114 || ayahCount <= 0) return
        val today = LocalDate.now()
        val next = today.plusDays(REVIEW_INTERVALS[0].toLong()).toString()
        val keys = (1..ayahCount).map { "$surah:$it" }
        var added = 0
        val mem = _memorized.value.toMutableSet()
        val rev = reviews.toMutableMap()
        keys.forEach { key ->
            if (key !in mem) {
                mem += key
                added++
            }
            rev[key] = AyahReviewEntry(stage = 0, nextDue = next)
        }
        _memorized.value = mem
        _weak.value = _weak.value - keys.toSet()
        reviews = rev
        if (added > 0) {
            val day = today.toString()
            markedToday = markedToday.toMutableMap().apply {
                put(day, (get(day) ?: 0) + added)
            }
            noteActivity(today)
        }
        persist()
        refreshDerived()
    }

    fun unmarkSurah(surah: Int) {
        val prefix = "$surah:"
        val drop = _memorized.value.filter { it.startsWith(prefix) }.toSet()
        if (drop.isEmpty()) return
        _memorized.value = _memorized.value - drop
        _weak.value = _weak.value - drop
        reviews = reviews.filterKeys { it !in drop }
        persist()
        refreshDerived()
    }

    fun recordReview(key: String, remembered: Boolean, today: LocalDate = LocalDate.now()) {
        if (key.isBlank()) return
        if (!_memorized.value.contains(key)) {
            if (remembered) markMemorized(key)
            return
        }
        val current = reviews[key] ?: AyahReviewEntry()
        val next = if (remembered) {
            _weak.value = _weak.value - key
            val stage = (current.stage + 1).coerceAtMost(REVIEW_INTERVALS.lastIndex)
            val days = REVIEW_INTERVALS[stage]
            AyahReviewEntry(stage = stage, nextDue = today.plusDays(days.toLong()).toString())
        } else {
            _weak.value = _weak.value + key
            AyahReviewEntry(stage = 0, nextDue = today.plusDays(1).toString())
        }
        reviews = reviews.toMutableMap().apply { put(key, next) }
        noteActivity(today)
        persist()
        refreshDerived()
    }

    fun dueForReview(today: LocalDate = LocalDate.now()): List<String> {
        val due = linkedSetOf<String>()
        _weak.value.filter { it in _memorized.value }.forEach { due += it }
        for (key in _memorized.value) {
            val entry = reviews[key]
            val dueDate = entry?.nextDue?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (dueDate == null || !dueDate.isAfter(today)) due += key
        }
        return due.sortedWith(
            compareBy(
                { it.substringBefore(':').toIntOrNull() ?: 0 },
                { it.substringAfter(':').toIntOrNull() ?: 0 }
            )
        )
    }

    fun isWeak(key: String): Boolean = key in _weak.value
    fun isMemorized(key: String): Boolean = key in _memorized.value

    fun progressForSurah(number: Int, ayahCount: Int): Float {
        if (ayahCount <= 0) return 0f
        return memorizedInSurah(number).toFloat() / ayahCount.toFloat()
    }

    fun memorizedInSurah(number: Int): Int =
        _memorized.value.count { it.startsWith("$number:") }

    /** True the first time the user marks an ayah — show a one-shot “open Hifz” tip. */
    fun consumeFirstMemorizeHint(): Boolean {
        if (prefs.getBoolean(KEY_FIRST_HINT_SHOWN, false)) return false
        prefs.edit().putBoolean(KEY_FIRST_HINT_SHOWN, true).apply()
        return true
    }

    private fun bumpMarkedToday(today: LocalDate) {
        val day = today.toString()
        markedToday = markedToday.toMutableMap().apply {
            put(day, (get(day) ?: 0) + 1)
        }
    }

    private fun noteActivity(today: LocalDate) {
        activityDays = activityDays + today.toString()
    }

    private fun streakDays(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var cursor = today
        // Empty today does not break the streak yet.
        if (today.toString() !in activityDays) cursor = today.minusDays(1)
        while (cursor.toString() in activityDays) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun reviewedThisWeek(today: LocalDate = LocalDate.now()): Int {
        val start = today.minusDays(6)
        return activityDays.count { day ->
            runCatching {
                val d = LocalDate.parse(day)
                !d.isBefore(start) && !d.isAfter(today)
            }.getOrDefault(false)
        }
    }

    private fun refreshDerived() {
        val today = LocalDate.now()
        val due = dueForReview(today)
        _dueToday.value = due
        _stats.value = HifzStats(
            memorizedCount = _memorized.value.size,
            weakCount = _weak.value.size,
            dueTodayCount = due.size,
            reviewedThisWeek = reviewedThisWeek(today),
            streakDays = streakDays(today),
            dailyGoal = dailyGoal,
            markedTodayCount = markedToday[today.toString()] ?: 0
        )
    }

    private fun currentSnapshot() = HifzSnapshot(
        memorized = _memorized.value,
        weak = _weak.value,
        reviews = reviews,
        activityDays = activityDays,
        dailyGoal = dailyGoal,
        markedToday = markedToday
    )

    private fun persist() {
        prefs.edit().putString(KEY_SNAPSHOT, Catalogs.json.encodeToString(currentSnapshot())).apply()
    }
}
