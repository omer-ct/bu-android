package com.codefixr.beummati.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.codefixr.beummati.R
import kotlin.math.max
import kotlin.math.min

/**
 * Be Ummati “Daily Quran” share card — fixed IG Story canvas (9:16), matching the brand PNG
 * and the iOS `dailyQuranCard` layout.
 *
 * Long ayah policy (in order):
 * 1. Shrink Arabic / Urdu / English within bounds
 * 2. If still too tall → [Fit.TRANSLATION_ONLY] (drop Arabic, keep EN + UR + ref)
 * 3. Batch prebake can also emit [Fit.SPLIT_A] + [Fit.SPLIT_B] for very long ayahs
 */
object DailyQuranCard {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val BRAND_RED = 0xFFE6000D.toInt()
    private const val SLOGAN_TEAL = 0xFF52D1D6.toInt()
    private const val TITLE_LAVENDER = 0xFFB39DDB.toInt()
    private const val QURAN_TOP = 0xFF08334D.toInt() // ~0.03, 0.20, 0.30
    private const val QURAN_MID = 0xFF051729.toInt() // ~0.02, 0.09, 0.16
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val SLOGAN = "FOLLOW MUHAMMAD PBUH IF YOU LOVE ALLAH"

    enum class Fit {
        /** Full card: Arabic + Urdu + English. */
        FULL,
        /** Arabic omitted so translations fit cleanly. */
        TRANSLATION_ONLY,
        /** First half of a split (Arabic focus). */
        SPLIT_A,
        /** Second half of a split (translations focus). */
        SPLIT_B
    }

    data class Content(
        val arabic: String = "",
        val urdu: String = "",
        val english: String = "",
        /** e.g. `AL QURAN SURAH ASH SHURAA 42:50` */
        val reference: String = "",
        val fit: Fit = Fit.FULL
    )

    private var instrumentSerif: Typeface? = null
    private var uthmani: Typeface? = null
    private var urduFace: Typeface? = null
    private var facesLoaded = false

