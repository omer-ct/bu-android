package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate

enum class SalahName(val title: String) {
    FAJR("Fajr"),
    DHUHR("Dhuhr"),
    ASR("Asr"),
    MAGHRIB("Maghrib"),
    ISHA("Isha")
}

@Serializable
enum class SalahStatus(val label: String) {
    @SerialName("none")
    NONE("Not logged"),

    @SerialName("prayed")
    PRAYED("Prayed"),

    @SerialName("qada")
    QADA("Made up"),

    @SerialName("missed")
    MISSED("Missed");

    /** Tap order used by the tracker rows. */
    val next: SalahStatus
        get() = when (this) {
            NONE -> PRAYED
            PRAYED -> QADA
            QADA -> MISSED
            MISSED -> NONE
        }

    /** Whether the prayer counts as fulfilled. */
    val isFulfilled: Boolean get() = this == PRAYED || this == QADA
}

@Serializable
data class SalahDayLog(
    val fajr: SalahStatus = SalahStatus.NONE,
    val dhuhr: SalahStatus = SalahStatus.NONE,
    val asr: SalahStatus = SalahStatus.NONE,
    val maghrib: SalahStatus = SalahStatus.NONE,
    val isha: SalahStatus = SalahStatus.NONE
) {
    operator fun get(name: SalahName): SalahStatus = when (name) {
        SalahName.FAJR -> fajr
        SalahName.DHUHR -> dhuhr
        SalahName.ASR -> asr
        SalahName.MAGHRIB -> maghrib
        SalahName.ISHA -> isha
    }

    fun with(name: SalahName, status: SalahStatus): SalahDayLog = when (name) {
        SalahName.FAJR -> copy(fajr = status)
        SalahName.DHUHR -> copy(dhuhr = status)
        SalahName.ASR -> copy(asr = status)
        SalahName.MAGHRIB -> copy(maghrib = status)
        SalahName.ISHA -> copy(isha = status)
    }

    val fulfilledCount: Int get() = SalahName.entries.count { this[it].isFulfilled }

    val isEmpty: Boolean get() = SalahName.entries.all { this[it] == SalahStatus.NONE }
}

/** Per-day salah log persisted in SharedPreferences. Port of the iOS `SalahTracker`. */
object SalahTracker {
    private const val PREFS = "beummati.salah"
    private const val KEY_LOGS = "logs.v1"
    private const val STREAK_LOOKBACK_DAYS = 365

    private lateinit var prefs: SharedPreferences

    private val _logs = MutableStateFlow<Map<String, SalahDayLog>>(emptyMap())
    val logs: StateFlow<Map<String, SalahDayLog>> = _logs.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOGS, null) ?: return
        _logs.value = runCatching { Catalogs.json.decodeFromString<Map<String, SalahDayLog>>(raw) }
            .getOrDefault(emptyMap())
    }

    fun dayKey(date: LocalDate = LocalDate.now()): String = date.toString()

    fun log(date: LocalDate = LocalDate.now()): SalahDayLog = _logs.value[dayKey(date)] ?: SalahDayLog()

    fun set(name: SalahName, status: SalahStatus, date: LocalDate = LocalDate.now()) {
        val key = dayKey(date)
        val updated = (_logs.value[key] ?: SalahDayLog()).with(name, status)
        _logs.value = _logs.value.toMutableMap().apply {
            if (updated.isEmpty) remove(key) else put(key, updated)
        }
        persist()
    }

    fun cycle(name: SalahName, date: LocalDate = LocalDate.now()) {
        set(name, log(date)[name].next, date)
    }

    /**
     * Gentle streak: consecutive days with at least one prayer logged. An empty today
     * does not break the streak yet.
     */
    fun gentleStreak(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var date = today
        repeat(STREAK_LOOKBACK_DAYS) {
            val fulfilled = log(date).fulfilledCount
            when {
                fulfilled >= 1 -> streak++
                date == today -> Unit
                else -> return streak
            }
            date = date.minusDays(1)
        }
        return streak
    }

    fun week(endingOn: LocalDate = LocalDate.now()): List<Pair<LocalDate, SalahDayLog>> =
        (6 downTo 0).map { offset ->
            val date = endingOn.minusDays(offset.toLong())
            date to log(date)
        }

    private fun persist() {
        prefs.edit().putString(KEY_LOGS, Catalogs.json.encodeToString(_logs.value)).apply()
    }
}
