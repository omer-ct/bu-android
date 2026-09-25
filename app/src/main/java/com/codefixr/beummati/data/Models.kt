package com.codefixr.beummati.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Library

@Serializable
data class LibraryCatalogFile(
    val series: List<LibrarySeries> = emptyList()
)

@Serializable
data class LibrarySeries(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: String? = null,
    val kind: String? = null,
    val primaryLanguage: String? = null,
    val attribution: String? = null,
    val chapters: List<LibraryChapter> = emptyList()
)

@Serializable
data class LibraryChapter(
    val id: String,
    val title: String,
    val index: Int? = null,
    val volume: Int? = null,
    val volumeTitle: String? = null,
    val youtubeId: String? = null,
    val srtEnglish: String? = null,
    val srtUrdu: String? = null
)

/** `Library/{seriesId}/{chapterId}.json` */
@Serializable
data class LibraryChapterText(
    val title: String? = null,
    val arabic: String? = null,
    val english: String? = null,
    val urdu: String? = null,
    val reference: String? = null,
    val source: String? = null,
    val youtubeId: String? = null,
    val srtEnglish: String? = null,
    val srtUrdu: String? = null
)

// endregion

// region Lecture audio

@Serializable
data class LectureAudioCatalogFile(
    val source: String? = null,
    val hosts: String? = null,
    val attribution: String? = null,
    val tracks: List<LectureAudioTrack> = emptyList()
)

@Serializable
data class LectureAudioTrack(
    @SerialName("seriesID") val seriesId: String,
    @SerialName("chapterID") val chapterId: String,
    val title: String = "",
    val audioUrl: String,
    val srtFile: String? = null,
    val audioName: String? = null
) {
    val id: String get() = "$seriesId/$chapterId"
}

// endregion

// region Hadith

@Serializable
data class HadithCatalogFile(
    val books: List<HadithBookInfo> = emptyList()
)

@Serializable
data class HadithBookInfo(
    val slug: String,
    val name: String,
    /** Display name in Urdu (Islam One–style), shown beside the English title. */
    val nameUrdu: String = "",
    val hasUrdu: Boolean = false,
    val hadithCount: Int = 0,
    val chapters: List<HadithChapter> = emptyList()
) {
    val titleLine: String
        get() = if (nameUrdu.isNotBlank()) "$name · $nameUrdu" else name

    /** Kitāb that contains hadith [number] by catalog ranges. */
    fun chapterForHadith(number: Int): HadithChapter? {
        if (number <= 0 || chapters.isEmpty()) return null
        chapters.firstOrNull { ch ->
            ch.first > 0 && ch.last >= ch.first && number in ch.first..ch.last
        }?.let { return it }
        return chapters.filter { it.first > 0 && it.first <= number }.maxByOrNull { it.first }
    }

    val maxHadithNumber: Int
        get() = chapters.maxOfOrNull { it.last }?.takeIf { it > 0 } ?: hadithCount
}

@Serializable
data class HadithChapter(
    val index: Int,
    val name: String,
    /** Kitāb title in Urdu (Islam One–style), shown under the English name. */
    val nameUrdu: String = "",
    val first: Int = 0,
    val last: Int = 0,
    val count: Int = 0
)

data class HadithGrade(
    val scholar: String,
    val grade: String
)

data class HadithItem(
    val book: String,
    val number: String,
    val arabic: String,
    val english: String,
    val urdu: String,
    /** Scholar grades from the edition metadata (Ṣaḥīḥ / Ḥasan / Ḍaʿīf, etc.). */
    val grades: List<HadithGrade> = emptyList()
) {
    /** Best single label for a chip — prefers Al-Albani when present. */
    val primaryGrade: String?
        get() {
            if (grades.isEmpty()) return defaultGradeForBook(book)
            val preferred = grades.firstOrNull {
                it.scholar.contains("Albani", ignoreCase = true) ||
                    it.scholar.contains("الباني", ignoreCase = true)
            }
            return (preferred ?: grades.first()).grade.trim().takeIf { it.isNotEmpty() }
                ?: defaultGradeForBook(book)
        }
}

