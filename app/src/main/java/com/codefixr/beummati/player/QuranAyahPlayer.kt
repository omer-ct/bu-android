package com.codefixr.beummati.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.codefixr.beummati.data.Ayah
import com.codefixr.beummati.data.QuranAudioCache
import com.codefixr.beummati.data.QuranReciter
import com.codefixr.beummati.data.QuranTranslationVoice
import com.codefixr.beummati.data.QuranTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class QuranPlayState(
    val surah: Int = 0,
    val ayah: Int = 0,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val phase: String = "",
    val error: String? = null
)

/**
 * Plays Qur’an ayah-by-ayah (Arabic from everyayah.com, then optional translation).
 * Updates [current] so the reader can highlight the active ayah.
 */
object QuranAyahPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var queueJob: Job? = null

    private var ayahs: List<Ayah> = emptyList()
    private var index = 0
    private var reciter: QuranReciter = QuranReciter.ALAFASY
    private var voice: QuranTranslationVoice = QuranTranslationVoice.NONE
    private var playTranslation = true
    private var onArabic = true
    private var repeatCount = 1
    /** Arabic plays still owed for the current ayah, after the one in flight. */
    private var arabicRepeatLeft = 0
    private var arabicFile: File? = null

    private val _current = MutableStateFlow(QuranPlayState())
    val current: StateFlow<QuranPlayState> = _current.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        QuranAudioCache.init(context)
        QuranTts.init(context)
    }

    fun playSurah(
        context: Context,
        detailAyahs: List<Ayah>,
        startAyah: Int = 1,
        reciter: QuranReciter = QuranAudioCache.reciter.value,
        voice: QuranTranslationVoice = QuranAudioCache.voice.value,
        playTranslation: Boolean = QuranAudioCache.repeatTranslation.value,
        repeatCount: Int = QuranAudioCache.ayahRepeatCount.value
    ) {
        init(context)
        stop()
        ayahs = detailAyahs
        this.reciter = reciter
        this.voice = voice
        this.playTranslation = playTranslation && voice != QuranTranslationVoice.NONE
        this.repeatCount = repeatCount.coerceAtLeast(1)
        index = detailAyahs.indexOfFirst { it.numberInSurah == startAyah }.coerceAtLeast(0)
        ensurePlayer()
        queueJob = scope.launch { playCurrent() }
    }

    fun playAyah(context: Context, ayah: Ayah, siblings: List<Ayah>) {
        playSurah(context, siblings, startAyah = ayah.numberInSurah)
    }

    fun pause() {
        player?.pause()
        _current.value = _current.value.copy(playing = false)
    }

    fun resume() {
        player?.play()
        _current.value = _current.value.copy(playing = true)
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) pause() else resume()
    }

    fun stop() {
        queueJob?.cancel()
        queueJob = null
        player?.stop()
        player?.clearMediaItems()
        arabicFile = null
        arabicRepeatLeft = 0
        _current.value = QuranPlayState()
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    private fun ensurePlayer() {
        val ctx = appContext ?: return
        if (player != null) return
        player = ExoPlayer.Builder(ctx).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        scope.launch { onClipEnded() }
                    }
                }
            })
        }
    }

    private suspend fun playCurrent() {
        val ayah = ayahs.getOrNull(index) ?: run {
            stop()
            return
        }
        _current.value = QuranPlayState(
            surah = ayah.surah,
            ayah = ayah.numberInSurah,
            playing = false,
            loading = true,
            phase = "Loading…"
        )
        onArabic = true
        arabicRepeatLeft = repeatCount - 1
        val file = runCatching {
            QuranAudioCache.ensureArabic(reciter, ayah.surah, ayah.numberInSurah)
        }.onFailure {
            _current.value = _current.value.copy(
                loading = false,
                error = it.message ?: "Could not load audio",
                playing = false
            )
            return
        }.getOrNull() ?: return

        arabicFile = file
        playFile(file, phase = arabicPhase())
    }

    /** Plays the same Arabic clip again for hifz drilling. */
    private suspend fun replayArabic() {
        val file = arabicFile?.takeIf { it.exists() } ?: return advance()
        arabicRepeatLeft--
        onArabic = true
        playFile(file, phase = arabicPhase())
    }

    private fun arabicPhase(): String =
        if (repeatCount > 1) "Arabic ${repeatCount - arabicRepeatLeft}/$repeatCount" else "Arabic"

    private suspend fun playTranslationClip(ayah: Ayah) {
        val ctx = appContext ?: return
        _current.value = _current.value.copy(loading = true, phase = "Translation…")
        val file: File? = when (voice) {
            QuranTranslationVoice.NONE -> null
            QuranTranslationVoice.ENGLISH_TTS ->
                QuranTts.ensureEnglishWav(ctx, ayah.surah, ayah.numberInSurah, ayah.english)
            QuranTranslationVoice.URDU_FARHAT,
            QuranTranslationVoice.URDU_SHAMSHAD ->
                runCatching {
                    QuranAudioCache.ensureTranslationMp3(voice, ayah.surah, ayah.numberInSurah)
                }.getOrNull()
        }
        if (file == null || !file.exists()) {
            advance()
            return
        }
        onArabic = false
        playFile(file, phase = voice.label)
    }

    private suspend fun playFile(file: File, phase: String) = withContext(Dispatchers.Main) {
        val exo = player ?: return@withContext
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.prepare()
        exo.play()
        _current.value = _current.value.copy(
            loading = false,
            playing = true,
            phase = phase,
            error = null
        )
    }

    private suspend fun onClipEnded() {
        val ayah = ayahs.getOrNull(index) ?: return
        when {
            onArabic && arabicRepeatLeft > 0 -> replayArabic()
            onArabic && playTranslation -> playTranslationClip(ayah)
            else -> advance()
        }
    }

    private suspend fun advance() {
        index++
        if (index >= ayahs.size) {
            stop()
        } else {
            playCurrent()
        }
    }
}
