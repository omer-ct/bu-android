package com.codefixr.beummati.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

private const val TAG = "AyahVideo"
private const val WIDTH = 720
private const val HEIGHT = 1280
/** Slideshow rate — enough for still cards; keeps files small. */
private const val FPS = 2
private const val VIDEO_BITRATE = 1_800_000
private const val AUDIO_BITRATE = 96_000
private const val AUDIO_SAMPLE_RATE = 44_100
private const val AUDIO_CHANNELS = 1
/** Hard caps so a few short ayahs can never become a half-hour file. */
private const val MAX_CLIP_US = 18_000_000L
private const val MAX_TOTAL_US = 90_000_000L
private const val MIN_CLIP_US = 1_200_000L

data class AyahVideoRequest(
    val surahName: String,
    val ayahs: List<Ayah>,
    val lang: AyahShareLang,
    val arabicFont: ScriptFont = ScriptFont.UTHMANI,
    val reciter: QuranReciter = QuranReciter.ALAFASY,
    /** Used when [lang] includes Urdu or English spoken translation. */
    val translationVoice: QuranTranslationVoice = QuranTranslationVoice.URDU_FARHAT
)

/**
 * Vertical MP4: still frames timed to real decoded audio length (PCM), with explicit PTS.
 * No Surface+sleep (that produced absurd 30+ minute silent videos).
 */