private fun defaultGradeForBook(book: String): String? = when (book.lowercase()) {
    // Entire collections graded Ṣaḥīḥ by consensus (same as Islam One’s صحیح label).
    "bukhari", "muslim" -> "Sahih"
    else -> null
}

/** Live full-text hit from islamic.app (not bundled). */
data class HadithSearchHit(
    val collection: String,
    val bookNumber: Int,
    val hadithNumber: String,
    val chapterTitle: String = "",
    val english: String = "",
    val arabic: String = "",
    val snippet: String = "",
    val grades: List<HadithGrade> = emptyList()
)

// endregion

// region Scholars / Sahaba / Duas

@Serializable
data class ScholarQuote(
    @SerialName("scholarID") val scholarId: String,
    val author: String,
    val title: String = "",
    val english: String = "",
    val arabic: String = "",
    val urdu: String = "",
    val reference: String = "",
    val theme: String = ""
)

@Serializable
data class SahabaStory(
    val id: String,
    val name: String,
    val title: String = "",
    val english: String = "",
    val urdu: String = "",
    val reference: String = "",
    val theme: String = ""
)

@Serializable
data class HisnAlMuslimFile(
    val source: String? = null,
    val note: String? = null,
    val categories: List<DuaCategory> = emptyList(),
    val shortcuts: List<DuaShortcut> = emptyList()
)

@Serializable
data class DuaCategory(
    val id: Int,
    val titleEn: String,
    val titleAr: String = "",
    val titleUr: String = "",
    val duas: List<Dua> = emptyList()
)

@Serializable
data class Dua(
    val id: String,
    val categoryId: Int,
    val arabic: String = "",
    val transliteration: String = "",
    val english: String = "",
    val urdu: String = "",
    val reference: String = "",
    val count: Int = 1
)

@Serializable
data class DuaShortcut(
    val id: String,
    val title: String,
    val categoryIds: List<Int> = emptyList()
)

// endregion

// region Qur'an

@Serializable
data class Surah(
    val number: Int,
    val name: String = "",
    val englishName: String = "",
    val englishNameTranslation: String = "",
    val numberOfAyahs: Int = 0,
    val revelationType: String = ""
)

data class Ayah(
    val surah: Int,
    val numberInSurah: Int,
    val arabic: String,
    val english: String,
    val urdu: String = "",
    val arabicIndopak: String = ""
) {
    val key: String get() = "$surah:$numberInSurah"

    /** Nastaliq and Indo-Pak faces need the Indo-Pak orthography; everything else reads Uthmani. */
    fun arabic(font: ScriptFont): String = if (font.prefersIndoPak) {
        arabicIndopak.ifBlank { arabic }
    } else {
        arabic.ifBlank { arabicIndopak }
    }
}

data class SurahDetail(
    val surah: Surah,
    val ayahs: List<Ayah>
)

/**
 * One of the thirty ajzā’ (parahs). [startKey]/[endKey] are `surah:ayah` in the Hafs mushaf;
 * [startSurahName] is a short English label for the list row.
 */
data class JuzInfo(
    val number: Int,
    val startKey: String,
    val endKey: String,
    val startSurahName: String
) {
    val titleUrdu: String get() = "پارہ ${number.toEasternDigits()}"
    val titleEnglish: String get() = "Parah $number"
    val rangeLabel: String get() = "$startKey – $endKey"
}

