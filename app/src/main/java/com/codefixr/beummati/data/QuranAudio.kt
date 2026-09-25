package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

private const val TAG = "QuranAudio"

/**
 * everyayah.com hosts per-ayah Arabic (and some translation) MP3s as `{sss}{aaa}.mp3`
 * under a reciter / translation folder.
 */
enum class QuranReciter(val id: String, val label: String, val folder: String) {
    ALAFASY("alafasy", "Mishary Alafasy", "Alafasy_128kbps"),
    HUSARY("husary", "Al-Husary (Mujawwad)", "Husary_128kbps_Mujawwad"),
    MINSHAWY("minshawy", "Minshawy Murattal", "Minshawy_Murattal_128kbps"),
    SUDAIS("sudais", "Abdurrahman As-Sudais", "Abdurrahmaan_As-Sudais_192kbps"),
    MAHER("maher", "Maher Al-Muaiqly", "MaherAlMuaiqly128kbps"),
    AYYOUB("ayyoub", "Muhammad Ayyoub", "Muhammad_Ayyoub_128kbps"),
    BASIT("basit", "Abdul Basit Murattal", "Abdul_Basit_Murattal_192kbps"),
    GHAMADI("ghamadi", "Saad Al-Ghamdi", "Ghamadi_40kbps"),
    ABDUL_SAMAD("abdul_samad", "Abdul Samad", "AbdulSamad_64kbps_QuranExplorer.Com")
}

enum class QuranTranslationVoice(val id: String, val label: String, val blurb: String) {
    NONE("none", "Off", "Arabic only"),
    ENGLISH_TTS("en_tts", "English (device voice)", "Speaks your English translation"),
    URDU_FARHAT("ur_farhat", "Urdu · Farhat Hashmi", "everyayah.com"),
    URDU_SHAMSHAD("ur_shamshad", "Urdu · Shamshad Ali Khan", "everyayah.com")
}

enum class AyahShareLang(val label: String, val blurb: String) {
    ARABIC_ONLY("Arabic only", "Arabic text + Arabic recitation"),
    ENGLISH_ONLY("English only", "English text + spoken English (device voice)"),
    URDU_ONLY("Urdu only", "Urdu text + Urdu audio (Farhat Hashmi)"),
    ARABIC_ENGLISH("Arabic + English", "Arabic + English on screen and in audio"),
    ARABIC_URDU("Arabic + Urdu", "Arabic + Urdu on screen and in audio"),
    ALL("Arabic + English + Urdu", "All three languages on screen and in audio")
}

object QuranAudioUrls {
    private const val EVERYAYAH = "https://everyayah.com/data"

    fun pad(surah: Int, ayah: Int): String =
        "%03d%03d".format(surah, ayah)

    fun arabic(reciter: QuranReciter, surah: Int, ayah: Int): String =
        "$EVERYAYAH/${reciter.folder}/${pad(surah, ayah)}.mp3"

    fun translation(voice: QuranTranslationVoice, surah: Int, ayah: Int): String? = when (voice) {
        QuranTranslationVoice.NONE, QuranTranslationVoice.ENGLISH_TTS -> null
        QuranTranslationVoice.URDU_FARHAT ->
            "$EVERYAYAH/translations/urdu_farhat_hashmi/${pad(surah, ayah)}.mp3"
        QuranTranslationVoice.URDU_SHAMSHAD ->
            "$EVERYAYAH/translations/urdu_shamshad_ali_khan_46kbps/${pad(surah, ayah)}.mp3"
    }
}

/**
 * Offline / cache for ayah audio. Files live under filesDir/quran_audio/.
 */
object QuranAudioCache {
    private const val PREFS = "beummati.quran_audio"
    private const val KEY_RECITER = "reciter"
    private const val KEY_VOICE = "voice"
    private const val KEY_REPEAT = "repeatTranslation"
    private const val KEY_AYAH_REPEAT = "ayahRepeat"

    /** How many times each Arabic ayah is played before moving on — hifz practice. */
    val AYAH_REPEAT_OPTIONS = listOf(1, 2, 3, 5, 7, 10)

    /** Hafs ayah counts per surah (114). */
    val SURAH_AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
        89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12,
        30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var root: File

    private val _reciter = MutableStateFlow(QuranReciter.ALAFASY)
    val reciter: StateFlow<QuranReciter> = _reciter.asStateFlow()

    private val _voice = MutableStateFlow(QuranTranslationVoice.ENGLISH_TTS)
    val voice: StateFlow<QuranTranslationVoice> = _voice.asStateFlow()

    private val _repeatTranslation = MutableStateFlow(true)
    val repeatTranslation: StateFlow<Boolean> = _repeatTranslation.asStateFlow()

