package com.codefixr.beummati.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

enum class ReminderLane(val title: String) {
    QURAN("Qur’an"),
    HADITH("Hadith"),
    SERIES("Series"),
    QUOTES("Quotes"),
    DHIKR("Dhikr")
}

data class ReminderItem(
    val lane: ReminderLane,
    val title: String,
    val ref: String,
    val arabic: String = "",
    val english: String = "",
    val urdu: String = "",
    val destination: Destination
) {
    /** Plain text for [android.content.Intent.ACTION_SEND]. */
    fun shareBody(): String = buildList {
        if (title.isNotBlank()) add(title)
        if (arabic.isNotBlank()) add(arabic)
        if (english.isNotBlank()) add(english)
        if (urdu.isNotBlank()) add(urdu)
        if (ref.isNotBlank()) add("— $ref")
    }.joinToString("\n\n")
}

/**
 * Picks one reminder per lane from the bundled catalogs, preferring seeds that match the
 * time of day. Simplified port of the iOS `ReminderStore` + `ReminderCatalog`.
 */
object ReminderStore {
    private const val TAG = "ReminderStore"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _items = MutableStateFlow<Map<ReminderLane, ReminderItem>>(emptyMap())
    val items: StateFlow<Map<ReminderLane, ReminderItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow<Set<ReminderLane>>(emptySet())
    val loading: StateFlow<Set<ReminderLane>> = _loading.asStateFlow()

    private val _errors = MutableStateFlow<Map<ReminderLane, String>>(emptyMap())
    val errors: StateFlow<Map<ReminderLane, String>> = _errors.asStateFlow()

    /** Recently shown refs per lane, so "Next" doesn't repeat itself. */
    private val recent = mutableMapOf<ReminderLane, MutableList<String>>()

    /** Loads any lane that has no card yet. Safe to call on every Home recomposition. */
    fun ensureLoaded() {
        ReminderLane.entries
            .filter { it !in _items.value && it !in _loading.value }
            .forEach { refresh(it) }
    }

    fun refreshAll() = ReminderLane.entries.forEach { refresh(it) }

    fun refresh(lane: ReminderLane) {
        if (lane in _loading.value) return
        _loading.value = _loading.value + lane
        _errors.value = _errors.value - lane
        scope.launch {
            val item = runCatching { resolve(lane) }
                .onFailure { Log.w(TAG, "Reminder lane ${lane.name} failed", it) }
                .getOrNull()
            if (item != null) {
                _items.value = _items.value + (lane to item)
                remember(lane, item.ref)
            } else {
                _errors.value = _errors.value + (lane to "Couldn’t load a ${lane.title} reminder")
            }
            _loading.value = _loading.value - lane
        }
    }

    private fun remember(lane: ReminderLane, ref: String) {
        val seen = recent.getOrPut(lane) { mutableListOf() }
        seen.add(ref)
        while (seen.size > 12) seen.removeAt(0)
    }

    private fun isRecent(lane: ReminderLane, ref: String): Boolean = recent[lane]?.contains(ref) == true

    private suspend fun resolve(lane: ReminderLane): ReminderItem? = when (lane) {
        ReminderLane.QURAN -> quran()
        ReminderLane.HADITH -> hadith()
        ReminderLane.SERIES -> withContext(Dispatchers.Default) { series() }
        ReminderLane.QUOTES -> withContext(Dispatchers.Default) { quote() }
        ReminderLane.DHIKR -> withContext(Dispatchers.Default) { dhikr() }
    }

    // region Lanes

    private suspend fun quran(): ReminderItem? {
        for (seed in pool(QURAN_SEEDS, ReminderLane.QURAN) { "Qur’an ${it.key}" }) {
            val ayah = runCatching { QuranApi.ayah(seed.key) }.getOrNull() ?: continue
            return ReminderItem(
                lane = ReminderLane.QURAN,
                title = seed.title,
                ref = "Qur’an ${seed.key}",
                arabic = ayah.arabic,
                english = ayah.english,
                destination = Destination.Surah(ayah.surah)
            )
        }
        return null
    }

