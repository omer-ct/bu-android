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
import java.time.temporal.ChronoUnit

enum class ReadingPlanKind(val title: String, val blurb: String, val lengthDays: Int) {
    THIRTY_DAY_QURAN("30-day Qur’an", "About one juz per day through the mushaf.", 30),
    ONE_HADITH_DAY("One hadith a day", "Rotate through major collections.", Int.MAX_VALUE),
    RAMADAN("Ramadan khatm", "One juz each day for 30 days.", 30),
    HAJJ_FOCUS("Hajj focus", "Short duas and selected surahs.", 7)
}

data class ReadingPlanTask(
    val title: String,
    val subtitle: String,
    val destination: Destination
)

@Serializable
data class ActivePlanState(
    val kind: String,
    val startDate: String,
    val completedDays: Set<Int> = emptySet()
)

/** Guided reading plans with a daily task and deep link. */
object ReadingPlanStore {
    private const val PREFS = "beummati.reading_plans"
    private const val KEY_ACTIVE = "active.v1"

    private lateinit var prefs: SharedPreferences

    private val _active = MutableStateFlow<ActivePlanState?>(null)
    val active: StateFlow<ActivePlanState?> = _active.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _active.value = prefs.getString(KEY_ACTIVE, null)
            ?.let { raw -> runCatching { Catalogs.json.decodeFromString<ActivePlanState>(raw) }.getOrNull() }
    }

    fun start(kind: ReadingPlanKind, startDate: LocalDate = LocalDate.now()) {
        _active.value = ActivePlanState(kind = kind.name, startDate = startDate.toString())
        persist()
    }

    fun clear() {
        _active.value = null
        prefs.edit().remove(KEY_ACTIVE).apply()
    }

    fun currentDayIndex(today: LocalDate = LocalDate.now()): Int {
        val state = _active.value ?: return 0
        val start = runCatching { LocalDate.parse(state.startDate) }.getOrNull() ?: today
        return ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
    }

    fun todayTask(today: LocalDate = LocalDate.now()): ReadingPlanTask? {
        val state = _active.value ?: return null
        val kind = ReadingPlanKind.entries.firstOrNull { it.name == state.kind } ?: return null
        val day = currentDayIndex(today)
        return taskFor(kind, day)
    }

    fun markTodayDone(today: LocalDate = LocalDate.now()) {
        val state = _active.value ?: return
        val day = currentDayIndex(today)
        _active.value = state.copy(completedDays = state.completedDays + day)
        persist()
    }

    fun isTodayDone(today: LocalDate = LocalDate.now()): Boolean {
        val state = _active.value ?: return false
        return currentDayIndex(today) in state.completedDays
    }

    private fun taskFor(kind: ReadingPlanKind, day: Int): ReadingPlanTask = when (kind) {
        ReadingPlanKind.THIRTY_DAY_QURAN, ReadingPlanKind.RAMADAN -> {
            val juz = (day % 30) + 1
            val info = JuzCatalog.get(juz) ?: JuzCatalog.all.first()
            val (surah, ayah) = parseAyahKey(info.startKey)
            ReadingPlanTask(
                title = "Juz $juz",
                subtitle = info.rangeLabel,
                destination = Destination.Surah(surah, ayah)
            )
        }
        ReadingPlanKind.ONE_HADITH_DAY -> {
            val books = Catalogs.hadith.books.filter { it.chapters.isNotEmpty() }
            if (books.isEmpty()) {
                ReadingPlanTask("Hadith", "Open a collection", Destination.HadithBook("bukhari"))
            } else {
                val book = books[day % books.size]
                val chapter = book.chapters[day % book.chapters.size]
                ReadingPlanTask(
                    title = book.name,
                    subtitle = chapter.name,
                    destination = Destination.HadithChapter(book.slug, chapter.index)
                )
            }
        }
        ReadingPlanKind.HAJJ_FOCUS -> {
            val tasks = hajjTasks()
            tasks[day % tasks.size]
        }
    }

    private fun hajjTasks(): List<ReadingPlanTask> = listOf(
        ReadingPlanTask("Talbiyah & travel duas", "Hisn al-Muslim", Destination.Duas),
        ReadingPlanTask("Al-Kahf", "Surah 18", Destination.Surah(18)),
        ReadingPlanTask("Al-Mulk", "Surah 67", Destination.Surah(67)),
        ReadingPlanTask("Al-Ikhlas", "Surah 112", Destination.Surah(112)),
        ReadingPlanTask("Morning & evening", "Adhkar categories", Destination.Duas),
        ReadingPlanTask("Al-Baqarah (start)", "Ayah 1", Destination.Surah(2, 1)),
        ReadingPlanTask("Al-Imran (end)", "Last verses", Destination.Surah(3))
    )

    private fun parseAyahKey(key: String): Pair<Int, Int> {
        val surah = key.substringBefore(':').toIntOrNull() ?: 1
        val ayah = key.substringAfter(':').toIntOrNull() ?: 1
        return surah to ayah
    }

    private fun persist() {
        val state = _active.value
        if (state == null) {
            prefs.edit().remove(KEY_ACTIVE).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE, Catalogs.json.encodeToString(state)).apply()
        }
    }
}
