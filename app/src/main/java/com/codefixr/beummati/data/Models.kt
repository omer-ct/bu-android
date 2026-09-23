package com.codefixr.beummati.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val chapters: List<LibraryChapter> = emptyList()
)

@Serializable
data class LibraryChapter(
    val id: String,
    val title: String,
    @SerialName("srtEnglish") val srtEnglish: String? = null,
    @SerialName("srtUrdu") val srtUrdu: String? = null
)

@Serializable
data class LectureAudioCatalogFile(
    val tracks: List<LectureAudioTrack> = emptyList(),
    val attribution: String? = null
)

@Serializable
data class LectureAudioTrack(
    val id: String? = null,
    @SerialName("seriesID") val seriesId: String,
    @SerialName("chapterID") val chapterId: String,
    @SerialName("audioUrl") val audioUrl: String,
    @SerialName("audioName") val audioName: String? = null
)
