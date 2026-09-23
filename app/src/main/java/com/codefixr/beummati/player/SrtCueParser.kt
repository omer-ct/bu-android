package com.codefixr.beummati.player

import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.LectureAudioTrack
import java.nio.charset.Charset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SrtCue(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String
)

enum class SubtitleLang(val label: String) {
    ENGLISH("English"),
    URDU("Urdu"),
    ARABIC("Arabic")
}

/** Port of iOS `SRTCueParser`; SRT files live under `assets/AlQalam/srt/`. */
object SrtCueParser {
    private const val SRT_DIR = "AlQalam/srt"

    private val timeRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    /** NFC-normalized file name → real asset name (macOS may have written names as NFD). */
    private val assetIndex: Map<String, String> by lazy {
        Catalogs.listAssets(SRT_DIR).associateBy { Catalogs.nfc(it) }
    }

    fun assetName(file: String): String? {
        val withExt = if (file.substringAfterLast('/').contains('.')) file else "$file.srt"
        return assetIndex[Catalogs.nfc(withExt)] ?: assetIndex[Catalogs.nfc(file)]
    }

    fun bundleContains(file: String?): Boolean = !file.isNullOrBlank() && assetName(file) != null

    /** Resolve the best SRT filename for a chapter + language. */
    fun resolveFile(
        track: LectureAudioTrack?,
        srtEnglish: String?,
        srtUrdu: String?,
        lang: SubtitleLang
    ): String? = when (lang) {
        SubtitleLang.URDU ->
            srtUrdu?.takeIf { bundleContains(it) }
                ?: resolveFile(track, srtEnglish, srtUrdu, SubtitleLang.ENGLISH)
        SubtitleLang.ARABIC, SubtitleLang.ENGLISH ->
            srtEnglish?.takeIf { bundleContains(it) }
                ?: track?.srtFile?.takeIf { bundleContains(it) }
                ?: track?.srtFile
                ?: srtEnglish
    }

    fun load(file: String?): List<SrtCue> {
        if (file.isNullOrBlank()) return emptyList()
        val name = assetName(file) ?: return emptyList()
        val bytes = Catalogs.readAssetBytes("$SRT_DIR/$name") ?: return emptyList()
        for (charset in candidateCharsets(bytes)) {
            val cues = parse(String(bytes, charset))
            if (cues.isNotEmpty()) return cues
        }
        return emptyList()
    }

    private fun candidateCharsets(bytes: ByteArray): List<Charset> {
        val b0 = bytes.getOrNull(0)?.toInt()?.and(0xFF)
        val b1 = bytes.getOrNull(1)?.toInt()?.and(0xFF)
        val utf16First = (b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)
        return if (utf16First) {
            listOf(Charsets.UTF_16, Charsets.UTF_8, Charsets.ISO_8859_1)
        } else {
            listOf(Charsets.UTF_8, Charsets.ISO_8859_1, Charsets.UTF_16)
        }
    }

    fun parse(raw: String): List<SrtCue> {
        // Many Al Qalam SRTs are CRLF — splitting on "\n\n" alone would yield one block.
        val text = raw
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val cues = ArrayList<SrtCue>()
        var idx = 0
        for (block in text.split("\n\n")) {
            val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timeLine = lines.firstOrNull { it.contains("-->") } ?: lines[0]
            val m = timeRegex.find(timeLine) ?: continue
            val g = m.groupValues
            val start = stamp(g[1], g[2], g[3], g[4])
            var end = stamp(g[5], g[6], g[7], g[8])
            if (end <= start) end = start + 1.5

            val body = lines
                .filter { !it.contains("-->") && it.toIntOrNull() == null }
                .joinToString("\n")
                .trim()
            if (body.isEmpty()) continue
            cues.add(SrtCue(idx, start, end, body))
            idx++
        }

        cues.sortBy { it.start }
        for (i in 0 until cues.size - 1) {
            if (cues[i].end > cues[i + 1].start) {
                cues[i] = cues[i].copy(end = cues[i + 1].start)
            }
        }
        return cues
    }

