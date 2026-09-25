package com.codefixr.beummati.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.codefixr.beummati.R
import com.codefixr.beummati.data.ShareColorMood
import com.codefixr.beummati.data.ShareTemplate
import com.codefixr.beummati.data.SettingsStore
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium Daily Quran share cards — 15 Claude designs, Instagram 4:5 (1080×1350).
 * Urdu is always Nastaliq. Mapped 1:1 from [ShareTemplate].
 */
object DailyQuranLab {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    enum class Style(val label: String) {
        MIHRAB("Mihrab — lit niche"),
        FOLIO("Folio — stone + emerald"),
        FAJR("Fajr — pre-dawn sky"),
        KUFIC_CIRCUIT("Kufic circuit"),
        INK_BLOOM("Ink bloom"),
        ZELLIJ_STACK("Zellij"),
        JADE_VELVET("Jade velvet"),
        CYANOTYPE("Cyanotype"),
        BASALT("Basalt"),
        NACRE("Nacre"),
        TERRAZZO_BONE("Terrazzo"),
        OXBLOOD_TAZHIB("Oxblood tazhib"),
        CONTOUR_TIDE("Contour tide"),
        RISO_DUO("Riso duo"),
        NIGHT_GIRIH("Night girih");

        companion object {
            fun from(template: ShareTemplate): Style = entries.first { it.name == template.name }
        }
    }

    enum class Fit { FULL, TRANSLATION_ONLY, ARABIC_ONLY }

    data class Content(
        val arabic: String = "",
        val urdu: String = "",
        val english: String = "",
        val reference: String = "",
        val fit: Fit = Fit.FULL
    )

    private var uthmani: Typeface? = null
    private var nastaliq: Typeface? = null
    private var serif: Typeface? = null
    private var facesLoaded = false

