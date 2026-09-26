package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class OfflinePackId(val label: String, val blurb: String, val sizeHint: String) {
    QURAN(
        "Qur’an",
        "Arabic (Uthmani + Indo-Pak) with your English & Urdu translations — all 114 surahs.",
        "~25 MB"
    ),
    TAFSIR(
        "Tafsir (Urdu)",
        "Ibn Kathir Urdu notes for every surah.",
        "~15 MB"
    ),
    HADITH(
        "Hadith library",
        "Bukhari, Muslim, Tirmidhi, Abu Dawud, Nasai, Ibn Majah & Malik (Arabic · English · Urdu).",
        "~80 MB"
    ),
    QURAN_AUDIO(
        "Qur’an audio (starter)",
        "Mishary Alafasy for Al-Fatiha, Yasin, Ar-Rahman, Al-Mulk, and the three Quls.",
        "~few MB"
    ),
    QURAN_AUDIO_MUSHAF(
        "Qur’an audio (full mushaf)",
        "Complete Alafasy Arabic recitation — all 6,236 ayahs. Large download; Wi‑Fi recommended.",
        "~800 MB+"
    )
}

enum class OfflinePackStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED
}

data class OfflinePackState(
    val id: OfflinePackId,
    val status: OfflinePackStatus = OfflinePackStatus.NOT_DOWNLOADED,
    /** 0f..1f while downloading */
    val progress: Float = 0f,
    val detail: String = "",
    val error: String? = null
)

/**
 * Optional offline packs: download once for fast / no-network reading.
 * Hisn al-Muslim, Sahaba, scholars quotes are already bundled in the APK.
 */
object OfflinePacks {
    private const val TAG = "OfflinePacks"
    private const val PREFS = "beummati.offline"
    private const val KEY_SETUP_DONE = "setupDone"
    private const val KEY_PACK_PREFIX = "pack."

    /** Surahs covered by [OfflinePackId.QURAN_AUDIO] — shared folder with full mushaf. */
    private val STARTER_AUDIO_SURAHS = setOf(1, 36, 55, 67, 112, 113, 114)

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var activeJob: Job? = null

