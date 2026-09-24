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
    val hasUrdu: Boolean = false,
    val hadithCount: Int = 0,
    val chapters: List<HadithChapter> = emptyList()
)

@Serializable
data class HadithChapter(
    val index: Int,
    val name: String,
    val first: Int = 0,
    val last: Int = 0,
    val count: Int = 0
)

data class HadithItem(
    val book: String,
    val number: String,
    val arabic: String,
    val english: String,
    val urdu: String
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
    val duas: List<Dua> = emptyList()
)

@Serializable
data class Dua(
    val id: String,
    val categoryId: Int,
    val arabic: String = "",
    val transliteration: String = "",
    val english: String = "",
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

/** One Arabic word of an ayah with its gloss, from the quran.com words endpoint. */
data class QuranWord(
    val position: Int,
    val arabic: String,
    val translation: String,
    val transliteration: String
)

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