    fun warmFonts(context: Context) {
        if (facesLoaded) return
        val app = context.applicationContext
        uthmani = runCatching { ResourcesCompat.getFont(app, R.font.uthmanic_hafs) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.amiri) }.getOrNull()
        nastaliq = runCatching { ResourcesCompat.getFont(app, R.font.noto_nastaliq_urdu) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.gulzar) }.getOrNull()
        serif = runCatching { ResourcesCompat.getFont(app, R.font.instrument_serif) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(app, R.font.fraunces) }.getOrNull()
        facesLoaded = true
    }

    fun chooseFit(arabic: String, english: String): Fit {
        val bare = arabic.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        return if (bare.length > 280 || english.length > 400) Fit.TRANSLATION_ONLY else Fit.FULL
    }

    fun tier(arabic: String): Int {
        val n = arabic.replace(Regex("[\\u064B-\\u065F\\u0670]"), "").length
        return when {
            n <= 70 -> 0
            n <= 150 -> 1
            else -> 2
        }
    }

    fun fromShareCard(card: ShareCard, fit: Fit = Fit.FULL): Content {
        val ayahKey = Regex("""(\d+)\s*:\s*(\d+)""").find(card.reference)?.value
            ?: Regex("""(\d+)\s*:\s*(\d+)""").find(card.title)?.value
            ?: card.reference
        val surah = card.title
            .replace(ayahKey, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "" }
        val ref = when {
            card.kind.contains("qur", ignoreCase = true) || ayahKey.contains(":") ->
                "AL QURAN SURAH ${surah.uppercase().ifBlank { "QURAN" }} $ayahKey".trim()
            card.reference.isNotBlank() -> card.reference.uppercase()
            else -> ""
        }
        return Content(
            arabic = if (SettingsStoreShare.shareArabic) card.arabic else "",
            urdu = if (SettingsStoreShare.shareUrdu) card.urdu else "",
            english = if (SettingsStoreShare.shareEnglish) card.english else "",
            reference = ref,
            fit = fit
        )
    }

    fun render(context: Context, style: Style, content: Content): Bitmap {
        warmFonts(context)
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bmp).also { canvas ->
            when (style) {
                Style.MIHRAB -> paintMihrab(canvas, content)
                Style.FOLIO -> paintFolio(canvas, content)
                Style.FAJR -> paintFajr(canvas, content)
                Style.KUFIC_CIRCUIT -> paintKufic(canvas, content)
                Style.INK_BLOOM -> paintInkBloom(canvas, content)
                Style.ZELLIJ_STACK -> paintZellij(canvas, content)
                Style.JADE_VELVET -> paintJade(canvas, content)
                Style.CYANOTYPE -> paintCyanotype(canvas, content)
                Style.BASALT -> paintBasalt(canvas, content)
                Style.NACRE -> paintNacre(canvas, content)
                Style.TERRAZZO_BONE -> paintTerrazzo(canvas, content)
                Style.OXBLOOD_TAZHIB -> paintOxblood(canvas, content)
                Style.CONTOUR_TIDE -> paintContour(canvas, content)
                Style.RISO_DUO -> paintRiso(canvas, content)
                Style.NIGHT_GIRIH -> paintGirih(canvas, content)
            }
        }
        return bmp
    }

    fun renderForShare(
        context: Context,
        card: ShareCard,
        template: ShareTemplate,
        width: Int = WIDTH,
        mood: ShareColorMood = SettingsStore.shareColorMood.value
    ): Bitmap {
        warmFonts(context)
        val style = Style.from(template)
        val fit = chooseFit(card.arabic, card.english)
        // Live share: prefer full when possible; mega → translation-only single card
        val resolved = if (fit == Fit.TRANSLATION_ONLY) Fit.TRANSLATION_ONLY else Fit.FULL
        val content = fromShareCard(card, resolved).let {
            if (resolved == Fit.TRANSLATION_ONLY) it.copy(arabic = "") else it
        }
        val painted = render(context, style, content)
        val full = grade(painted, mood)
        if (width == WIDTH) return full
        val h = (width * HEIGHT / WIDTH.toFloat()).toInt()
        val scaled = Bitmap.createScaledBitmap(full, width, h, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    fun writeShareFiles(
        context: Context,
        card: ShareCard,
        template: ShareTemplate,
        dir: java.io.File,
        mood: ShareColorMood = SettingsStore.shareColorMood.value
    ): List<java.io.File> {
        warmFonts(context)
        val style = Style.from(template)
        val fit = chooseFit(card.arabic, card.english)
        val files = mutableListOf<java.io.File>()
        if (fit == Fit.TRANSLATION_ONLY && card.arabic.isNotBlank()) {
            val ref = fromShareCard(card).reference
            val bmpA = grade(
                render(
                    context, style,
                    Content(arabic = card.arabic, reference = "$ref · 1/2", fit = Fit.ARABIC_ONLY)
                ),
                mood
            )
            val fA = java.io.File(dir, "be-ummati-a-${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(fA).use { bmpA.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmpA.recycle()
            files += fA
            val bmpB = grade(
                render(
                    context, style,
                    fromShareCard(card, Fit.TRANSLATION_ONLY).copy(arabic = "", reference = "$ref · 2/2")
                ),
                mood
            )
            val fB = java.io.File(dir, "be-ummati-b-${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(fB).use { bmpB.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmpB.recycle()
            files += fB
        } else {
            val bmp = grade(render(context, style, fromShareCard(card, Fit.FULL)), mood)
            val f = java.io.File(dir, "be-ummati-${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
            files += f
        }
        return files
    }

    private fun grade(src: Bitmap, mood: ShareColorMood): Bitmap {
        if (mood == ShareColorMood.DEFAULT) return src
        val graded = mood.apply(src)
        if (graded !== src) src.recycle()
        return graded
    }

    // Avoid circular import with SettingsStore toggles at call time
    private object SettingsStoreShare {
        val shareArabic: Boolean get() = com.codefixr.beummati.data.SettingsStore.shareArabic.value
        val shareEnglish: Boolean get() = com.codefixr.beummati.data.SettingsStore.shareEnglish.value
        val shareUrdu: Boolean get() = com.codefixr.beummati.data.SettingsStore.shareUrdu.value
    }

    private fun sizes(t: Int): FloatArray = when (t) {
        0 -> floatArrayOf(78f, 46f, 34f)
        1 -> floatArrayOf(60f, 38f, 30f)
        else -> floatArrayOf(46f, 30f, 26f)
    }

    // ── Original three ───────────────────────────────────────────────────────

    private fun paintMihrab(canvas: Canvas, c: Content) {
        val abyss = 0xFF060D0C.toInt()
        val brass = 0xFFC79A4B.toInt()
        val bone = 0xFFF2EDE1.toInt()
        val sage = 0xFFC4CFC6.toInt()
        val t = tier(c.arabic)
        canvas.drawColor(abyss)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), abyss, 0xFF0A1412.toInt(), Shader.TileMode.CLAMP)
        })
        val arch = Path().apply {
            moveTo(70f, HEIGHT + 20f); lineTo(70f, 720f)
            quadTo(70f, 190f, WIDTH / 2f, 110f)
            quadTo(WIDTH - 70f, 190f, WIDTH - 70f, 720f)
            lineTo(WIDTH - 70f, HEIGHT + 20f); close()
        }
        canvas.drawPath(arch, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 120f, 0f, HEIGHT.toFloat(),
                intArrayOf(0xFF16302A.toInt(), 0xFF0D1B18.toInt(), 0xFF0A1614.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        })
        canvas.save(); canvas.clipPath(arch)
        canvas.drawCircle(540f, 470f, 620f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(540f, 470f, 620f, 0x292A5B4C, 0x002A5B4C, Shader.TileMode.CLAMP)
        })
        canvas.restore()
        canvas.drawPath(arch, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f; color = brass; alpha = 97
        })
        if (t < 2 && c.fit == Fit.FULL) {
            drawStar(canvas, 540f, 200f, 22f, brass)
            canvas.drawText("AYAH OF THE DAY", WIDTH / 2f, 280f,
                tp(20f, brass, bold = true, tracking = 0.28f).center())
        }
        val s = sizes(t)
        drawCenteredStack(canvas, c, if (t >= 2) 260f else 340f, 1160f, 780, s[0], s[1], s[2], bone, bone, sage, t < 2, brass)
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, 1210f, tp(20f, brass, bold = true, tracking = 0.18f).center())
        brandCenter(canvas, bone, 110)
    }

    private fun paintFolio(canvas: Canvas, c: Content) {
        val stone = 0xFFE9E4D8.toInt(); val ink = 0xFF141A18.toInt()
        val emerald = 0xFF0B5A45.toInt(); val bone = 0xFFFBF8F1.toInt(); val slate = 0xFF6C7570.toInt()
        val t = tier(c.arabic)
        canvas.drawColor(stone)
        canvas.drawCircle(880f, 400f, 430f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCD5C5.toInt() })
        val slabY = if (t >= 2) 1000f else 1040f
        canvas.drawRect(0f, slabY, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, slabY, 0f, HEIGHT.toFloat(), emerald, 0xFF073B2E.toInt(), Shader.TileMode.CLAMP)
        })
        canvas.drawRoundRect(RectF(72f, 72f, 148f, 148f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emerald })
        canvas.drawText("ب", 110f, 128f, tp(36f, bone, uthmani).center())
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH - 72f, 118f, tp(18f, slate, bold = true, tracking = 0.16f).right())
        val s = sizes(t)
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        val showEn = c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()
        var y = if (t == 0) 420f else 280f
        var shrink = 1f
        fun ar() = if (showAr) lay(c.arabic, 820, tp(s[0] * shrink, ink, uthmani, rtl = true), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 14f) else null
        fun ur() = if (showUr) lay(c.urdu, 760, tp(s[1] * shrink, ink, nastaliq, rtl = true, alpha = 0.82f), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 12f) else null
        var a = ar(); var u = ur()
        while ((a?.height ?: 0) + (u?.height ?: 0) + 80 > slabY - y - 40 && shrink > 0.72f) { shrink -= 0.04f; a = ar(); u = ur() }
        a?.let { canvas.save(); canvas.translate(WIDTH - 72f - it.width, y); it.draw(canvas); canvas.restore(); y += it.height + 40f }
        u?.let { canvas.save(); canvas.translate(WIDTH - 72f - it.width, y); it.draw(canvas); canvas.restore() }
        if (showEn) {
            val en = lay(c.english, 780, tp(s[2] * shrink, bone), sp = 8f)
            canvas.save(); canvas.translate(72f, slabY + 48f); en.draw(canvas); canvas.restore()
        }
        canvas.drawText("BE UMMATI", WIDTH - 72f, HEIGHT - 48f, tp(16f, bone, bold = true, tracking = 0.24f).right().also { it.alpha = 140 })
    }

    private fun paintFajr(canvas: Canvas, c: Content) {
        val amber = 0xFFE2A74E.toInt(); val light = 0xFFF5F1E8.toInt(); val mute = 0xFFC6D3DC.toInt()
        val t = tier(c.arabic)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(),
                intArrayOf(0xFF061021.toInt(), 0xFF0B1B33.toInt(), 0xFF17364C.toInt(), 0xFF2A5568.toInt(), 0xFF345F6E.toInt()),
                floatArrayOf(0f, 0.32f, 0.58f, 0.78f, 1f), Shader.TileMode.CLAMP)
        })
        val discCy = if (t >= 2) 400f else 480f
        val discR = if (t >= 2) 220f else 280f
        canvas.drawCircle(540f, discCy, discR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(540f, discCy, discR, 0x1CF6D9A4, 0x00F6D9A4, Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(540f, discCy, discR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = amber; alpha = 66
        })
        val washTop = if (t >= 2) 860f else 980f
        canvas.drawRect(0f, washTop, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = 0xB3061021.toInt() })
        Path().apply {
            moveTo(-40f, HEIGHT.toFloat()); lineTo(-40f, 1180f)
            cubicTo(200f, 1040f, 500f, 1120f, 700f, 1080f)
            cubicTo(900f, 1040f, 1100f, 1140f, WIDTH + 40f, 1160f)
            lineTo(WIDTH + 40f, HEIGHT.toFloat()); close()
        }.also { canvas.drawPath(it, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3061021.toInt() }) }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, 110f, tp(18f, amber, bold = true, tracking = 0.2f).center())
        val s = sizes(t)
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        val showEn = c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()
        var shrink = 1f
        fun arL() = if (showAr) lay(c.arabic, 780, tp(s[0] * shrink, light, uthmani, rtl = true), rtl = true, sp = 14f) else null
        var ar = arL()
        while ((ar?.height ?: 0) > discR * 1.4f && shrink > 0.7f) { shrink -= 0.04f; ar = arL() }
        ar?.let { canvas.save(); canvas.translate((WIDTH - it.width) / 2f, discCy - it.height / 2f); it.draw(canvas); canvas.restore() }
        var by = washTop + 28f
        val maxBy = HEIGHT - 70f
        if (showUr) {
            val ur = lay(c.urdu, 800, tp(s[1] * shrink, light, nastaliq, rtl = true, alpha = 0.9f), rtl = true, sp = 10f)
            if (by + ur.height < maxBy) { canvas.save(); canvas.translate((WIDTH - ur.width) / 2f, by); ur.draw(canvas); canvas.restore(); by += ur.height + 18f }
        }
        if (showEn) {
            val en = lay(c.english, 720, tp(s[2] * shrink, mute), sp = 7f)
            if (by + en.height < maxBy) { canvas.save(); canvas.translate((WIDTH - en.width) / 2f, by); en.draw(canvas); canvas.restore() }
        }
        brandCenter(canvas, light, 100)
    }

    // ── New twelve ───────────────────────────────────────────────────────────

    private fun paintKufic(canvas: Canvas, c: Content) {
        val bg = 0xFF0B1220.toInt(); val brass = 0xFFC9A24A.toInt(); val text = 0xFFF2F4F8.toInt(); val muted = 0xFF93A0B8.toInt()
        canvas.drawColor(bg)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6F5A2C.toInt(); alpha = 36; strokeWidth = 6f; style = Paint.Style.STROKE }
        val rnd = Random(42)
        for (row in 0 until 14) for (col in 0 until 11) {
            if (rnd.nextFloat() > 0.35f) continue
            val x = col * 96f; val y = row * 96f
            when (rnd.nextInt(3)) {
                0 -> canvas.drawLine(x, y + 48f, x + 72f, y + 48f, grid).also { canvas.drawLine(x + 72f, y + 48f, x + 72f, y + 96f, grid) }
                1 -> canvas.drawLine(x + 24f, y, x + 24f, y + 72f, grid).also { canvas.drawLine(x + 24f, y + 72f, x + 96f, y + 72f, grid) }
                else -> canvas.drawLine(x, y + 24f, x + 48f, y + 24f, grid).also { canvas.drawLine(x + 48f, y + 24f, x + 48f, y + 96f, grid) }
            }
        }
        canvas.drawRect(90f, 0f, 96f, HEIGHT.toFloat(), Paint().apply { color = brass })
        val s = sizes(tier(c.arabic))
        drawLeftSpineStack(canvas, c, 168f, 180f, 1180f, 820, s[0], s[1], s[2], text, text, muted)
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), 168f, HEIGHT - 80f, tp(22f, brass, bold = true, tracking = 0.12f))
        canvas.save(); canvas.translate(48f, HEIGHT - 120f); canvas.rotate(-90f)
        canvas.drawText("BE UMMATI", 0f, 0f, tp(18f, brass, bold = true, tracking = 0.2f).also { it.alpha = 140 })
        canvas.restore()
    }

    private fun paintInkBloom(canvas: Canvas, c: Content) {
        val paper = 0xFFF4F1EA.toInt(); val ink = 0xFF14161A.toInt(); val indigo = 0xFF2B3A67.toInt(); val seal = 0xFFB23A2E.toInt()
        canvas.drawColor(paper)
        canvas.drawCircle(820f, 980f, 520f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = indigo; alpha = 230 })
        canvas.drawCircle(700f, 1100f, 340f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = indigo; alpha = 100 })
        canvas.drawCircle(900f, 850f, 280f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7E8CB5.toInt(); alpha = 40 })
        val s = sizes(tier(c.arabic))
        drawRtlTopStack(canvas, c, 200f, 1100f, s[0], s[1], s[2], ink, ink, paper)
        canvas.drawRoundRect(RectF(72f, HEIGHT - 140f, 148f, HEIGHT - 64f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = seal })
        canvas.drawText("BU", 110f, HEIGHT - 88f, tp(22f, paper, bold = true).center())
    }

    private fun paintZellij(canvas: Canvas, c: Content) {
        val plaster = 0xFFE8E2D4.toInt(); val ink = 0xFF1A1A1A.toInt(); val lapis = 0xFF1B4B8F.toInt()
        val colors = intArrayOf(0xFF1B4B8F.toInt(), 0xFFD99A2B.toInt(), 0xFF7E2B2B.toInt(), 0xFF14634F.toInt())
        canvas.drawColor(plaster)
        for (band in listOf(0f to 280f, HEIGHT - 120f to HEIGHT.toFloat())) {
            var i = 0
            var x = 0f
            while (x < WIDTH) {
                canvas.drawRect(x, band.first, x + 100f, band.second, Paint().apply { color = colors[i % 4] })
                // simple star hint
                drawStar(canvas, x + 50f, (band.first + band.second) / 2f, 18f, plaster)
                x += 104f; i++
            }
        }
        val s = sizes(tier(c.arabic))
        drawCenteredStack(canvas, c, 340f, 1120f, 800, s[0], s[1], s[2], ink, ink, lapis, true, 0xFF7E2B2B.toInt())
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 150f, tp(20f, 0xFF7E2B2B.toInt(), bold = true, tracking = 0.14f).center())
        brandCenter(canvas, ink, 120)
    }

    private fun paintJade(canvas: Canvas, c: Content) {
        val deep = 0xFF06231C.toInt(); val champagne = 0xFFE7D7A8.toInt(); val gold = 0xFFC6A664.toInt(); val mist = 0xFF9BC3AE.toInt()
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = RadialGradient(540f, 430f, 900f, intArrayOf(0xFF14543E.toInt(), 0xFF0C3A2C.toInt(), deep), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })
        canvas.drawText("BE UMMATI", WIDTH / 2f, 80f, tp(18f, gold, bold = true, tracking = 0.3f).center().also { it.alpha = 140 })
        val s = sizes(tier(c.arabic)).let { floatArrayOf(it[0] + 8f, it[1], it[2]) }
        drawCenteredStack(canvas, c, 260f, 1180f, 820, s[0], s[1], s[2], champagne, mist, champagne, true, gold)
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 90f, tp(22f, gold, bold = true, tracking = 0.16f).center())
    }

    private fun paintCyanotype(canvas: Canvas, c: Content) {
        val prussian = 0xFF0A2A43.toInt(); val paper = 0xFFF7F9FA.toInt(); val chalk = 0xFFCBD9E2.toInt(); val amber = 0xFFD9A441.toInt()
        canvas.drawColor(prussian)
        canvas.drawCircle(300f, 400f, 380f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10456B.toInt(); alpha = 90 })
        canvas.drawCircle(800f, 900f, 420f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E6E99.toInt(); alpha = 70 })
        canvas.drawRect(24f, 24f, WIDTH - 24f, HEIGHT - 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 24f; color = paper; alpha = 46
        })
        val s = sizes(tier(c.arabic))
        drawRtlTopStack(canvas, c, 180f, 900f, s[0], s[1], s[2], paper, chalk, paper)
        if (c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()) {
            val en = lay(c.english, 700, tp(s[2], paper, alpha = 0.9f), sp = 8f)
            canvas.save(); canvas.translate(120f, 1000f); en.draw(canvas); canvas.restore()
        }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH - 80f, HEIGHT - 70f, tp(20f, amber, bold = true, tracking = 0.12f).right())
        canvas.drawText("be ummati", 100f, HEIGHT - 70f, tp(16f, chalk, tracking = 0.1f).also { it.alpha = 160 })
    }

    private fun paintBasalt(canvas: Canvas, c: Content) {
        val stone = 0xFF1C1C1A.toInt(); val slab = 0xFF262623.toInt(); val bone = 0xFFE6E3DC.toInt(); val lichen = 0xFF8A9A5B.toInt()
        canvas.drawColor(stone)
        listOf(0f to 420f, 420f to 920f, 920f to HEIGHT.toFloat()).forEachIndexed { i, (top, bot) ->
            val ox = if (i % 2 == 0) -30f else 30f
            canvas.drawRect(ox, top, WIDTH + ox, bot, Paint().apply { color = slab })
            canvas.drawRect(ox, top, WIDTH + ox, top + 4f, Paint().apply { color = 0xFF3A3A35.toInt() })
        }
        val s = sizes(tier(c.arabic)).let { floatArrayOf(it[0] + 14f, it[1], it[2]) }
        drawRtlTopStack(canvas, c, 220f, 880f, s[0], s[1], s[2], bone, bone, bone)
        if (c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()) {
            val en = lay(c.english, 780, tp(s[2], bone, alpha = 0.7f), sp = 8f)
            canvas.save(); canvas.translate(80f, 980f); en.draw(canvas); canvas.restore()
        }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), 80f, HEIGHT - 70f, tp(22f, lichen, bold = true, tracking = 0.14f))
        canvas.drawText("BE UMMATI", WIDTH - 80f, 90f, tp(18f, lichen, bold = true, tracking = 0.2f).right().also { it.alpha = 150 })
    }

    private fun paintNacre(canvas: Canvas, c: Content) {
        val pearl = 0xFFEFF4F3.toInt(); val ink = 0xFF23282B.toInt(); val thread = 0xFFB79A5B.toInt()
        canvas.drawColor(pearl)
        listOf(0xFFCFE7DF.toInt(), 0xFFCBDDF0.toInt(), 0xFFEFD9DE.toInt()).forEachIndexed { i, col ->
            canvas.drawCircle(200f + i * 280f, 300f + i * 200f, 500f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 70 })
        }
        for (r in 8..24) {
            canvas.drawCircle(-100f, HEIGHT + 100f, r * 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = thread; alpha = 20
            })
        }
        canvas.drawText("be ummati", WIDTH / 2f, 90f, tp(16f, thread, tracking = 0.35f).center())
        val s = sizes(tier(c.arabic))
        drawCenteredStack(canvas, c, 280f, 1120f, 760, s[0], s[1], s[2], ink, ink, ink, true, thread)
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 100f, tp(20f, thread, bold = true, tracking = 0.14f).center())
    }

    private fun paintTerrazzo(canvas: Canvas, c: Content) {
        val bone = 0xFFF1EDE4.toInt(); val ink = 0xFF1F2421.toInt()
        val chips = intArrayOf(0xFF1F2421.toInt(), 0xFF2E6B55.toInt(), 0xFF2A4FA3.toInt(), 0xFFE2A32B.toInt(), 0xFF8C3A32.toInt())
        canvas.drawColor(bone)
        val rnd = Random(7)
        repeat(180) {
            val cx = rnd.nextFloat() * WIDTH; val cy = rnd.nextFloat() * HEIGHT
            if (cy in 380f..980f && cx in 120f..960f) return@repeat
            canvas.drawCircle(cx, cy, 8f + rnd.nextFloat() * 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = chips[rnd.nextInt(chips.size)]; alpha = 200
            })
        }
        canvas.drawRoundRect(RectF(90f, 320f, WIDTH - 90f, 980f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bone })
        canvas.drawRoundRect(RectF(90f, 320f, WIDTH - 90f, 980f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFD8D2C4.toInt()
        })
        val s = sizes(tier(c.arabic))
        drawCenteredStack(canvas, c, 360f, 920f, 780, s[0], s[1], s[2], ink, ink, ink, false, ink)
        if (c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank() && c.fit == Fit.FULL) {
            // english already in stack; ok
        }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 80f, tp(20f, 0xFF8C3A32.toInt(), bold = true, tracking = 0.12f).center())
        canvas.drawCircle(WIDTH - 90f, HEIGHT - 90f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E6B55.toInt() })
        canvas.drawText("BU", WIDTH - 90f, HEIGHT - 82f, tp(16f, bone, bold = true).center())
    }

    private fun paintOxblood(canvas: Canvas, c: Content) {
        val ox = 0xFF4A0F16.toInt(); val gold = 0xFFD4AF5A.toInt(); val goldDim = 0xFF8C6F2E.toInt(); val ivory = 0xFFF0E6D2.toInt()
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), ox, 0xFF2E0A0F.toInt(), Shader.TileMode.CLAMP)
        })
        canvas.drawRect(72f, 72f, WIDTH - 72f, HEIGHT - 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = gold
        })
        canvas.drawRect(84f, 84f, WIDTH - 84f, HEIGHT - 84f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f; color = goldDim
        })
        listOf(160f to 160f, WIDTH - 160f to 160f, 160f to HEIGHT - 160f, WIDTH - 160f to HEIGHT - 160f).forEach { (x, y) ->
            drawStar(canvas, x, y, 36f, goldDim)
        }
        canvas.drawText("BE UMMATI", WIDTH / 2f, 100f, tp(16f, goldDim, bold = true, tracking = 0.3f).center())
        val s = sizes(tier(c.arabic))
        drawCenteredStack(canvas, c, 280f, 1120f, 780, s[0], s[1], s[2], ivory, ivory, gold, true, gold)
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 120f, tp(20f, goldDim, bold = true, tracking = 0.14f).center())
    }

    private fun paintContour(canvas: Canvas, c: Content) {
        val abyss = 0xFF041F25.toInt(); val line = 0xFF2E8C86.toInt(); val mint = 0xFF7FE0C4.toInt(); val pale = 0xFFE8FBF4.toInt(); val accent = 0xFFF2C879.toInt()
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), abyss, 0xFF06343C.toInt(), Shader.TileMode.CLAMP)
        })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = line }
        for (i in 4..28) {
            stroke.alpha = max(20, 90 - i * 2)
            canvas.drawCircle(760f, 1050f, i * 38f, stroke)
        }
        val s = sizes(tier(c.arabic))
        drawRtlTopStack(canvas, c, 180f, 780f, s[0], s[1], s[2], pale, mint, pale)
        if (c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()) {
            val en = lay(c.english, 680, tp(s[2], pale, alpha = 0.8f), sp = 8f)
            canvas.save(); canvas.translate(110f, 920f); en.draw(canvas); canvas.restore()
        }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), WIDTH - 80f, HEIGHT - 70f, tp(20f, accent, bold = true, tracking = 0.12f).right())
        canvas.drawText("be ummati", 90f, HEIGHT - 70f, tp(16f, mint, tracking = 0.1f).also { it.alpha = 150 })
    }

    private fun paintRiso(canvas: Canvas, c: Content) {
        val paper = 0xFFFAF7F0.toInt(); val blue = 0xFF2B3EE0.toInt(); val orange = 0xFFFF5A36.toInt(); val ink = 0xFF141414.toInt()
        canvas.drawColor(paper)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 560f, Paint().apply { color = blue; alpha = 220 })
        // curved bite
        canvas.drawCircle(540f, 560f, 420f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper })
        canvas.drawCircle(820f + 14f, 520f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = orange; alpha = 200 })
        val s = sizes(tier(c.arabic))
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        val showEn = c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()
        var y = 160f
        if (showAr) {
            val ar = lay(c.arabic, 820, tp(s[0], paper, uthmani, rtl = true), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 14f)
            canvas.save(); canvas.translate(WIDTH - 100f - ar.width, y); ar.draw(canvas); canvas.restore(); y = 600f
        }
        if (showUr) {
            val ur = lay(c.urdu, 780, tp(s[1], ink, nastaliq, rtl = true), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 12f)
            canvas.save(); canvas.translate(WIDTH - 100f - ur.width, max(y, 620f)); ur.draw(canvas); canvas.restore()
        }
        if (showEn) {
            val en = lay(c.english, 700, tp(s[2], ink), sp = 8f)
            canvas.save(); canvas.translate(90f, 980f); en.draw(canvas); canvas.restore()
        }
        if (c.reference.isNotBlank()) canvas.drawText(c.reference.uppercase(), 90f, HEIGHT - 70f, tp(22f, blue, bold = true, tracking = 0.1f))
        canvas.save(); canvas.translate(WIDTH - 40f, HEIGHT - 160f); canvas.rotate(-90f)
        canvas.drawText("BE UMMATI", 0f, 0f, tp(18f, orange, bold = true, tracking = 0.2f))
        canvas.restore()
    }

    private fun paintGirih(canvas: Canvas, c: Content) {
        val midnight = 0xFF060A18.toInt(); val line = 0xFF9AA7C7.toInt(); val gold = 0xFFE0B95F.toInt(); val text = 0xFFEDF0F8.toInt()
        canvas.drawColor(midnight)
        canvas.drawCircle(200f, 200f, 700f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF131C3A.toInt(); alpha = 100 })
        canvas.drawCircle(900f, 1100f, 600f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF131C3A.toInt(); alpha = 90 })
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = line; alpha = 36 }
        for (i in 0 until 8) for (j in 0 until 10) {
            val cx = i * 220f - 40f; val cy = j * 200f - 40f
            canvas.drawCircle(cx, cy, 70f, grid)
            canvas.drawCircle(cx + 40f, cy + 40f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; alpha = 100 })
        }
        // Clear center for text
        canvas.drawRoundRect(RectF(140f, 280f, WIDTH - 140f, 980f), 20f, 20f, Paint().apply { color = midnight })
        drawStar(canvas, WIDTH / 2f, 110f, 22f, gold)
        canvas.drawText("be ummati", WIDTH / 2f, 160f, tp(15f, line, tracking = 0.2f).center().also { it.alpha = 130 })
        val s = sizes(tier(c.arabic))
        drawCenteredStack(canvas, c, 300f, 1000f, 780, s[0], s[1], s[2], text, line, text, false, gold)
        if (c.reference.isNotBlank()) {
            canvas.drawText(c.reference.uppercase(), WIDTH / 2f, HEIGHT - 90f, tp(20f, gold, bold = true, tracking = 0.14f).center())
            canvas.drawRect(WIDTH / 2f - 160f, HEIGHT - 110f, WIDTH / 2f - 100f, HEIGHT - 108f, Paint().apply { color = gold; alpha = 100 })
            canvas.drawRect(WIDTH / 2f + 100f, HEIGHT - 110f, WIDTH / 2f + 160f, HEIGHT - 108f, Paint().apply { color = gold; alpha = 100 })
        }
    }

    // ── Shared drawing helpers ───────────────────────────────────────────────

    private fun drawCenteredStack(
        canvas: Canvas, c: Content, topY: Float, bottomY: Float, col: Int,
        arSize: Float, urSize: Float, enSize: Float,
        arColor: Int, urColor: Int, enColor: Int, showSep: Boolean, sepColor: Int
    ) {
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        val showEn = c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()
        var shrink = 1f
        fun builds(): List<Any> = buildList {
            if (showAr) add(lay(c.arabic, col, tp(arSize * shrink, arColor, uthmani, rtl = true), rtl = true, sp = 14f))
            if (showSep && showAr && (showUr || showEn)) add("SEP")
            if (showUr) add(lay(c.urdu, col, tp(urSize * shrink, urColor, nastaliq, rtl = true, alpha = 0.9f), rtl = true, sp = 12f))
            if (showEn) add(lay(c.english, (col * 0.92f).toInt(), tp(enSize * shrink, enColor, serif), sp = 8f))
        }
        var items = builds()
        fun h(): Float = items.sumOf { if (it is StaticLayout) it.height + 28.0 else 44.0 }.toFloat()
        while (h() > bottomY - topY && shrink > 0.68f) { shrink -= 0.04f; items = builds() }
        var y = topY + max(0f, (bottomY - topY - h()) / 2f)
        items.forEach { item ->
            when (item) {
                is StaticLayout -> {
                    canvas.save(); canvas.translate((WIDTH - item.width) / 2f, y); item.draw(canvas); canvas.restore()
                    y += item.height + 28f
                }
                else -> {
                    val mid = WIDTH / 2f
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sepColor; alpha = 120 }
                    canvas.drawRect(mid - 80f, y + 10f, mid - 14f, y + 12f, p)
                    canvas.drawRect(mid + 14f, y + 10f, mid + 80f, y + 12f, p)
                    canvas.drawCircle(mid, y + 11f, 5f, p)
                    y += 40f
                }
            }
        }
    }

    private fun drawRtlTopStack(
        canvas: Canvas, c: Content, topY: Float, maxY: Float,
        arSize: Float, urSize: Float, enSize: Float,
        arColor: Int, urColor: Int, enColor: Int
    ) {
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        var shrink = 1f
        fun ar() = if (showAr) lay(c.arabic, 820, tp(arSize * shrink, arColor, uthmani, rtl = true), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 14f) else null
        fun ur() = if (showUr) lay(c.urdu, 760, tp(urSize * shrink, urColor, nastaliq, rtl = true, alpha = 0.85f), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 12f) else null
        var a = ar(); var u = ur()
        while ((a?.height ?: 0) + (u?.height ?: 0) + 60 > maxY - topY && shrink > 0.7f) { shrink -= 0.04f; a = ar(); u = ur() }
        var y = topY
        a?.let { canvas.save(); canvas.translate(WIDTH - 100f - it.width, y); it.draw(canvas); canvas.restore(); y += it.height + 36f }
        u?.let { canvas.save(); canvas.translate(WIDTH - 100f - it.width, y); it.draw(canvas); canvas.restore() }
    }

    private fun drawLeftSpineStack(
        canvas: Canvas, c: Content, left: Float, topY: Float, bottomY: Float, col: Int,
        arSize: Float, urSize: Float, enSize: Float, arColor: Int, urColor: Int, enColor: Int
    ) {
        // Arabic RTL right-aligned to right margin; English left from spine
        val showAr = c.fit != Fit.TRANSLATION_ONLY && c.arabic.isNotBlank()
        val showUr = c.fit != Fit.ARABIC_ONLY && c.urdu.isNotBlank()
        val showEn = c.fit != Fit.ARABIC_ONLY && c.english.isNotBlank()
        var shrink = 1f
        fun builds(): List<StaticLayout> = buildList {
            if (showAr) add(lay(c.arabic, col, tp(arSize * shrink, arColor, uthmani, rtl = true), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 14f))
            if (showUr) add(lay(c.urdu, col - 40, tp(urSize * shrink, urColor, nastaliq, rtl = true, alpha = 0.9f), rtl = true, align = Layout.Alignment.ALIGN_OPPOSITE, sp = 12f))
            if (showEn) add(lay(c.english, col - 60, tp(enSize * shrink, enColor, serif), sp = 8f))
        }
        var items = builds()
        fun h() = items.sumOf { it.height + 32.0 }.toFloat()
        while (h() > bottomY - topY && shrink > 0.7f) { shrink -= 0.04f; items = builds() }
        var y = topY
        items.forEachIndexed { i, lay ->
            val x = if (i < 2 && (showAr || showUr)) WIDTH - 96f - lay.width else left
            canvas.save(); canvas.translate(x, y); lay.draw(canvas); canvas.restore()
            y += lay.height + 32f
        }
    }

    private fun brandCenter(canvas: Canvas, color: Int, alpha: Int) {
        canvas.drawText("BE UMMATI", WIDTH / 2f, HEIGHT - 42f, tp(16f, color, bold = true, tracking = 0.24f).center().also { it.alpha = alpha })
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; this.color = color; alpha = 180 }
        val path = Path()
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45 - 90).toDouble())
            val x = (cx + r * cos(a)).toFloat(); val y = (cy + r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, p)
    }

    private fun tp(
        size: Float, color: Int, face: Typeface? = null, rtl: Boolean = false,
        bold: Boolean = false, alpha: Float = 1f, tracking: Float = 0f
    ): TextPaint {
        val base = face ?: Typeface.SANS_SERIF
        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            typeface = if (bold) Typeface.create(base, Typeface.BOLD) else base
            if (tracking != 0f) letterSpacing = tracking
            if (rtl) textLocale = java.util.Locale("ar")
        }
    }

    private fun TextPaint.center() = apply { textAlign = Paint.Align.CENTER }
    private fun TextPaint.right() = apply { textAlign = Paint.Align.RIGHT }

    private fun lay(
        text: String, width: Int, paint: TextPaint, rtl: Boolean = false,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, sp: Float = 0f
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(align).setLineSpacing(sp, 1f).setIncludePad(false)
        .also { if (rtl) it.setTextDirection(TextDirectionHeuristics.RTL) }
        .build()
}
