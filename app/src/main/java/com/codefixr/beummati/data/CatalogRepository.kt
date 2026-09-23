package com.codefixr.beummati.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

/** Bundled asset catalogs (same JSON files as the iOS app bundle). */
object Catalogs {
    private const val TAG = "Catalogs"

    lateinit var appContext: Context
        private set

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    val library: LibraryCatalogFile by lazy {
        read<LibraryCatalogFile>("LibraryCatalog.json") ?: LibraryCatalogFile()
    }

    val audio: LectureAudioCatalogFile by lazy {
        read<LectureAudioCatalogFile>("LectureAudioCatalog.json") ?: LectureAudioCatalogFile()
    }

    val hadith: HadithCatalogFile by lazy {
        read<HadithCatalogFile>("HadithCatalog.json") ?: HadithCatalogFile()
    }

    val scholarQuotes: List<ScholarQuote> by lazy {
        read<List<ScholarQuote>>("ScholarQuotes.json") ?: emptyList()
    }

    val sahabaStories: List<SahabaStory> by lazy {
        read<List<SahabaStory>>("SahabaStories.json") ?: emptyList()
    }

    val hisnAlMuslim: HisnAlMuslimFile by lazy {
        read<HisnAlMuslimFile>("HisnAlMuslim.json") ?: HisnAlMuslimFile()
    }

    private val tracksByKey: Map<String, LectureAudioTrack> by lazy {
        audio.tracks.associateBy { it.id }
    }

    val audioSeriesIds: Set<String> by lazy { audio.tracks.map { it.seriesId }.toSet() }

    val lectureSeries: List<LibrarySeries> by lazy {
        library.series.filter { it.id in audioSeriesIds && it.chapters.isNotEmpty() }
    }

    fun series(id: String): LibrarySeries? = library.series.firstOrNull { it.id == id }

    fun chapter(seriesId: String, chapterId: String): LibraryChapter? =
        series(seriesId)?.chapters?.firstOrNull { it.id == chapterId }

    fun track(seriesId: String, chapterId: String): LectureAudioTrack? =
        tracksByKey["$seriesId/$chapterId"]

    fun hasAudio(seriesId: String, chapterId: String): Boolean = track(seriesId, chapterId) != null

    fun hadithBook(slug: String): HadithBookInfo? = hadith.books.firstOrNull { it.slug == slug }

    fun duaCategory(id: Int): DuaCategory? = hisnAlMuslim.categories.firstOrNull { it.id == id }

    // region Chapter text

    private val chapterTextCache = HashMap<String, LibraryChapterText?>()

    private val libraryDirs: Map<String, Set<String>> by lazy {
        val root = listAssets("Library")
        root.associateWith { dir -> listAssets("Library/$dir").toSet() }
    }

    fun hasChapterText(seriesId: String, chapterId: String): Boolean =
        libraryDirs[seriesId]?.contains("$chapterId.json") == true ||
            assetExists("${seriesId}__${chapterId}.json")

    /** Loads `Library/{seriesId}/{chapterId}.json`, falling back to the flat `{seriesId}__{chapterId}.json`. */
    fun chapterText(seriesId: String, chapterId: String): LibraryChapterText? {
        val key = "$seriesId/$chapterId"
        synchronized(chapterTextCache) {
            if (chapterTextCache.containsKey(key)) return chapterTextCache[key]
        }
        val loaded = read<LibraryChapterText>("Library/$seriesId/$chapterId.json", logMissing = false)
            ?: read<LibraryChapterText>("${seriesId}__${chapterId}.json", logMissing = false)
        synchronized(chapterTextCache) { chapterTextCache[key] = loaded }
        return loaded
    }

    // endregion

    // region Asset helpers

    fun listAssets(dir: String): List<String> =
        runCatching { appContext.assets.list(dir)?.toList().orEmpty() }.getOrDefault(emptyList())

    fun assetExists(path: String): Boolean =
        runCatching { appContext.assets.open(path).use { true } }.getOrDefault(false)

    fun readAssetBytes(path: String): ByteArray? =
        runCatching { appContext.assets.open(path).use { it.readBytes() } }.getOrNull()

    fun readAssetText(path: String): String? = readAssetBytes(path)?.toString(Charsets.UTF_8)

    inline fun <reified T> read(path: String, logMissing: Boolean = true): T? {
        val text = readAssetText(path) ?: run {
            if (logMissing) Log.w("Catalogs", "Missing asset $path")
            return null
        }
        return runCatching { json.decodeFromString<T>(text) }
            .onFailure { Log.e("Catalogs", "Failed to decode $path", it) }
            .getOrNull()
    }

    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    // endregion

    // region Disk cache (API responses)

    private val cacheDir: File by lazy { File(appContext.filesDir, "cache").apply { mkdirs() } }

    fun readCache(key: String): String? =
        runCatching { File(cacheDir, key).takeIf { it.exists() }?.readText() }.getOrNull()

    fun writeCache(key: String, text: String) {
        runCatching { File(cacheDir, key).writeText(text) }
            .onFailure { Log.w(TAG, "Cache write failed for $key", it) }
    }

    // endregion
}