    private fun stamp(h: String, m: String, s: String, frac: String): Double {
        val fracValue = frac.toDouble()
        val millis = when (frac.length) {
            1 -> fracValue / 10
            2 -> fracValue / 100
            else -> fracValue / 1000
        }
        return h.toDouble() * 3600 + m.toDouble() * 60 + s.toDouble() + millis
    }

    /**
     * When an SRT was timed against a longer master (leading silence) but the MP3 is trimmed,
     * shift cues so the first line lands near t=0. Never shift when the audio is the full master.
     */
    fun alignToAudioDuration(cues: List<SrtCue>, audioDuration: Double): Pair<List<SrtCue>, Double> {
        if (cues.size < 5 || audioDuration <= 30) return cues to 0.0
        val first = cues.first().start
        val last = cues.last().end
        if (first < 15) return cues to 0.0
        if (last <= audioDuration + 20) return cues to 0.0

        val contentSpan = last - first
        val looksTrimmed = abs(contentSpan - audioDuration) < max(90.0, audioDuration * 0.12) ||
            abs(contentSpan - audioDuration) < abs(last - audioDuration)
        if (!looksTrimmed) return cues to 0.0

        var offset = -first
        if (last + offset > audioDuration + 45) offset = audioDuration - last
        if (first + offset < -1) offset = -first

        val shifted = cues.mapIndexed { i, c ->
            val s = max(0.0, c.start + offset)
            SrtCue(i, s, max(s + 0.3, c.end + offset), c.text)
        }
        return shifted to offset
    }

    /** Strict window — never sticky leftover text between cues. */
    fun activeCue(cues: List<SrtCue>, time: Double): SrtCue? {
        if (cues.isEmpty() || !time.isFinite()) return null
        var lo = 0
        var hi = cues.size - 1
        var candidate = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].start <= time) {
                candidate = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (candidate < 0) return null
        val cue = cues[candidate]
        return if (time >= cue.start && time < cue.end) cue else null
    }

    fun isPrimarilyRtl(text: String): Boolean {
        var rtl = 0
        var ltr = 0
        var seen = 0
        var i = 0
        while (i < text.length && seen < 80) {
            val cp = text.codePointAt(i)
            when (cp) {
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> rtl++
                in 0x0041..0x007A, in 0x00C0..0x024F -> ltr++
            }
            i += Character.charCount(cp)
            seen++
        }
        return rtl > ltr
    }

    /** Keep only Arabic-script lines of a bilingual cue. */
    fun preferArabic(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) {
            val ar = lines.filter { isPrimarilyRtl(it) }
            if (ar.isNotEmpty()) return ar.joinToString("\n")
        }
        return text
    }

    /** Build approximate timed cues from a plain transcript when no SRT exists. */
    fun synthesize(text: String, duration: Double): List<SrtCue> {
        val cleaned = text.replace("\r\n", "\n").trim()
        if (duration <= 1 || cleaned.length <= 40) return emptyList()

        var parts = cleaned.split(Regex("[.\n!?۔]"))
            .map { it.trim() }
            .filter { it.length >= 12 }
        if (parts.size < 4) {
            parts = cleaned.lines().map { it.trim() }.filter { it.length >= 8 }
        }
        if (parts.isEmpty()) return emptyList()

        val weights = parts.map { max(it.length.toDouble(), 20.0) }
        val total = weights.sum()
        val pad = min(1.5, duration * 0.01)
        val cues = ArrayList<SrtCue>()
        var t = 0.0
        for ((i, part) in parts.withIndex()) {
            val slice = max(1.2, (weights[i] / total) * max(duration - pad, 1.0))
            val end = min(duration, t + slice)
            cues.add(SrtCue(i, t, end, part))
            t = end
            if (t >= duration - 0.05) break
        }
        cues.lastOrNull()?.let { last ->
            if (last.end < duration) cues[cues.size - 1] = last.copy(end = duration)
        }
        return cues
    }
}
