package com.codefixr.beummati.data

/**
 * Content location independent of navigation, so stores in `data` can point at screens
 * without knowing route strings. The UI maps these with `routeFor`.
 */
sealed interface Destination {
    data class Surah(val number: Int) : Destination
    data class HadithBook(val slug: String) : Destination
    data class HadithChapter(val slug: String, val index: Int) : Destination
    data class Series(val id: String) : Destination
    data class LectureChapter(val seriesId: String, val chapterId: String) : Destination
    data class DuaCategory(val id: Int) : Destination
    data object Duas : Destination
    data object Sahaba : Destination
    data object Scholars : Destination
}