    private val _packs = MutableStateFlow(
        OfflinePackId.entries.map { OfflinePackState(it) }
    )
    val packs: StateFlow<List<OfflinePackState>> = _packs.asStateFlow()

    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _setupDone.value = prefs.getBoolean(KEY_SETUP_DONE, false)
        refreshStatuses()
    }

    fun markSetupDone() {
        _setupDone.value = true
        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
    }

    fun isReady(id: OfflinePackId): Boolean =
        prefs.getBoolean(KEY_PACK_PREFIX + id.name, false)

    fun refreshStatuses() {
        _packs.update { list ->
            list.map { row ->
                val ready = isReady(row.id)
                val downloading = row.status == OfflinePackStatus.DOWNLOADING && _busy.value
                row.copy(
                    status = when {
                        downloading -> OfflinePackStatus.DOWNLOADING
                        ready -> OfflinePackStatus.READY
                        row.status == OfflinePackStatus.FAILED -> OfflinePackStatus.FAILED
                        else -> OfflinePackStatus.NOT_DOWNLOADED
                    },
                    progress = if (downloading) row.progress else 0f,
                    detail = when {
                        ready && !downloading -> "On this device"
                        row.status == OfflinePackStatus.FAILED -> row.detail
                        downloading -> row.detail
                        else -> ""
                    },
                    error = if (row.status == OfflinePackStatus.FAILED) row.error else null
                )
            }
        }
    }

    fun download(ids: Collection<OfflinePackId>) {
        val wanted = ids.distinct().filter { !isReady(it) }
        if (wanted.isEmpty()) return
        activeJob?.cancel()
        activeJob = scope.launch {
            mutex.withLock {
                _busy.value = true
                try {
                    for (id in wanted) {
                        runCatching { downloadOne(id) }
                            .onFailure { e ->
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Pack ${id.name} failed", e)
                                setState(id) {
                                    it.copy(
                                        status = OfflinePackStatus.FAILED,
                                        progress = 0f,
                                        error = e.message ?: "Download failed",
                                        detail = ""
                                    )
                                }
                            }
                    }
                } finally {
                    _busy.value = false
                    refreshStatuses()
                }
            }
        }
    }

    fun remove(id: OfflinePackId) {
        scope.launch {
            mutex.withLock {
                clearPackFiles(id)
                prefs.edit().putBoolean(KEY_PACK_PREFIX + id.name, false).apply()
                setState(id) {
                    OfflinePackState(id, OfflinePackStatus.NOT_DOWNLOADED)
                }
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _busy.value = false
        _packs.update { list ->
            list.map { row ->
                if (row.status == OfflinePackStatus.DOWNLOADING) {
                    row.copy(
                        status = OfflinePackStatus.NOT_DOWNLOADED,
                        progress = 0f,
                        detail = "Cancelled",
                        error = null
                    )
                } else row
            }
        }
        refreshStatuses()
    }

    private suspend fun downloadOne(id: OfflinePackId) {
        setState(id) {
            it.copy(status = OfflinePackStatus.DOWNLOADING, progress = 0f, detail = "Starting…", error = null)
        }
        when (id) {
            OfflinePackId.QURAN -> downloadQuran()
            OfflinePackId.TAFSIR -> downloadTafsir()
            OfflinePackId.HADITH -> downloadHadith()
            OfflinePackId.QURAN_AUDIO -> downloadQuranAudio()
            OfflinePackId.QURAN_AUDIO_MUSHAF -> downloadQuranAudioMushaf()
        }
        prefs.edit().putBoolean(KEY_PACK_PREFIX + id.name, true).apply()
        setState(id) {
            it.copy(status = OfflinePackStatus.READY, progress = 1f, detail = "On this device", error = null)
        }
    }

    private suspend fun downloadQuran() {
        val en = SettingsStore.englishTranslationId.value
        val ur = SettingsStore.urduTranslationId.value
        for (n in 1..114) {
            QuranApi.surah(n, en, ur)
            setState(OfflinePackId.QURAN) {
                it.copy(
                    progress = n / 114f,
                    detail = "Surah $n of 114"
                )
            }
        }
    }

    private suspend fun downloadTafsir() {
        for (n in 1..114) {
            TafsirApi.urduIbnKathir(n)
            setState(OfflinePackId.TAFSIR) {
                it.copy(progress = n / 114f, detail = "Surah $n of 114")
            }
        }
    }

    private suspend fun downloadQuranAudioMushaf() {
        val reciter = QuranReciter.ALAFASY
        val counts = QuranAudioCache.SURAH_AYAH_COUNTS
        val total = counts.sum().coerceAtLeast(1)
        var done = 0
        for (surah in 1..114) {
            val ayahCount = counts[surah - 1]
            for (ayah in 1..ayahCount) {
                QuranAudioCache.ensureArabic(reciter, surah, ayah)
                done++
                if (done % 5 == 0 || ayah == ayahCount) {
                    setState(OfflinePackId.QURAN_AUDIO_MUSHAF) {
                        it.copy(
                            progress = done.toFloat() / total,
                            detail = "Surah $surah · $done / $total"
                        )
                    }
                }
            }
        }
    }

    private suspend fun downloadQuranAudio() {
        val reciter = QuranReciter.ALAFASY
        val surahs = listOf(
            1 to 7,
            36 to 83,
            55 to 78,
            67 to 30,
            112 to 4,
            113 to 5,
            114 to 6
        )
        val total = surahs.sumOf { it.second }
        var done = 0
        for ((surah, ayahCount) in surahs) {
            for (ayah in 1..ayahCount) {
                QuranAudioCache.ensureArabic(reciter, surah, ayah)
                done++
                setState(OfflinePackId.QURAN_AUDIO) {
                    it.copy(
                        progress = done.toFloat() / total,
                        detail = "Surah $surah · ayah $ayah"
                    )
                }
            }
        }
    }

    private suspend fun downloadHadith() {
        val books = Catalogs.hadith.books
        val total = books.sumOf { it.chapters.size.coerceAtLeast(1) }.coerceAtLeast(1)
        var done = 0
        for (book in books) {
            for (ch in book.chapters) {
                runCatching { HadithApi.chapter(book.slug, ch.index) }
                done++
                setState(OfflinePackId.HADITH) {
                    it.copy(
                        progress = done.toFloat() / total,
                        detail = "${book.name} · ${ch.index}/${book.chapters.size}"
                    )
                }
            }
        }
    }

    private fun clearPackFiles(id: OfflinePackId) {
        when (id) {
            OfflinePackId.QURAN_AUDIO -> {
                // Shared Alafasy folder with full mushaf — don't wipe if mushaf is still Ready.
                if (isReady(OfflinePackId.QURAN_AUDIO_MUSHAF)) return
                QuranAudioCache.clearSurahs(QuranReciter.ALAFASY, STARTER_AUDIO_SURAHS)
                return
            }
            OfflinePackId.QURAN_AUDIO_MUSHAF -> {
                if (isReady(OfflinePackId.QURAN_AUDIO)) {
                    QuranAudioCache.clearExceptSurahs(QuranReciter.ALAFASY, STARTER_AUDIO_SURAHS)
                } else {
                    QuranAudioCache.clearReciter(QuranReciter.ALAFASY)
                }
                return
            }
            OfflinePackId.QURAN,
            OfflinePackId.TAFSIR,
            OfflinePackId.HADITH -> Unit
        }
        val dir = File(Catalogs.appContext.filesDir, "cache")
        if (!dir.isDirectory) return
        val prefixes = when (id) {
            OfflinePackId.QURAN -> listOf("quran_v4_", "quran_surah_", "quran_surahs", "quran_juz_", "quran_ayah_", "quran_verse_")
            OfflinePackId.TAFSIR -> listOf("tafsir_ur_")
            OfflinePackId.HADITH -> listOf("hadith_", "hadith_edition_")
            OfflinePackId.QURAN_AUDIO,
            OfflinePackId.QURAN_AUDIO_MUSHAF -> emptyList()
        }
        dir.listFiles()?.forEach { file ->
            if (prefixes.any { file.name.startsWith(it) }) {
                runCatching { file.delete() }
            }
        }
    }

    private fun setState(id: OfflinePackId, transform: (OfflinePackState) -> OfflinePackState) {
        _packs.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }
}