/** Hafs juz boundaries used by most South Asian printed masāhif. */
object JuzCatalog {
    val all: List<JuzInfo> = listOf(
        JuzInfo(1, "1:1", "2:141", "Al-Fatihah"),
        JuzInfo(2, "2:142", "2:252", "Al-Baqarah"),
        JuzInfo(3, "2:253", "3:91", "Al-Baqarah"),
        JuzInfo(4, "3:92", "4:23", "Aal-E-Imran"),
        JuzInfo(5, "4:24", "4:147", "An-Nisa"),
        JuzInfo(6, "4:148", "5:81", "An-Nisa"),
        JuzInfo(7, "5:82", "6:110", "Al-Ma'idah"),
        JuzInfo(8, "6:111", "7:87", "Al-An'am"),
        JuzInfo(9, "7:88", "8:40", "Al-A'raf"),
        JuzInfo(10, "8:41", "9:92", "Al-Anfal"),
        JuzInfo(11, "9:93", "11:5", "At-Tawbah"),
        JuzInfo(12, "11:6", "12:52", "Hud"),
        JuzInfo(13, "12:53", "14:52", "Yusuf"),
        JuzInfo(14, "15:1", "16:128", "Al-Hijr"),
        JuzInfo(15, "17:1", "18:74", "Al-Isra"),
        JuzInfo(16, "18:75", "20:135", "Al-Kahf"),
        JuzInfo(17, "21:1", "22:78", "Al-Anbiya"),
        JuzInfo(18, "23:1", "25:20", "Al-Mu'minun"),
        JuzInfo(19, "25:21", "27:55", "Al-Furqan"),
        JuzInfo(20, "27:56", "29:45", "An-Naml"),
        JuzInfo(21, "29:46", "33:30", "Al-Ankabut"),
        JuzInfo(22, "33:31", "36:27", "Al-Ahzab"),
        JuzInfo(23, "36:28", "39:31", "Ya-Sin"),
        JuzInfo(24, "39:32", "41:46", "Az-Zumar"),
        JuzInfo(25, "41:47", "45:37", "Fussilat"),
        JuzInfo(26, "46:1", "51:30", "Al-Ahqaf"),
        JuzInfo(27, "51:31", "57:29", "Adh-Dhariyat"),
        JuzInfo(28, "58:1", "66:12", "Al-Mujadila"),
        JuzInfo(29, "67:1", "77:50", "Al-Mulk"),
        JuzInfo(30, "78:1", "114:6", "An-Naba")
    )

    fun get(number: Int): JuzInfo? = all.firstOrNull { it.number == number }
}

fun Int.toEasternDigits(): String = toString().map { ch ->
    if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch
}.joinToString("")

/** One Arabic word of an ayah with glosses from the quran.com words endpoint. */
data class QuranWord(
    val position: Int,
    val arabic: String,
    val transliteration: String,
    val english: String = "",
    val urdu: String = ""
) {
    fun gloss(lang: WordByWordLang): String = when (lang) {
        WordByWordLang.ENGLISH -> english
        WordByWordLang.URDU -> urdu
        WordByWordLang.BOTH -> listOf(english, urdu).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

/** `TafsirUrdu/{surah}.json` — one entry per ayah. */
@Serializable
data class TafsirEntry(
    val surah: Int = 0,
    val ayah: Int = 0,
    val text: String = ""
)

// endregion

// region Prayer

@Serializable
data class PrayerDay(
    val fajr: String = "—",
    val sunrise: String = "—",
    val dhuhr: String = "—",
    val asr: String = "—",
    val maghrib: String = "—",
    val isha: String = "—",
    val hijriDate: String = "",
    val hijriWeekday: String = "",
    val gregorian: String = "",
    /** Gregorian `yyyy-MM-dd` the times were fetched for. */
    val date: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    /** The five obligatory prayers, in order. */
    val obligatory: List<Pair<String, String>>
        get() = listOf("Fajr" to fajr, "Dhuhr" to dhuhr, "Asr" to asr, "Maghrib" to maghrib, "Isha" to isha)

    /** Raw `HH:mm` for one obligatory prayer. */
    fun time(name: SalahName): String = when (name) {
        SalahName.FAJR -> fajr
        SalahName.DHUHR -> dhuhr
        SalahName.ASR -> asr
        SalahName.MAGHRIB -> maghrib
        SalahName.ISHA -> isha
    }

    /** Everything worth showing on a day card, including sunrise. */
    val all: List<Pair<String, String>>
        get() = listOf(
            "Fajr" to fajr,
            "Sunrise" to sunrise,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )
}

@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    /** False when the position came from the device rather than the Dubai fallback. */
    val isFallback: Boolean = true
)

// endregion

// region Saved

@Serializable
data class Bookmark(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    /** In-app navigation route to reopen the source, when available. */
    val route: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    /** Source reference; lecture moments use `seriesId/chapterId@seconds`. */
    val ref: String = "",
    val route: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// endregion