object AyahVideoExporter {
    suspend fun exportAndShare(context: Context, request: AyahVideoRequest): Boolean =
        withContext(Dispatchers.IO) {
            val file = runCatching { render(context.applicationContext, request) }
                .onFailure { Log.w(TAG, "Video export failed", it) }
                .getOrNull()
                ?: return@withContext false
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.shares",
                    file
                )
                val range = if (request.ayahs.size > 1) {
                    "${request.ayahs.first().key}–${request.ayahs.last().numberInSurah}"
                } else {
                    request.ayahs.first().key
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${request.surahName} · $range\n— via Be Ummati")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share ayah video"))
            }
            true
        }

    private suspend fun render(context: Context, request: AyahVideoRequest): File {
        QuranAudioCache.init(context)
        QuranTts.init(context)

        val segments = ArrayList<Segment>()
        for (ayah in request.ayahs) {
            val clips = prepareClips(context, request, ayah)
            if (clips.isEmpty()) {
                segments += Segment(ayah, SpokenLang.NONE, ShortArray(0), MIN_CLIP_US)
                continue
            }
            for (clip in clips) {
                val pcm = decodeFileToPcmArray(clip.file)
                var durUs = if (pcm.isEmpty()) {
                    MIN_CLIP_US
                } else {
                    (pcm.size.toLong() * 1_000_000L) / AUDIO_SAMPLE_RATE
                }
                durUs = durUs.coerceIn(MIN_CLIP_US, MAX_CLIP_US)
                // Trim PCM to match capped duration so A/V stay aligned.
                val maxSamples = ((durUs * AUDIO_SAMPLE_RATE) / 1_000_000L).toInt().coerceAtLeast(1)
                val trimmed = if (pcm.size > maxSamples) pcm.copyOf(maxSamples) else pcm
                segments += Segment(ayah, clip.spoken, trimmed, durUs)
            }
        }

        // Scale down if total would exceed cap (keeps proportions).
        val rawTotal = segments.sumOf { it.durationUs }.coerceAtLeast(1L)
        val scale = if (rawTotal > MAX_TOTAL_US) MAX_TOTAL_US.toDouble() / rawTotal else 1.0
        if (scale < 1.0) {
            for (i in segments.indices) {
                val s = segments[i]
                val newDur = (s.durationUs * scale).toLong().coerceAtLeast(MIN_CLIP_US / 2)
                val maxSamples = ((newDur * AUDIO_SAMPLE_RATE) / 1_000_000L).toInt().coerceAtLeast(1)
                val pcm = if (s.pcm.size > maxSamples) s.pcm.copyOf(maxSamples) else s.pcm
                segments[i] = s.copy(pcm = pcm, durationUs = newDur)
            }
        }

        val pcm = concatPcm(segments)
        Log.i(
            TAG,
            "Export ${request.ayahs.size} ayahs → ${segments.size} clips, " +
                "audio=${pcm.size / AUDIO_SAMPLE_RATE}s, frames≈${segments.sumOf { frameCountFor(it.durationUs) }}"
        )

        val outDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val out = File(outDir, "ayah_${System.currentTimeMillis()}.mp4")
        if (out.exists()) out.delete()

        mux(out, request, segments, pcm)

        if (!out.exists() || out.length() < 4096) {
            throw IllegalStateException("Video file was empty")
        }
        return out
    }

    private enum class SpokenLang { ARABIC, ENGLISH, URDU, NONE }

    private data class AudioClip(val file: File, val spoken: SpokenLang)

    private data class Segment(
        val ayah: Ayah,
        val spoken: SpokenLang,
        val pcm: ShortArray,
        val durationUs: Long
    )

    private fun concatPcm(segments: List<Segment>): ShortArray {
        val out = ArrayList<Short>(segments.sumOf { it.pcm.size } + segments.size * (AUDIO_SAMPLE_RATE / 10))
        for (s in segments) {
            s.pcm.forEach { out.add(it) }
            repeat(AUDIO_SAMPLE_RATE / 10) { out.add(0) }
        }
        return out.toShortArray()
    }

    private fun frameCountFor(durationUs: Long): Int =
        ((durationUs * FPS) / 1_000_000L).toInt().coerceAtLeast(1)

    private suspend fun prepareClips(
        context: Context,
        request: AyahVideoRequest,
        ayah: Ayah
    ): List<AudioClip> {
        val files = mutableListOf<AudioClip>()
        val lang = request.lang
        if (langShowsArabic(lang)) {
            runCatching {
                QuranAudioCache.ensureArabic(request.reciter, ayah.surah, ayah.numberInSurah)
            }.getOrNull()?.let { files += AudioClip(it, SpokenLang.ARABIC) }
        }
        if (langShowsUrdu(lang)) {
            val urduVoice = when (request.translationVoice) {
                QuranTranslationVoice.URDU_SHAMSHAD -> QuranTranslationVoice.URDU_SHAMSHAD
                else -> QuranTranslationVoice.URDU_FARHAT
            }
            runCatching {
                QuranAudioCache.ensureTranslationMp3(
                    urduVoice,
                    ayah.surah,
                    ayah.numberInSurah
                )
            }.getOrNull()?.let { files += AudioClip(it, SpokenLang.URDU) }
        }
        if (langShowsEnglish(lang) && ayah.english.isNotBlank()) {
            QuranTts.ensureEnglishWav(context, ayah.surah, ayah.numberInSurah, ayah.english)
                ?.let { files += AudioClip(it, SpokenLang.ENGLISH) }
        }
        if (files.isEmpty() && langShowsArabic(lang).not()) {
            runCatching {
                QuranAudioCache.ensureArabic(request.reciter, ayah.surah, ayah.numberInSurah)
            }.getOrNull()?.let { files += AudioClip(it, SpokenLang.ARABIC) }
        }
        return files
    }

    private fun mux(
        out: File,
        request: AyahVideoRequest,
        segments: List<Segment>,
        pcm: ShortArray
    ) {
        val tmpAudio = File(out.parentFile, "ayah_audio_${System.currentTimeMillis()}.m4a")
        val hasAudio = pcm.isNotEmpty() && runCatching {
            encodePcmToM4a(pcm, tmpAudio)
            tmpAudio.exists() && tmpAudio.length() > 256
        }.onFailure { Log.w(TAG, "Audio encode failed", it) }.getOrDefault(false)

        if (!hasAudio) {
            Log.w(TAG, "No audio track — pcm=${pcm.size} samples")
        }

        var audioExtractor: MediaExtractor? = null
        var audioFormat: MediaFormat? = null
        if (hasAudio) {
            val ex = MediaExtractor()
            ex.setDataSource(tmpAudio.absolutePath)
            val track = (0 until ex.trackCount).first { idx ->
                ex.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            ex.selectTrack(track)
            audioExtractor = ex
            audioFormat = ex.getTrackFormat(track)
        }

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val colorFormat = pickYuvColorFormat(videoCodec)
            ?: throw IllegalStateException("No YUV color format for AVC encoder")
        val videoFormatCfg = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, WIDTH * HEIGHT * 3 / 2)
        }
        videoCodec.configure(videoFormatCfg, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoCodec.start()

        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false
        val vInfo = MediaCodec.BufferInfo()

        fun startIfReady() {
            if (muxerStarted) return
            if (videoTrack < 0) return
            if (hasAudio && audioTrack < 0) return
            muxer.start()
            muxerStarted = true
        }

        fun drainVideo() {
            while (true) {
                val outIdx = videoCodec.dequeueOutputBuffer(vInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (videoTrack < 0) {
                            videoTrack = muxer.addTrack(videoCodec.outputFormat)
                            if (hasAudio && audioFormat != null && audioTrack < 0) {
                                audioTrack = muxer.addTrack(audioFormat)
                            }
                            startIfReady()
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = videoCodec.getOutputBuffer(outIdx)
                        if (outBuf != null && vInfo.size > 0 && muxerStarted && videoTrack >= 0) {
                            outBuf.position(vInfo.offset)
                            outBuf.limit(vInfo.offset + vInfo.size)
                            muxer.writeSampleData(videoTrack, outBuf, vInfo)
                        }
                        val eos = vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        videoCodec.releaseOutputBuffer(outIdx, false)
                        if (eos) return
                    }
                }
            }
        }

        try {
            // If audio-only format is known, add it after first video format — handled in drain.
            // Force video format by queuing first frame early.
            var ptsUs = 0L
            val frameStepUs = 1_000_000L / FPS
            for (segment in segments) {
                val bitmap = drawFrame(request, segment.ayah, segment.spoken)
                val yuv = bitmapToNv12(bitmap)
                bitmap.recycle()
                val frames = frameCountFor(segment.durationUs)
                repeat(frames) {
                    var queued = false
                    var spins = 0
                    while (!queued && spins++ < 80) {
                        val inIdx = videoCodec.dequeueInputBuffer(30_000)
                        if (inIdx >= 0) {
                            val inBuf = videoCodec.getInputBuffer(inIdx)!!
                            inBuf.clear()
                            inBuf.put(yuv)
                            videoCodec.queueInputBuffer(inIdx, 0, yuv.size, ptsUs, 0)
                            queued = true
                        } else {
                            drainVideo()
                        }
                    }
                    ptsUs += frameStepUs
                    drainVideo()
                }
            }
            // EOS video
            run {
                var spins = 0
                while (spins++ < 80) {
                    val inIdx = videoCodec.dequeueInputBuffer(30_000)
                    if (inIdx >= 0) {
                        videoCodec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        break
                    }
                    drainVideo()
                }
            }
            var videoDone = false
            var spins = 0
            while (!videoDone && spins++ < 300) {
                val outIdx = videoCodec.dequeueOutputBuffer(vInfo, 30_000)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (videoTrack < 0) {
                            videoTrack = muxer.addTrack(videoCodec.outputFormat)
                            if (hasAudio && audioFormat != null && audioTrack < 0) {
                                audioTrack = muxer.addTrack(audioFormat)
                            }
                            startIfReady()
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = videoCodec.getOutputBuffer(outIdx)
                        if (outBuf != null && vInfo.size > 0 && muxerStarted && videoTrack >= 0) {
                            outBuf.position(vInfo.offset)
                            outBuf.limit(vInfo.offset + vInfo.size)
                            muxer.writeSampleData(videoTrack, outBuf, vInfo)
                        }
                        val eos = vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        videoCodec.releaseOutputBuffer(outIdx, false)
                        if (eos) videoDone = true
                    }
                }
            }

            // Copy pre-encoded AAC into the final file (after muxer started).
            if (hasAudio && audioExtractor != null && muxerStarted && audioTrack >= 0) {
                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = audioExtractor.sampleTime.coerceAtLeast(0L)
                    info.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrack, buffer, info)
                    if (!audioExtractor.advance()) break
                }
                Log.i(TAG, "Muxed audio track into video")
            } else if (hasAudio) {
                Log.w(TAG, "Audio ready but muxer state blocked copy (started=$muxerStarted track=$audioTrack)")
            }
        } finally {
            runCatching { videoCodec.stop() }
            runCatching { videoCodec.release() }
            runCatching { audioExtractor?.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { tmpAudio.delete() }
        }
    }

    /** Encode mono PCM to a tiny AAC M4A so we can copy samples into the final muxer cleanly. */
    private fun encodePcmToM4a(pcm: ShortArray, out: File) {
        if (out.exists()) out.delete()
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var pts = 0L
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(20_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        if (offset >= pcm.size) {
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val count = minOf(1024, pcm.size - offset)
                            for (i in 0 until count) {
                                val s = pcm[offset + i]
                                buf.put((s.toInt() and 0xff).toByte())
                                buf.put(((s.toInt() shr 8) and 0xff).toByte())
                            }
                            codec.queueInputBuffer(inIdx, 0, count * 2, pts, 0)
                            pts += (count * 1_000_000L) / AUDIO_SAMPLE_RATE
                            offset += count
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 20_000)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0 && started && track >= 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        if (!out.exists() || out.length() < 256) {
            throw IllegalStateException("Temp AAC file empty")
        }
    }

    private fun pickYuvColorFormat(codec: MediaCodec): Int? {
        val info = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val preferred = intArrayOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            0x7F000789 // COLOR_FormatYUV420Flexible
        )
        for (p in preferred) {
            if (info.colorFormats.contains(p)) return p
        }
        return info.colorFormats.firstOrNull {
            it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        }
    }

    /** ARGB_8888 bitmap → NV12 (YUV420 semi-planar). */
    private fun bitmapToNv12(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIndex = 0
        var uvIndex = w * h
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    private fun decodeFileToPcmArray(file: File): ShortArray {
        val out = ArrayList<Short>(AUDIO_SAMPLE_RATE * 20)
        runCatching { decodeFileToPcm(file, out) }
            .onFailure { Log.w(TAG, "Decode failed ${file.name}", it) }
        return out.toShortArray()
    }

    private fun decodeFileToPcm(file: File, out: MutableList<Short>) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            return
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val srcRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(AUDIO_SAMPLE_RATE)
            .takeIf { it > 0 } ?: AUDIO_SAMPLE_RATE
        val srcChannels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)
            .coerceAtLeast(1)

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIndex >= 0 -> {
                    val buf = decoder.getOutputBuffer(outIndex)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shortBuf = ShortArray(info.size / 2)
                        buf.asShortBuffer().get(shortBuf)
                        appendResampledMono(shortBuf, srcChannels, srcRate, out)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        decoder.stop()
        decoder.release()
        extractor.release()
    }

    private fun appendResampledMono(
        samples: ShortArray,
        channels: Int,
        sampleRate: Int,
        out: MutableList<Short>
    ) {
        val mono = if (channels == 1) samples else ShortArray(samples.size / channels) { i ->
            var sum = 0
            for (c in 0 until channels) sum += samples[i * channels + c].toInt()
            (sum / channels).toShort()
        }
        if (sampleRate == AUDIO_SAMPLE_RATE) {
            mono.forEach { out.add(it) }
            return
        }
        val ratio = sampleRate.toDouble() / AUDIO_SAMPLE_RATE
        val newLen = (mono.size / ratio).toInt().coerceAtLeast(1)
        for (i in 0 until newLen) {
            val src = i * ratio
            val i0 = src.toInt().coerceIn(0, mono.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(mono.lastIndex)
            val frac = src - i0
            val v = mono[i0] * (1 - frac) + mono[i1] * frac
            out.add(v.toInt().toShort())
        }
    }

    private fun langShowsArabic(lang: AyahShareLang) = lang in setOf(
        AyahShareLang.ARABIC_ONLY, AyahShareLang.ARABIC_ENGLISH, AyahShareLang.ARABIC_URDU, AyahShareLang.ALL
    )

    private fun langShowsEnglish(lang: AyahShareLang) = lang in setOf(
        AyahShareLang.ENGLISH_ONLY, AyahShareLang.ARABIC_ENGLISH, AyahShareLang.ALL
    )

    private fun langShowsUrdu(lang: AyahShareLang) = lang in setOf(
        AyahShareLang.URDU_ONLY, AyahShareLang.ARABIC_URDU, AyahShareLang.ALL
    )

    private fun drawFrame(request: AyahVideoRequest, ayah: Ayah, highlight: SpokenLang): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F4EFE4"))

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F6F5B")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 160f, accent)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("Be Ummati", 48f, 70f, titlePaint)
        titlePaint.textSize = 32f
        titlePaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val phase = when (highlight) {
            SpokenLang.ARABIC -> " · Arabic"
            SpokenLang.ENGLISH -> " · English"
            SpokenLang.URDU -> " · Urdu"
            SpokenLang.NONE -> ""
        }
        canvas.drawText("${request.surahName} · ${ayah.key}$phase", 48f, 120f, titlePaint)

        var y = 220f
        val bodyWidth = WIDTH - 96

        fun drawBlock(text: String, size: Float, color: Int, rtl: Boolean, active: Boolean) {
            if (text.isBlank()) return
            if (active) {
                val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.parseColor("#D8EDE4")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(32f, y - 12f, WIDTH - 32f, y + size * 3.2f, 18f, 18f, band)
            }
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (active) Color.parseColor("#0F3D32") else color
                textSize = if (active) size + 4f else size
                typeface = Typeface.create(Typeface.SERIF, if (active) Typeface.BOLD else Typeface.NORMAL)
                alpha = if (active || highlight == SpokenLang.NONE) 255 else 140
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, bodyWidth)
                .setAlignment(if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(48f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 36f
        }

        val lang = request.lang
        if (langShowsArabic(lang)) {
            drawBlock(
                ayah.arabic(request.arabicFont), 48f, Color.parseColor("#1A1A1A"),
                rtl = true, active = highlight == SpokenLang.ARABIC
            )
        }
        if (langShowsEnglish(lang)) {
            drawBlock(
                ayah.english, 34f, Color.parseColor("#333333"),
                rtl = false, active = highlight == SpokenLang.ENGLISH
            )
        }
        if (langShowsUrdu(lang)) {
            drawBlock(
                ayah.urdu.ifBlank { "(Urdu translation unavailable)" }, 38f, Color.parseColor("#2A2A2A"),
                rtl = true, active = highlight == SpokenLang.URDU
            )
        }

        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A7265")
            textSize = 28f
        }
        canvas.drawText("— via Be Ummati", 48f, HEIGHT - 64f, foot)
        return bmp
    }
}