    fun warmFonts(context: Context) {
        if (facesLoaded) return
        val app = context.applicationContext
        instrumentSerif = runCatching { ResourcesCompat.getFont(app, R.font.instrument_serif) }.getOrNull()
        uthmani = runCatching { ResourcesCompat.getFont(app, R.font.uthmanic_hafs) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.me_quran) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.amiri) }.getOrNull()
        urduFace = runCatching { ResourcesCompat.getFont(app, R.font.gulzar) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.noto_nastaliq_urdu) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.noto_naskh_arabic) }.getOrNull()
        facesLoaded = true
    }

    /**
     * Premium floor: if we had to shrink below this, the card looks cramped — escalate fit.
     */
    private const val PREMIUM_SHRINK_FLOOR = 0.82f

    /**
     * Pick the best single-card fit for this ayah. Used by the batch generator and live share.
     * Returns [Fit.SPLIT_A] when even translation-only is too dense (caller should emit A+B).
     */
    fun chooseFit(context: Context, arabic: String, urdu: String, english: String): Fit {
        warmFonts(context)
        val arabicLen = arabic.length
        val englishLen = english.length
        // Hard gates for 2:282-class ayahs before measure.
        if (arabicLen > 380 || englishLen > 650) return Fit.SPLIT_A
        if (arabicLen > 220 || englishLen > 380) {
            val translationOnly = render(
                context,
                Content(arabic = "", urdu = urdu, english = english, reference = "PROBE", fit = Fit.TRANSLATION_ONLY),
                measureOnly = true
            )
            return if (translationOnly.overflow <= 0f && translationOnly.shrink >= PREMIUM_SHRINK_FLOOR) {
                Fit.TRANSLATION_ONLY
            } else {
                Fit.SPLIT_A
            }
        }
        val probe = render(
            context,
            Content(arabic = arabic, urdu = urdu, english = english, reference = "PROBE", fit = Fit.FULL),
            measureOnly = true
        )
        if (probe.overflow <= 0f && probe.shrink >= PREMIUM_SHRINK_FLOOR) return Fit.FULL
        val translationOnly = render(
            context,
            Content(arabic = "", urdu = urdu, english = english, reference = "PROBE", fit = Fit.TRANSLATION_ONLY),
            measureOnly = true
        )
        return if (translationOnly.overflow <= 0f && translationOnly.shrink >= PREMIUM_SHRINK_FLOOR) {
            Fit.TRANSLATION_ONLY
        } else {
            Fit.SPLIT_A
        }
    }

    fun render(context: Context, content: Content, width: Int = WIDTH, height: Int = HEIGHT): Bitmap {
        warmFonts(context)
        return render(context, content, measureOnly = false, width = width, height = height).bitmap!!
    }

    private data class RenderResult(val bitmap: Bitmap?, val overflow: Float, val shrink: Float = 1f)

    private fun render(
        context: Context,
        content: Content,
        measureOnly: Boolean,
        width: Int = WIDTH,
        height: Int = HEIGHT
    ): RenderResult {
        warmFonts(context)
        val scale = width / WIDTH.toFloat()
        val topH = height * 0.50f
        val botH = height - topH
        val rightMargin = 42f * scale
        val leftPad = 30f * scale
        val contentW = (width - leftPad - rightMargin).toInt().coerceAtLeast(1)

        val showArabic = content.fit != Fit.TRANSLATION_ONLY &&
            content.arabic.isNotBlank()
        val showUrdu = content.fit != Fit.SPLIT_A && content.urdu.isNotBlank()
        val showEnglish = content.fit != Fit.SPLIT_A && content.english.isNotBlank()

        val arabicSize = uthmaniSize(content.arabic) * scale
        val urduSize = 20f * scale
        val englishSize = 17f * scale
        val refSize = 13f * scale

        var arLayout = if (showArabic) {
            layout(
                content.arabic,
                contentW,
                textPaint(arabicSize, WHITE, face = uthmani, rtl = true),
                rtl = true,
                spacingAdd = 8f * scale
            )
        } else null

        val urduIsRtl = content.urdu.any { it.code in 0x0600..0x06FF }
        var urLayout = if (showUrdu) {
            layout(
                content.urdu,
                contentW,
                textPaint(
                    urduSize,
                    WHITE,
                    face = if (urduIsRtl) urduFace else null,
                    rtl = urduIsRtl,
                    alpha = 0.95f
                ),
                rtl = urduIsRtl,
                spacingAdd = 6f * scale
            )
        } else null

        var enLayout = if (showEnglish) {
            layout(
                content.english,
                (contentW - 12f * scale).toInt().coerceAtLeast(1),
                textPaint(englishSize, WHITE),
                spacingAdd = 4f * scale
            )
        } else null

        val refLayout = if (content.reference.isNotBlank() && content.fit != Fit.SPLIT_A) {
            layout(
                content.reference.uppercase(),
                (contentW - 24f * scale).toInt().coerceAtLeast(1),
                textPaint(refSize, WHITE, bold = true, trackingEm = 0.12f)
            )
        } else null

        var shrink = 1f
        fun topNeeded(): Float {
            var h = 0f
            arLayout?.let { h += it.height + 14f * scale }
            urLayout?.let { h += it.height }
            return h
        }
        fun botNeeded(): Float {
            var h = 28f * scale
            enLayout?.let { h += it.height + 16f * scale }
            refLayout?.let { h += it.height }
            h += 64f * scale
            return h
        }
        val topBudget = topH - 120f * scale
        val botBudget = botH - 40f * scale
        while ((topNeeded() > topBudget || botNeeded() > botBudget) && shrink > 0.62f) {
            shrink -= 0.04f
            if (showArabic) {
                arLayout = layout(
                    content.arabic,
                    contentW,
                    textPaint(arabicSize * shrink, WHITE, face = uthmani, rtl = true),
                    rtl = true,
                    spacingAdd = 8f * scale * shrink
                )
            }
            if (showUrdu) {
                urLayout = layout(
                    content.urdu,
                    contentW,
                    textPaint(
                        urduSize * shrink,
                        WHITE,
                        face = if (urduIsRtl) urduFace else null,
                        rtl = urduIsRtl,
                        alpha = 0.95f
                    ),
                    rtl = urduIsRtl,
                    spacingAdd = 6f * scale * shrink
                )
            }
            if (showEnglish) {
                enLayout = layout(
                    content.english,
                    (contentW - 12f * scale).toInt().coerceAtLeast(1),
                    textPaint(englishSize * max(0.75f, shrink), WHITE),
                    spacingAdd = 4f * scale
                )
            }
        }
        val overflow = max(0f, topNeeded() - topBudget) + max(0f, botNeeded() - botBudget)
        if (measureOnly) return RenderResult(null, overflow, shrink)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val topPaint = Paint().apply {
            isDither = true
            shader = LinearGradient(
                0f, 0f, 0f, topH,
                intArrayOf(QURAN_TOP, QURAN_MID, BLACK),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), topH, topPaint)
        canvas.drawRect(0f, topH, width.toFloat(), height.toFloat(), Paint().apply { color = BLACK })

        canvas.drawRect(
            0f, topH - 0.5f * scale, width.toFloat(), topH + 0.5f * scale,
            Paint().apply { color = 0x1AFFFFFF }
        )

        val title = "Daily Quran"
        val titlePaint = textPaint(40f * scale, SLOGAN_TEAL, face = instrumentSerif, light = true)
        titlePaint.letterSpacing = 0.015f
        titlePaint.shader = LinearGradient(
            16f * scale, 0f, 16f * scale + titlePaint.measureText(title), 0f,
            intArrayOf(SLOGAN_TEAL, TITLE_LAVENDER),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawText(title, 16f * scale, 22f * scale + 40f * scale, titlePaint)

        val topContentH = topNeeded()
        var y = (topH - topContentH) / 2f + 10f * scale
        y = max(y, 100f * scale)
        // Keep bottom padding of top panel (~28 like iOS)
        y = min(y, topH - topContentH - 28f * scale)

        arLayout?.let {
            canvas.save()
            canvas.translate(leftPad + (contentW - it.width) / 2f, y)
            it.draw(canvas)
            canvas.restore()
            y += it.height + 14f * scale
        }
        urLayout?.let {
            canvas.save()
            canvas.translate(leftPad + (contentW - it.width) / 2f, y)
            it.draw(canvas)
            canvas.restore()
        }

        var by = topH + 48f * scale
        enLayout?.let {
            canvas.save()
            canvas.translate(leftPad + 6f * scale + (contentW - 12f * scale - it.width) / 2f, by)
            it.draw(canvas)
            canvas.restore()
            by += it.height + 16f * scale
        }
        refLayout?.let {
            canvas.save()
            canvas.translate(leftPad + 12f * scale + (contentW - 24f * scale - it.width) / 2f, by)
            it.draw(canvas)
            canvas.restore()
        }

        // Brand badge — flush left red bar + tagline (matches iOS BeUmmatiBadge)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_RED }
        val brandPaint = textPaint(12f * scale, WHITE, bold = true)
        val brand = "Be Ummati"
        val brandPadX = 10f * scale
        val brandPadY = 5f * scale
        val brandW = brandPaint.measureText(brand) + brandPadX * 2f
        val brandH = 12f * scale + brandPadY * 2f
        val badgeY = height - 20f * scale - brandH - 18f * scale
        canvas.drawRect(0f, badgeY, brandW, badgeY + brandH, badgePaint)
        canvas.drawText(brand, brandPadX, badgeY + brandH - brandPadY - 2f * scale, brandPaint)
        val tagPaint = textPaint(10f * scale, WHITE, bold = true)
        canvas.drawText("Soldier of Allah", brandPadX, badgeY + brandH + 14f * scale, tagPaint)

        drawVerticalSlogan(canvas, width, height, scale)

        return RenderResult(bitmap, overflow, shrink)
    }

    private fun drawVerticalSlogan(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val paint = textPaint(10f * scale, SLOGAN_TEAL, bold = true, trackingEm = 0.28f)
        val w = paint.measureText(SLOGAN)
        canvas.save()
        canvas.translate(width - 12f * scale, height * 0.52f)
        canvas.rotate(-90f)
        canvas.drawText(SLOGAN, -w / 2f, 0f, paint)
        canvas.restore()
    }

    private fun uthmaniSize(text: String): Float {
        val n = text.length
        return when {
            n < 50 -> 34f
            n < 100 -> 28f
            n < 180 -> 24f
            else -> 20f
        }
    }

    private fun textPaint(
        size: Float,
        color: Int,
        face: Typeface? = null,
        rtl: Boolean = false,
        bold: Boolean = false,
        light: Boolean = false,
        alpha: Float = 1f,
        trackingEm: Float = 0f
    ): TextPaint {
        val base = face ?: Typeface.SANS_SERIF
        val tf = when {
            bold -> Typeface.create(base, Typeface.BOLD)
            light && android.os.Build.VERSION.SDK_INT >= 28 -> Typeface.create(base, 200, false)
            else -> base
        }
        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            typeface = tf
            if (trackingEm != 0f) letterSpacing = trackingEm
            textAlign = Paint.Align.LEFT
            if (rtl) textLocale = java.util.Locale("ar")
        }
    }

    private fun layout(
        text: String,
        width: Int,
        paint: TextPaint,
        rtl: Boolean = false,
        spacingAdd: Float = 0f
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(spacingAdd, 1f)
            .setIncludePad(false)
        if (rtl) builder.setTextDirection(TextDirectionHeuristics.RTL)
        return builder.build()
    }

    /** Split a long Arabic ayah roughly in half at a word boundary for two-card packs. */
    fun splitArabic(arabic: String): Pair<String, String> {
        val parts = arabic.trim().split(Regex("\\s+"))
        if (parts.size < 4) return arabic to ""
        val mid = parts.size / 2
        return parts.take(mid).joinToString(" ") to parts.drop(mid).joinToString(" ")
    }

    /**
     * Brand reference line. [surahName] is English (e.g. `Ash-Shuraa`); [ayahKey] is `42:50`.
     */
    fun formatReference(surahName: String, ayahKey: String): String {
        val clean = surahName
            .replace(Regex("^Surah\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .uppercase()
            .ifBlank { "QURAN" }
        return "AL QURAN SURAH $clean $ayahKey"
    }

    fun fromShareCard(card: ShareCard, fit: Fit = Fit.FULL): Content {
        val ayahKey = card.reference.trim().ifBlank {
            Regex("""(\d+:\d+)""").find(card.title)?.groupValues?.getOrNull(1).orEmpty()
        }
        val surahName = card.title
            .replace(ayahKey, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "" }
        val reference = when {
            card.kind.contains("qur", ignoreCase = true) || ayahKey.isNotBlank() ->
                formatReference(surahName, ayahKey.ifBlank { card.reference })
            card.reference.isNotBlank() -> card.reference.uppercase()
            else -> ""
        }
        // Prefer Nastaliq Urdu; fall back to transliteration/roman when Urdu is empty.
        val middle = card.urdu.ifBlank { card.transliteration }
        return Content(
            arabic = card.arabic,
            urdu = middle,
            english = card.english,
            reference = reference,
            fit = fit
        )
    }
}
