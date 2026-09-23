package com.codefixr.beummati.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.Http
import com.codefixr.beummati.data.LectureAudioTrack
import com.codefixr.beummati.data.LibraryChapter
import com.codefixr.beummati.data.LibrarySeries
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One shared lecture audio engine — mini player, full player, notification (via [PlaybackService]),
 * queue, subtitles, downloads, streak. Port of iOS `LectureAudioSession`.
 */
@OptIn(UnstableApi::class)
object LecturePlayerSession {
    private const val TAG = "LecturePlayer"
    private const val PREFS = "beummati.lecture"
    private const val KEY_LAST_SESSION = "lastSession"
    private const val KEY_STREAK = "streak"
    private const val KEY_LAST_LISTEN_DAY = "lastListenDay"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_STARTED = "started"
    private const val KEY_RATE = "rate"
    private const val KEY_LANG = "subtitleLang"
    private const val BOOST_SERIES = "anwar-boj"
    /** BOJ archive rips are ~−17 dB vs Seerah; matches iOS +15 dB tap gain. */
    private const val BOOST_MILLIBELS = 1500
    private const val SKIP_MS = 15_000L

    val rates = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

    data class NowPlaying(
        val seriesId: String,
        val chapterId: String,
        val seriesTitle: String,
        val chapterTitle: String,
        val track: LectureAudioTrack,
        val srtEnglish: String?,
        val srtUrdu: String?
    )

    enum class SleepOption(val label: String, val minutes: Int?) {
        OFF("Off", null),
        M15("15 min", 15),
        M30("30 min", 30),
        M45("45 min", 45),
        M60("60 min", 60),
        END_OF_LECTURE("End of lecture", null)
    }

    data class LastSession(val seriesId: String, val chapterId: String, val timeSeconds: Double)

    data class UiState(
        val nowPlaying: NowPlaying? = null,
        val isPlaying: Boolean = false,
        val isReady: Boolean = false,
        val isBuffering: Boolean = false,
        val error: String? = null,
        val rate: Float = 1.0f,
        val sleep: SleepOption = SleepOption.OFF,
        val sleepEndsAtMillis: Long? = null,
        val subtitleLang: SubtitleLang = SubtitleLang.ENGLISH,
        val availableLangs: List<SubtitleLang> = emptyList(),
        val subtitleLabel: String = "",
        val syncOffset: Double = 0.0,
        val boostEnabled: Boolean = false,
        val queue: List<NowPlaying> = emptyList(),
        val streakDays: Int = 0
    )

    data class Progress(val position: Double = 0.0, val duration: Double = 1.0, val buffered: Double = 0.0)

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _cues = MutableStateFlow<List<SrtCue>>(emptyList())
    val cues: StateFlow<List<SrtCue>> = _cues.asStateFlow()

    private val _activeCue = MutableStateFlow<SrtCue?>(null)
    val activeCue: StateFlow<SrtCue?> = _activeCue.asStateFlow()

    /** track id → 0..1 while downloading, 1 when done. */
    private val _downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloads: StateFlow<Map<String, Float>> = _downloads.asStateFlow()

    private val _completed = MutableStateFlow<Set<String>>(emptySet())
    val completed: StateFlow<Set<String>> = _completed.asStateFlow()

    private val _started = MutableStateFlow<Set<String>>(emptySet())
    val started: StateFlow<Set<String>> = _started.asStateFlow()

    private val _lastSession = MutableStateFlow<LastSession?>(null)
    val lastSession: StateFlow<LastSession?> = _lastSession.asStateFlow()

    private var player: ExoPlayer? = null
    private var loudness: LoudnessEnhancer? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var rawCues: List<SrtCue> = emptyList()
    private var autoAlignOffset = 0.0
    private var didAutoAlign = false
    private var didSynthesize = false
    private var pendingFallback = ""
    private var pendingResumeMs: Long? = null
    private var lastPersistedSecond = -1

