package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Where you are in each Library series — last chapter opened, chapters finished, and when the
 * series was last touched. Port of the iOS `LibraryProgressStore`.
 *
 * Both reading and listening write here, so "continue" and the completed ticks agree no matter
 * how a chapter was consumed.
 */
object LibraryProgressStore {
    private const val PREFS = "beummati.library"
    private const val KEY_SNAPSHOT = "progress.v1"

    /** v1.1 kept audio completions inside the player's own prefs. */
    private const val LEGACY_PREFS = "beummati.lecture"
    private const val LEGACY_COMPLETED = "completed"

    @Serializable
    private data class Snapshot(
        val lastChapter: Map<String, String> = emptyMap(),
        val completed: Map<String, List<String>> = emptyMap(),
        val lastTouchedAt: Map<String, Long> = emptyMap()
    )

    private lateinit var prefs: SharedPreferences

    private val _lastChapter = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastChapter: StateFlow<Map<String, String>> = _lastChapter.asStateFlow()

    private val _completed = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val completed: StateFlow<Map<String, Set<String>>> = _completed.asStateFlow()

    private val _lastTouchedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snapshot = prefs.getString(KEY_SNAPSHOT, null)
            ?.let { raw -> runCatching { Catalogs.json.decodeFromString<Snapshot>(raw) }.getOrNull() }
        if (snapshot == null) {
            migrateLegacy(app)
            return
        }
        _lastChapter.value = snapshot.lastChapter
        _completed.value = snapshot.completed.mapValues { (_, ids) -> ids.toSet() }
        _lastTouchedAt.value = snapshot.lastTouchedAt
    }

    /** Folds the old `seriesId/chapterId` set into the per-series map so ticks survive the upgrade. */
    private fun migrateLegacy(app: Context) {
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getStringSet(LEGACY_COMPLETED, emptySet())
            .orEmpty()
        if (legacy.isEmpty()) return
        val grouped = HashMap<String, MutableSet<String>>()
        for (key in legacy) {
            val seriesId = key.substringBefore('/', "")
            val chapterId = key.substringAfter('/', "")
            if (seriesId.isEmpty() || chapterId.isEmpty()) continue
            grouped.getOrPut(seriesId) { mutableSetOf() }.add(chapterId)
        }
        _completed.value = grouped
        persist()
    }

    // region Reads

    fun last(seriesId: String): String? = _lastChapter.value[seriesId]

    fun isCompleted(seriesId: String, chapterId: String): Boolean =
        _completed.value[seriesId]?.contains(chapterId) == true

    fun completedCount(seriesId: String): Int = _completed.value[seriesId]?.size ?: 0

    /** Progress including the chapter you are part-way through. */
    fun progressCount(seriesId: String, total: Int): Int {
        val done = completedCount(seriesId)
        val current = _lastChapter.value[seriesId]
        val inProgress = current != null && !isCompleted(seriesId, current)
        return minOf(total, if (inProgress) done + 1 else done)
    }

    /** The chapter after the last one opened, else the first unfinished chapter. */
    fun nextChapter(series: LibrarySeries): LibraryChapter? {
        if (series.chapters.isEmpty()) return null
        val done = _completed.value[series.id].orEmpty()
        val lastIndex = _lastChapter.value[series.id]?.let { id -> series.chapters.indexOfFirst { it.id == id } } ?: -1
        if (lastIndex >= 0) {
            series.chapters.getOrNull(lastIndex + 1)?.let { return it }
        }
        return series.chapters.firstOrNull { it.id !in done }
    }

    /** Where "continue" should land: the chapter you're part-way through, else the next unread one. */
    fun resumeChapter(series: LibrarySeries): LibraryChapter? {
        if (series.chapters.isEmpty()) return null
        val current = _lastChapter.value[series.id]
        if (current != null && !isCompleted(series.id, current)) {
            series.chapters.firstOrNull { it.id == current }?.let { return it }
        }
        return nextChapter(series)
    }

    /** Best series to surface on Home: most recently opened that still has chapters left. */
    fun primarySeries(): LibrarySeries? {
        val touched = Catalogs.library.series.filter { it.id in _lastChapter.value }
        if (touched.isEmpty()) return null
        val unfinished = touched.filter { completedCount(it.id) < it.chapters.size }
        val pool = unfinished.ifEmpty { touched }
        return pool.maxByOrNull { _lastTouchedAt.value[it.id] ?: 0L }
    }

    // endregion

    // region Writes

    /** Records that a chapter was opened. */
    fun mark(seriesId: String, chapterId: String) {
        if (seriesId.isEmpty() || chapterId.isEmpty()) return
        if (_lastChapter.value[seriesId] == chapterId && _lastTouchedAt.value.containsKey(seriesId)) {
            _lastTouchedAt.value = _lastTouchedAt.value + (seriesId to System.currentTimeMillis())
            persist()
            return
        }
        _lastChapter.value = _lastChapter.value + (seriesId to chapterId)
        _lastTouchedAt.value = _lastTouchedAt.value + (seriesId to System.currentTimeMillis())
        persist()
    }

    fun markCompleted(seriesId: String, chapterId: String) = setCompleted(seriesId, chapterId, true)

    fun setCompleted(seriesId: String, chapterId: String, value: Boolean) {
        if (seriesId.isEmpty() || chapterId.isEmpty()) return
        val current = _completed.value[seriesId].orEmpty()
        val updated = if (value) current + chapterId else current - chapterId
        if (updated == current) return
        _completed.value = if (updated.isEmpty()) {
            _completed.value - seriesId
        } else {
            _completed.value + (seriesId to updated)
        }
        persist()
    }

    fun toggleCompleted(seriesId: String, chapterId: String) =
        setCompleted(seriesId, chapterId, !isCompleted(seriesId, chapterId))

    // endregion

    private fun persist() {
        if (!::prefs.isInitialized) return
        val snapshot = Snapshot(
            lastChapter = _lastChapter.value,
            completed = _completed.value.mapValues { (_, ids) -> ids.sorted() },
            lastTouchedAt = _lastTouchedAt.value
        )
        prefs.edit().putString(KEY_SNAPSHOT, Catalogs.json.encodeToString(snapshot)).apply()
    }
}