    private val _ayahRepeatCount = MutableStateFlow(1)
    val ayahRepeatCount: StateFlow<Int> = _ayahRepeatCount.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        root = File(context.applicationContext.filesDir, "quran_audio").apply { mkdirs() }
        _reciter.value = prefs.getString(KEY_RECITER, null)
            ?.let { id -> QuranReciter.entries.firstOrNull { it.id == id } }
            ?: QuranReciter.ALAFASY
        _voice.value = prefs.getString(KEY_VOICE, null)
            ?.let { id -> QuranTranslationVoice.entries.firstOrNull { it.id == id } }
            ?: QuranTranslationVoice.ENGLISH_TTS
        _repeatTranslation.value = prefs.getBoolean(KEY_REPEAT, true)
        _ayahRepeatCount.value = prefs.getInt(KEY_AYAH_REPEAT, 1)
            .takeIf { it in AYAH_REPEAT_OPTIONS } ?: 1
    }

    fun setReciter(value: QuranReciter) {
        _reciter.value = value
        prefs.edit().putString(KEY_RECITER, value.id).apply()
    }

    fun setVoice(value: QuranTranslationVoice) {
        _voice.value = value
        prefs.edit().putString(KEY_VOICE, value.id).apply()
    }

    fun setRepeatTranslation(value: Boolean) {
        _repeatTranslation.value = value
        prefs.edit().putBoolean(KEY_REPEAT, value).apply()
    }

    fun setAyahRepeatCount(value: Int) {
        val count = value.takeIf { it in AYAH_REPEAT_OPTIONS } ?: 1
        _ayahRepeatCount.value = count
        prefs.edit().putInt(KEY_AYAH_REPEAT, count).apply()
    }

    fun arabicFile(reciter: QuranReciter, surah: Int, ayah: Int): File =
        File(File(root, "ar_${reciter.id}").apply { mkdirs() }, "${QuranAudioUrls.pad(surah, ayah)}.mp3")

    fun translationFile(voice: QuranTranslationVoice, surah: Int, ayah: Int): File =
        File(File(root, "tr_${voice.id}").apply { mkdirs() }, "${QuranAudioUrls.pad(surah, ayah)}.mp3")

    fun ttsFile(surah: Int, ayah: Int, lang: String): File =
        File(File(root, "tts_$lang").apply { mkdirs() }, "${QuranAudioUrls.pad(surah, ayah)}.wav")

    suspend fun ensureArabic(reciter: QuranReciter, surah: Int, ayah: Int): File {
        val dest = arabicFile(reciter, surah, ayah)
        if (dest.exists() && dest.length() > 1024) return dest
        download(QuranAudioUrls.arabic(reciter, surah, ayah), dest)
        return dest
    }

    suspend fun ensureTranslationMp3(voice: QuranTranslationVoice, surah: Int, ayah: Int): File? {
        val url = QuranAudioUrls.translation(voice, surah, ayah) ?: return null
        val dest = translationFile(voice, surah, ayah)
        if (dest.exists() && dest.length() > 1024) return dest
        download(url, dest)
        return dest
    }

    /**
     * Download every ayah of [surah] for [reciter]. Optional [onProgress] receives 0f..1f.
     */
    suspend fun downloadSurah(
        reciter: QuranReciter,
        surah: Int,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ) {
        val count = SURAH_AYAH_COUNTS.getOrElse(surah - 1) { 0 }.coerceAtLeast(1)
        for (ayah in 1..count) {
            ensureArabic(reciter, surah, ayah)
            onProgress(ayah.toFloat() / count, "Ayah $ayah / $count")
        }
    }

    fun surahCachedCount(reciter: QuranReciter, surah: Int): Int {
        val count = SURAH_AYAH_COUNTS.getOrElse(surah - 1) { 0 }
        return (1..count).count { arabicFile(reciter, surah, it).exists() }
    }

    fun clearReciter(reciter: QuranReciter) {
        File(root, "ar_${reciter.id}").deleteRecursively()
    }

    fun clearVoice(voice: QuranTranslationVoice) {
        File(root, "tr_${voice.id}").deleteRecursively()
        if (voice == QuranTranslationVoice.ENGLISH_TTS) {
            File(root, "tts_en").deleteRecursively()
        }
    }

    fun bytesOnDisk(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private suspend fun download(url: String, dest: File) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BeUmmati/1.6")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} for $url")
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            conn.disconnect()
            if (tmp.exists() && !dest.exists()) tmp.delete()
        }
    }
}

/**
 * Synthesizes English (or other) translation speech to a WAV file via [TextToSpeech].
 */
object QuranTts {
    private val lock = Any()
    @Volatile private var tts: TextToSpeech? = null
    private val ready = ConcurrentHashMap.newKeySet<Int>()

    fun init(context: Context) {
        synchronized(lock) {
            if (tts != null) return
            val app = context.applicationContext
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) ready.add(1)
            }
        }
    }

    suspend fun ensureEnglishWav(context: Context, surah: Int, ayah: Int, text: String): File? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        init(context)
        val dest = QuranAudioCache.ttsFile(surah, ayah, "en")
        if (dest.exists() && dest.length() > 256) return dest
        return withContext(Dispatchers.Main) {
            synthesizeToFile(dest, trimmed, Locale.US)
        }
    }

    private suspend fun synthesizeToFile(dest: File, text: String, locale: Locale): File? {
        val engine = tts ?: return null
        // Wait briefly for TTS engine init.
        var spins = 0
        while (ready.isEmpty() && spins < 40) {
            kotlinx.coroutines.delay(50)
            spins++
        }
        if (ready.isEmpty()) return null
        dest.parentFile?.mkdirs()
        engine.language = locale
        return suspendCancellableCoroutine { cont ->
            val utteranceId = "bu_${dest.name}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(if (dest.exists()) dest else null)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            val code = engine.synthesizeToFile(text, null, dest, utteranceId)
            if (code != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS synthesize failed for ${dest.name}")
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { dest.delete() } }
        }
    }
}