    // region Setup

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        Catalogs.init(appContext)
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _completed.value = prefs.getStringSet(KEY_COMPLETED, emptySet()).orEmpty().toSet()
        _started.value = prefs.getStringSet(KEY_STARTED, emptySet()).orEmpty().toSet()
        _lastSession.value = readLastSession()
        val savedLang = prefs.getString(KEY_LANG, null)
            ?.let { name -> SubtitleLang.entries.firstOrNull { it.name == name } }
            ?: SubtitleLang.ENGLISH
        _state.update {
            it.copy(
                rate = prefs.getFloat(KEY_RATE, 1.0f),
                subtitleLang = savedLang,
                streakDays = currentStreak()
            )
        }
        scanDownloads()
    }

    /** Binds a controller so [PlaybackService] runs and shows the media notification. */
    fun connect(context: Context) {
        if (controllerFuture != null) return
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context.applicationContext, token).buildAsync()
    }

    fun player(): ExoPlayer {
        player?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("BeUmmati-Android/1.0")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        val dataSource = DefaultDataSource.Factory(appContext, http)
        val p = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            .build()
        p.addListener(playerListener)
        player = p
        return p
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) touchListeningStreak()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = player ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                Player.STATE_READY -> {
                    _state.update { it.copy(isBuffering = false, isReady = true, error = null) }
                    val durMs = p.duration
                    if (durMs != C.TIME_UNSET && durMs > 0) {
                        _progress.update { it.copy(duration = durMs / 1000.0) }
                        synthesizeIfNeeded()
                        alignCuesIfNeeded()
                        pendingResumeMs?.let { resume ->
                            pendingResumeMs = null
                            if (resume < durMs - 5_000) p.seekTo(resume)
                        }
                    }
                }
                Player.STATE_ENDED -> {
                    _state.update { it.copy(isBuffering = false) }
                    itemDidFinish()
                }
                Player.STATE_IDLE -> _state.update { it.copy(isBuffering = false) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error", error)
            _state.update { it.copy(error = error.localizedMessage ?: "Playback failed", isBuffering = false) }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachLoudness(audioSessionId)
        }
    }

    // endregion

    // region Play

    fun play(
        series: LibrarySeries,
        chapter: LibraryChapter,
        track: LectureAudioTrack,
        enqueueRestOfSeries: Boolean = true,
        startAtSeconds: Double? = null
    ) {
        val item = NowPlaying(
            seriesId = series.id,
            chapterId = chapter.id,
            seriesTitle = series.title,
            chapterTitle = chapter.title,
            track = track,
            srtEnglish = chapter.srtEnglish,
            srtUrdu = chapter.srtUrdu
        )
        pendingFallback = Catalogs.chapterText(series.id, chapter.id)?.english.orEmpty()
        didSynthesize = false
        didAutoAlign = false
        autoAlignOffset = 0.0
        pendingResumeMs = when {
            startAtSeconds != null -> (startAtSeconds * 1000).toLong()
            else -> _lastSession.value
                ?.takeIf { it.seriesId == series.id && it.chapterId == chapter.id && it.timeSeconds > 8 }
                ?.let { (it.timeSeconds * 1000).toLong() }
        }

        val lang = _state.value.subtitleLang
        _state.update {
            it.copy(
                nowPlaying = item,
                isReady = false,
                error = null,
                syncOffset = savedSyncOffset(series.id),
                boostEnabled = series.id == BOOST_SERIES,
                availableLangs = availableLangs(item),
                queue = if (enqueueRestOfSeries) buildQueue(series, chapter) else it.queue
            )
        }
        _progress.value = Progress()
        loadCues(resolveFile(item, lang), lang)
        startPlayer(item)

        markStarted(series.id, chapter.id)
        persistLastSession()
    }

    /** Plays a chapter by id. Returns false when no audio track is mapped. */
    fun playChapter(seriesId: String, chapterId: String, startAtSeconds: Double? = null): Boolean {
        val series = Catalogs.series(seriesId) ?: return false
        val chapter = series.chapters.firstOrNull { it.id == chapterId } ?: return false
        val track = Catalogs.track(seriesId, chapterId) ?: return false
        play(series, chapter, track, startAtSeconds = startAtSeconds)
        return true
    }

    fun resumeLastSession(): Boolean {
        val last = _lastSession.value ?: return false
        return playChapter(last.seriesId, last.chapterId, last.timeSeconds)
    }

    fun playNextInQueue() {
        val queue = _state.value.queue
        if (queue.isEmpty()) return
        val next = queue.first()
        _state.update { it.copy(queue = queue.drop(1)) }
        val series = Catalogs.series(next.seriesId) ?: return
        val chapter = series.chapters.firstOrNull { it.id == next.chapterId } ?: return
        play(series, chapter, next.track, enqueueRestOfSeries = false, startAtSeconds = 0.0)
    }

    fun playFromQueue(item: NowPlaying) {
        val queue = _state.value.queue
        val idx = queue.indexOf(item)
        if (idx < 0) return
        _state.update { it.copy(queue = queue.drop(idx)) }
        playNextInQueue()
    }

    private fun buildQueue(series: LibrarySeries, after: LibraryChapter): List<NowPlaying> {
        val idx = series.chapters.indexOfFirst { it.id == after.id }
        if (idx < 0) return emptyList()
        return series.chapters.drop(idx + 1).mapNotNull { ch ->
            val t = Catalogs.track(series.id, ch.id) ?: return@mapNotNull null
            NowPlaying(series.id, ch.id, series.title, ch.title, t, ch.srtEnglish, ch.srtUrdu)
        }
    }

    private fun startPlayer(item: NowPlaying) {
        val p = player()
        val local = localFile(item.track)
        val uri = if (local.exists()) Uri.fromFile(local) else Uri.parse(item.track.audioUrl)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(item.track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.chapterTitle)
                    .setArtist(item.seriesTitle)
                    .setAlbumTitle("Be Ummati")
                    .build()
            )
            .build()
        p.setMediaItem(mediaItem)
        p.setPlaybackSpeed(_state.value.rate)
        p.prepare()
        p.playWhenReady = true
        attachLoudness(p.audioSessionId)
        startTicker()
    }

    // endregion

    // region Transport

    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            p.play()
            return
        }
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(seconds: Double) {
        val p = player ?: return
        val dur = _progress.value.duration
        val clamped = seconds.coerceIn(0.0, max(dur, 0.0))
        p.seekTo((clamped * 1000).toLong())
        _progress.update { it.copy(position = clamped) }
        refreshActiveCue()
    }

    fun skip(deltaSeconds: Double) {
        seekTo(_progress.value.position + deltaSeconds)
    }

    fun setRate(rate: Float) {
        player?.setPlaybackSpeed(rate)
        prefs.edit().putFloat(KEY_RATE, rate).apply()
        _state.update { it.copy(rate = rate) }
    }

    fun setSleep(option: SleepOption) {
        sleepJob?.cancel()
        sleepJob = null
        val minutes = option.minutes
        val endsAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        _state.update { it.copy(sleep = option, sleepEndsAtMillis = endsAt) }
        if (minutes != null) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                pause()
                _state.update { it.copy(sleep = SleepOption.OFF, sleepEndsAtMillis = null) }
            }
        }
        // END_OF_LECTURE is handled in itemDidFinish.
    }

    fun setBoost(enabled: Boolean) {
        _state.update { it.copy(boostEnabled = enabled) }
        applyBoost()
    }

    fun stopAndClear() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        player?.run {
            stop()
            clearMediaItems()
        }
        rawCues = emptyList()
        _cues.value = emptyList()
        _activeCue.value = null
        _progress.value = Progress()
        _state.update {
            it.copy(
                nowPlaying = null,
                isPlaying = false,
                isReady = false,
                queue = emptyList(),
                sleep = SleepOption.OFF,
                sleepEndsAtMillis = null,
                subtitleLabel = "",
                error = null
            )
        }
    }

    private fun itemDidFinish() {
        _activeCue.value = null
        val np = _state.value.nowPlaying ?: return
        markCompleted(np.seriesId, np.chapterId)
        if (_state.value.sleep == SleepOption.END_OF_LECTURE) {
            _state.update { it.copy(sleep = SleepOption.OFF, sleepEndsAtMillis = null) }
            return
        }
        if (_state.value.queue.isNotEmpty()) playNextInQueue()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            // ~8 Hz is enough for cue sync + scrubber.
            while (isActive) {
                val p = player
                if (p != null) {
                    val pos = p.currentPosition / 1000.0
                    val durMs = p.duration
                    val dur = if (durMs != C.TIME_UNSET && durMs > 0) durMs / 1000.0 else _progress.value.duration
                    _progress.value = Progress(pos, dur, p.bufferedPosition / 1000.0)
                    refreshActiveCue()
                    val sec = pos.toInt()
                    if (sec != lastPersistedSecond && sec % 5 == 0) {
                        lastPersistedSecond = sec
                        persistLastSession()
                    }
                }
                delay(125)
            }
        }
    }

    // endregion

    // region Volume boost

    private fun attachLoudness(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        runCatching { loudness?.release() }
        loudness = runCatching { LoudnessEnhancer(sessionId) }
            .onFailure { Log.w(TAG, "LoudnessEnhancer unavailable", it) }
            .getOrNull()
        applyBoost()
    }

    private fun applyBoost() {
        val enabled = _state.value.boostEnabled
        val enhancer = loudness
        if (enhancer != null) {
            runCatching {
                enhancer.setTargetGain(if (enabled) BOOST_MILLIBELS else 0)
                enhancer.setEnabled(enabled)
            }.onFailure { Log.w(TAG, "LoudnessEnhancer failed", it) }
            player?.volume = 1f
        } else {
            // ExoPlayer volume caps at 1.0, so without the effect we can only make sure we're at max.
            player?.volume = 1f
        }
    }

    // endregion

    // region Subtitles

    fun setSubtitleLang(lang: SubtitleLang) {
        prefs.edit().putString(KEY_LANG, lang.name).apply()
        _state.update { it.copy(subtitleLang = lang) }
        val item = _state.value.nowPlaying ?: return
        didSynthesize = false
        didAutoAlign = false
        autoAlignOffset = 0.0
        loadCues(resolveFile(item, lang), lang)
        alignCuesIfNeeded()
        synthesizeIfNeeded()
    }

    /** Positive = show lines later (helps when the SRT runs ahead of the audio). */
    fun nudgeSync(delta: Double) = setSyncOffset(_state.value.syncOffset + delta)

    fun resetSync() = setSyncOffset(0.0)

    fun setSyncOffset(value: Double) {
        val stepped = ((value * 20).roundToInt() / 20.0).coerceIn(-20.0, 20.0)
        _state.update { it.copy(syncOffset = stepped) }
        applyOffsets()
        _state.value.nowPlaying?.seriesId?.let { persistSyncOffset(stepped, it) }
    }

    private fun syncKey(seriesId: String) = "syncOffset.$seriesId"

    private fun savedSyncOffset(seriesId: String): Double = prefs.getFloat(syncKey(seriesId), 0f).toDouble()

    private fun persistSyncOffset(value: Double, seriesId: String) {
        val edit = prefs.edit()
        if (abs(value) < 0.05) edit.remove(syncKey(seriesId)) else edit.putFloat(syncKey(seriesId), value.toFloat())
        edit.apply()
    }

    private fun resolveFile(item: NowPlaying, lang: SubtitleLang): String? =
        SrtCueParser.resolveFile(item.track, item.srtEnglish, item.srtUrdu, lang)

    private fun availableLangs(item: NowPlaying): List<SubtitleLang> {
        val hasEnglish = SrtCueParser.bundleContains(item.srtEnglish) || SrtCueParser.bundleContains(item.track.srtFile)
        val hasUrdu = SrtCueParser.bundleContains(item.srtUrdu)
        return buildList {
            if (hasEnglish) add(SubtitleLang.ENGLISH)
            if (hasUrdu) add(SubtitleLang.URDU)
            if (hasEnglish) add(SubtitleLang.ARABIC)
        }
    }

    private fun loadCues(file: String?, lang: SubtitleLang) {
        var loaded = SrtCueParser.load(file)
        val label = when {
            loaded.isEmpty() -> ""
            lang == SubtitleLang.ARABIC -> {
                loaded = loaded.map { cue ->
                    val filtered = SrtCueParser.preferArabic(cue.text)
                    cue.copy(text = filtered.ifEmpty { cue.text })
                }
                "SRT · Arabic · ${loaded.size} cues"
            }
            lang == SubtitleLang.URDU && file != null && file == _state.value.nowPlaying?.srtUrdu ->
                "SRT · Urdu · ${loaded.size} cues"
            else -> "SRT · ${loaded.size} cues"
        }
        _state.update { it.copy(subtitleLabel = label) }
        rawCues = loaded
        applyOffsets()
    }

    private fun alignCuesIfNeeded() {
        val duration = _progress.value.duration
        if (didAutoAlign || rawCues.isEmpty() || duration <= 30) return
        val (_, offset) = SrtCueParser.alignToAudioDuration(rawCues, duration)
        didAutoAlign = true
        autoAlignOffset = offset
        applyOffsets()
        if (offset != 0.0) {
            val sec = abs(offset).roundToInt()
            _state.update { it.copy(subtitleLabel = it.subtitleLabel + " · auto-sync −${sec}s") }
        }
    }

    private fun synthesizeIfNeeded() {
        val duration = _progress.value.duration
        if (rawCues.isNotEmpty() || didSynthesize || duration <= 5) return
        val text = pendingFallback.trim()
        if (text.length <= 80) return
        didSynthesize = true
        rawCues = SrtCueParser.synthesize(text, duration)
        _state.update {
            it.copy(subtitleLabel = if (rawCues.isEmpty()) "" else "Approx. transcript · ${rawCues.size} lines")
        }
        applyOffsets()
    }

    private fun applyOffsets() {
        val total = autoAlignOffset + _state.value.syncOffset
        _cues.value = if (abs(total) < 0.001) {
            rawCues
        } else {
            rawCues.mapIndexed { i, c ->
                val s = max(0.0, c.start + total)
                SrtCue(i, s, max(s + 0.25, c.end + total), c.text)
            }
        }
        refreshActiveCue()
    }

    private fun refreshActiveCue() {
        val next = SrtCueParser.activeCue(_cues.value, _progress.value.position)
        if (next?.id != _activeCue.value?.id || next?.text != _activeCue.value?.text) _activeCue.value = next
    }

    /** Current subtitle line + timestamp for notes. Triple of (title, body, ref). */
    fun momentSnapshot(): Triple<String, String, String>? {
        val np = _state.value.nowPlaying ?: return null
        val pos = _progress.value.position
        val t = formatTime(pos)
        val line = _activeCue.value?.text.orEmpty()
        val body = if (line.isEmpty()) "Listening at $t" else "[$t]\n$line"
        return Triple("${np.chapterTitle} · $t", body, "${np.seriesId}/${np.chapterId}@${pos.toInt()}")
    }

    // endregion

    // region Downloads

    private val downloadRoot: File
        get() = File(appContext.filesDir, "LectureAudio").apply { mkdirs() }

    fun localFile(track: LectureAudioTrack): File {
        val name = (track.audioName ?: "${track.seriesId}_${track.chapterId}.mp3").replace("/", "_")
        return File(downloadRoot, "${track.seriesId}__$name")
    }

    fun isDownloaded(track: LectureAudioTrack): Boolean = localFile(track).exists()

    private fun scanDownloads() {
        val done = Catalogs.audio.tracks.filter { isDownloaded(it) }.associate { it.id to 1f }
        _downloads.value = done
    }

    fun download(track: LectureAudioTrack) {
        val id = track.id
        if (isDownloaded(track)) {
            _downloads.update { it + (id to 1f) }
            return
        }
        if ((_downloads.value[id] ?: 0f) in 0.001f..0.999f) return
        _downloads.update { it + (id to 0.02f) }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val dest = localFile(track)
                val tmp = File(dest.parentFile, dest.name + ".part")
                runCatching {
                    val conn = Http.open(track.audioUrl)
                    try {
                        val total = conn.contentLengthLong
                        conn.inputStream.use { input ->
                            tmp.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                var sum = 0L
                                var lastReported = 0f
                                while (true) {
                                    val read = input.read(buf)
                                    if (read < 0) break
                                    output.write(buf, 0, read)
                                    sum += read
                                    if (total > 0) {
                                        val frac = min(0.99f, sum.toFloat() / total)
                                        if (frac - lastReported >= 0.01f) {
                                            lastReported = frac
                                            _downloads.update { it + (id to max(0.02f, frac)) }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    if (!tmp.renameTo(dest)) throw IllegalStateException("Could not move download into place")
                }.onFailure {
                    Log.e(TAG, "Download failed for $id", it)
                    tmp.delete()
                }.isSuccess
            }
            _downloads.update { if (ok) it + (id to 1f) else it - id }
        }
    }

    fun removeDownload(track: LectureAudioTrack) {
        localFile(track).delete()
        _downloads.update { it - track.id }
    }

    // endregion

    // region Progress / streak / last session

    private fun key(seriesId: String, chapterId: String) = "$seriesId/$chapterId"

    fun isCompleted(seriesId: String, chapterId: String) = key(seriesId, chapterId) in _completed.value

    private fun markStarted(seriesId: String, chapterId: String) {
        _started.update { it + key(seriesId, chapterId) }
        prefs.edit().putStringSet(KEY_STARTED, _started.value).apply()
    }

    private fun markCompleted(seriesId: String, chapterId: String) {
        _completed.update { it + key(seriesId, chapterId) }
        prefs.edit().putStringSet(KEY_COMPLETED, _completed.value).apply()
    }

    private fun persistLastSession() {
        val np = _state.value.nowPlaying ?: return
        val session = LastSession(np.seriesId, np.chapterId, _progress.value.position)
        _lastSession.value = session
        prefs.edit()
            .putString(KEY_LAST_SESSION, "${session.seriesId}|${session.chapterId}|${session.timeSeconds}")
            .apply()
    }

    private fun readLastSession(): LastSession? {
        val parts = prefs.getString(KEY_LAST_SESSION, null)?.split("|") ?: return null
        if (parts.size != 3) return null
        return LastSession(parts[0], parts[1], parts[2].toDoubleOrNull() ?: 0.0)
    }

    private fun currentStreak(): Int {
        val last = prefs.getString(KEY_LAST_LISTEN_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return 0
        val gap = ChronoUnit.DAYS.between(last, LocalDate.now())
        return if (gap <= 1) prefs.getInt(KEY_STREAK, 0) else 0
    }

    private fun touchListeningStreak() {
        val today = LocalDate.now()
        val last = prefs.getString(KEY_LAST_LISTEN_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val current = prefs.getInt(KEY_STREAK, 0)
        val next = when {
            last == null -> 1
            last == today -> max(1, current)
            last.plusDays(1) == today -> current + 1
            else -> 1
        }
        prefs.edit().putString(KEY_LAST_LISTEN_DAY, today.toString()).putInt(KEY_STREAK, next).apply()
        _state.update { it.copy(streakDays = next) }
    }

    // endregion
}

fun formatTime(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val s = seconds.roundToInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}