    private suspend fun hadith(): ReminderItem? {
        for (seed in pool(HADITH_SEEDS, ReminderLane.HADITH) { "${bookName(it.book)} ${it.number}" }) {
            val section = runCatching { HadithApi.chapter(seed.book, seed.section) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: continue
            val hadith = section.firstOrNull { it.number == seed.number } ?: section.random()
            val name = bookName(seed.book)
            return ReminderItem(
                lane = ReminderLane.HADITH,
                title = seed.title,
                ref = "$name ${hadith.number}",
                arabic = hadith.arabic,
                english = hadith.english,
                urdu = hadith.urdu,
                destination = Destination.HadithChapter(seed.book, seed.section)
            )
        }
        return null
    }

    /** A lecture the listener hasn't finished, with a snippet of its transcript when bundled. */
    private fun series(): ReminderItem? {
        val candidates = Catalogs.lectureSeries.flatMap { s ->
            s.chapters.filter { Catalogs.hasAudio(s.id, it.id) }.map { s to it }
        }
        if (candidates.isEmpty()) return null
        val withText = candidates.filter { (s, c) -> Catalogs.hasChapterText(s.id, c.id) }
        val ordered = (withText.shuffled() + candidates.shuffled())
        val (seriesItem, chapter) = ordered.firstOrNull { (s, c) ->
            !isRecent(ReminderLane.SERIES, "${s.id}/${c.id}")
        } ?: ordered.first()

        val text = Catalogs.chapterText(seriesItem.id, chapter.id)
        val english = text?.english?.let { snippet(it) }.orEmpty()
        val urdu = text?.urdu?.let { snippet(it) }.orEmpty()
        return ReminderItem(
            lane = ReminderLane.SERIES,
            title = chapter.title,
            ref = "${seriesItem.id}/${chapter.id}",
            english = english.ifBlank { seriesItem.subtitle.orEmpty() },
            urdu = urdu,
            destination = Destination.LectureChapter(seriesItem.id, chapter.id)
        )
    }

    private fun quote(): ReminderItem? {
        val quotes = Catalogs.scholarQuotes.takeIf { it.isNotEmpty() } ?: return null
        val mood = moodThemes()
        val ordered = quotes.filter { it.theme in mood }.shuffled() + quotes.shuffled()
        val quote = ordered.firstOrNull { !isRecent(ReminderLane.QUOTES, quoteRef(it)) } ?: ordered.first()
        return ReminderItem(
            lane = ReminderLane.QUOTES,
            title = quote.title.ifBlank { quote.author },
            ref = quoteRef(quote),
            arabic = quote.arabic,
            english = quote.english,
            urdu = quote.urdu,
            destination = Destination.Scholars
        )
    }

    private fun dhikr(): ReminderItem? {
        val categories = Catalogs.hisnAlMuslim.categories.filter { it.duas.isNotEmpty() }
        if (categories.isEmpty()) return null
        val duas = categories.flatMap { category -> category.duas.map { category to it } }.shuffled()
        val (category, dua) = duas.firstOrNull { !isRecent(ReminderLane.DHIKR, it.second.id) } ?: duas.first()
        return ReminderItem(
            lane = ReminderLane.DHIKR,
            title = category.titleEn,
            ref = dua.id,
            arabic = dua.arabic,
            english = listOf(dua.transliteration, dua.english).filter { it.isNotBlank() }.joinToString("\n"),
            destination = Destination.DuaCategory(category.id)
        )
    }

    // endregion

    private fun quoteRef(quote: ScholarQuote): String =
        quote.reference.ifBlank { "${quote.author} · ${quote.title}" }

    private fun bookName(slug: String): String =
        Catalogs.hadithBook(slug)?.name ?: slug.replaceFirstChar { it.uppercase() }

    private fun snippet(text: String, maxChars: Int = 320): String {
        val paragraph = text.split(Regex("\n\\s*\n")).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (paragraph.length <= maxChars) return paragraph
        val cut = paragraph.take(maxChars)
        val lastStop = cut.lastIndexOfAny(charArrayOf('.', '۔', '!', '?'))
        return if (lastStop > maxChars / 2) cut.take(lastStop + 1) else "$cut…"
    }

    /** Seeds matching the hour's mood first, then the rest, skipping refs shown recently. */
    private fun <T : Seed> pool(seeds: List<T>, lane: ReminderLane, ref: (T) -> String): List<T> {
        val mood = moodThemes()
        val ordered = seeds.filter { it.theme in mood }.shuffled() + seeds.shuffled()
        val fresh = ordered.filterNot { isRecent(lane, ref(it)) }
        return fresh.ifEmpty { ordered }
    }

    /** Port of `SeriesReminderBrain.moodThemes` — gentle time-of-day weighting. */
    private fun moodThemes(now: LocalTime = LocalTime.now()): Set<String> = when (now.hour) {
        in 4..10 -> setOf("Morning", "Gratitude", "Hope", "Trust", "Heart")
        in 11..15 -> setOf("Heart", "Patience", "Mercy", "Trust")
        in 16..19 -> setOf("Trust", "Hope", "Patience", "Mercy")
        else -> setOf("Heart", "Mercy", "Hope", "Dhikr", "Patience")
    }

    // region Curated seeds (from the iOS ReminderCatalog)

    private interface Seed {
        val theme: String
    }

    private data class QuranSeed(val key: String, override val theme: String, val title: String) : Seed

    private data class HadithSeed(
        val book: String,
        val section: Int,
        val number: String,
        override val theme: String,
        val title: String
    ) : Seed

    private val QURAN_SEEDS = listOf(
        QuranSeed("2:255", "Heart", "Ayat al-Kursi"),
        QuranSeed("2:286", "Mercy", "Allah does not burden"),
        QuranSeed("2:152", "Gratitude", "Remember Me"),
        QuranSeed("2:153", "Patience", "Seek help in patience"),
        QuranSeed("3:139", "Hope", "Do not weaken"),
        QuranSeed("3:159", "Mercy", "By mercy from Allah"),
        QuranSeed("9:51", "Trust", "Nothing will befall us"),
        QuranSeed("13:28", "Heart", "Hearts find rest"),
        QuranSeed("14:7", "Gratitude", "If you are grateful"),
        QuranSeed("18:10", "Trust", "Grant us mercy"),
        QuranSeed("21:87", "Mercy", "La ilaha illa Anta"),
        QuranSeed("39:53", "Hope", "Do not despair"),
        QuranSeed("48:1", "Hope", "A clear opening"),
        QuranSeed("55:13", "Gratitude", "Which favours"),
        QuranSeed("65:3", "Trust", "He will provide"),
        QuranSeed("67:2", "Heart", "Who created death"),
        QuranSeed("93:5", "Hope", "Your Lord will give"),
        QuranSeed("94:5", "Patience", "With hardship, ease"),
        QuranSeed("94:8", "Morning", "To your Lord turn"),
        QuranSeed("1:5", "Morning", "You alone we worship"),
        QuranSeed("1:6", "Morning", "Guide us"),
        QuranSeed("112:1", "Heart", "He is One"),
        QuranSeed("113:1", "Morning", "Seek refuge"),
        QuranSeed("114:1", "Morning", "Lord of mankind")
    )

    private val HADITH_SEEDS = listOf(
        HadithSeed("bukhari", 1, "1", "Heart", "Actions by intentions"),
        HadithSeed("bukhari", 2, "8", "Mercy", "Religion is naseehah"),
        HadithSeed("muslim", 1, "8", "Heart", "Islam · Iman · Ihsan"),
        HadithSeed("nawawi", 1, "1", "Heart", "Actions by intention"),
        HadithSeed("nawawi", 1, "2", "Heart", "The visit of Jibreel"),
        HadithSeed("nawawi", 1, "5", "Heart", "Innovation rejected"),
        HadithSeed("nawawi", 1, "6", "Heart", "Halal and haram are clear"),
        HadithSeed("nawawi", 1, "12", "Mercy", "Love for your brother"),
        HadithSeed("nawawi", 1, "15", "Mercy", "Speak good or stay silent"),
        HadithSeed("nawawi", 1, "18", "Trust", "Fear Allah wherever you are"),
        HadithSeed("nawawi", 1, "19", "Trust", "Be mindful of Allah"),
        HadithSeed("nawawi", 1, "40", "Hope", "Be in the world as a traveller"),
        HadithSeed("qudsi", 1, "1", "Mercy", "Hadith Qudsi"),
        HadithSeed("tirmidhi", 1, "1", "Morning", "Tirmidhi opening")
    )

    // endregion
}
