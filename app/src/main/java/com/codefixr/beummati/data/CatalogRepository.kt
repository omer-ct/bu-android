package com.codefixr.beummati.data

import android.content.Context
import kotlinx.serialization.json.Json

class CatalogRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val library: LibraryCatalogFile by lazy {
        read("catalog/LibraryCatalog.json")
    }

    val audio: LectureAudioCatalogFile by lazy {
        read("catalog/LectureAudioCatalog.json")
    }

    fun trackFor(seriesId: String, chapterId: String): LectureAudioTrack? =
        audio.tracks.firstOrNull { it.seriesId == seriesId && it.chapterId == chapterId }

    private inline fun <reified T> read(assetPath: String): T {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
