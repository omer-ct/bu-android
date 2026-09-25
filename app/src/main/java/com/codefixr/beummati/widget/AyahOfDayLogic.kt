package com.codefixr.beummati.widget

import android.content.Context
import com.codefixr.beummati.data.Catalogs
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Hafs mushaf ayah counts per surah (114). Sum = 6236. */
private val SURAH_AYAH_COUNTS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12,
    30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
)

private const val TOTAL_AYAHS = 6236
private const val SNIPPET_MAX = 72

internal data class AyahOfDay(
    val surah: Int,
    val ayah: Int,
    val ref: String,
    val arabicSnippet: String?,
)

internal fun ayahOfDay(date: LocalDate = LocalDate.now()): AyahOfDay {
    val global = (
        ((date.toEpochDay() % TOTAL_AYAHS) + TOTAL_AYAHS) % TOTAL_AYAHS + 1
    ).toInt()
    val (surah, ayah) = globalToSurahAyah(global)
    return AyahOfDay(
        surah = surah,
        ayah = ayah,
        ref = "$surah:$ayah",
        arabicSnippet = null,
    )
}

internal fun ayahOfDayWithCache(context: Context, date: LocalDate = LocalDate.now()): AyahOfDay {
    val base = ayahOfDay(date)
    val snippet = cachedArabicSnippet(context, base.surah, base.ayah)
    return base.copy(arabicSnippet = snippet)
}

private fun globalToSurahAyah(global: Int): Pair<Int, Int> {
    var remaining = global.coerceIn(1, TOTAL_AYAHS)
    for (index in SURAH_AYAH_COUNTS.indices) {
        val count = SURAH_AYAH_COUNTS[index]
        if (remaining <= count) return (index + 1) to remaining
        remaining -= count
    }
    return 1 to 1
}

private fun cachedArabicSnippet(context: Context, surah: Int, ayah: Int): String? {
    runCatching { Catalogs.init(context) }
    val cacheKey = "quran_ayah_${surah}_$ayah.json"
    val raw = Catalogs.readCache(cacheKey) ?: return null
    return runCatching {
        val data = Catalogs.json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray ?: return null
        for (el in data) {
            val obj = el.jsonObject
            val identifier = obj["edition"]?.jsonObject?.get("identifier")?.jsonPrimitive?.content.orEmpty()
            if (identifier == "quran-uthmani" || identifier.isEmpty()) {
                val arabic = obj["text"]?.jsonPrimitive?.content.orEmpty()
                if (arabic.isNotBlank()) return snippet(arabic)
            }
        }
        val first = data.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
        if (first.isNotBlank()) snippet(first) else null
    }.getOrNull()
}

private fun snippet(arabic: String): String {
    val clean = arabic.replace('\n', ' ').trim()
    if (clean.length <= SNIPPET_MAX) return clean
    return clean.take(SNIPPET_MAX).trimEnd() + "…"
}
