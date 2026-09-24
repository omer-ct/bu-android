package com.codefixr.beummati.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.ShareTemplate
import java.io.File

private const val TAG = "ShareCards"
private const val SIGNATURE = "— via Be Ummati"

/**
 * One shareable piece of content. Rendered as plain text by [ShareCard.render] and as an image by
 * [shareCardImage]; the text form follows the layout of the iOS `ShareText.compose`:
 *
 * ```
 * Title
 * Kind — Reference
 *
 * <arabic>
 *
 * <english>
 *
 * <urdu>
 *
 * — via Be Ummati
 * ```
 */
data class ShareCard(
    val title: String = "",
    val kind: String = "",
    val reference: String = "",
    val arabic: String = "",
    val transliteration: String = "",
    val english: String = "",
    val urdu: String = ""
) {
    /** Heading line pair, already merged — `Kind — Reference`, or whichever half exists. */
    val subheading: String
        get() = when {
            kind.isNotBlank() && reference.isNotBlank() -> "$kind — $reference"
            reference.isNotBlank() -> reference
            else -> kind
        }

    /**
     * Body blocks in reading order, skipping anything empty or switched off by the share language
     * toggles in Reading settings.
     */
    fun blocks(): List<String> = buildList {
        if (SettingsStore.shareArabic.value && arabic.isNotBlank()) add(arabic.trim())
        if (transliteration.isNotBlank() && SettingsStore.showTransliteration.value) add(transliteration.trim())
        if (SettingsStore.shareEnglish.value && english.isNotBlank()) add(english.trim())
        if (SettingsStore.shareUrdu.value && urdu.isNotBlank()) add(urdu.trim())
    }

    fun render(): String = buildList {
        if (title.isNotBlank()) add(title.trim())
        subheading.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(blocks())
        add(SIGNATURE)
    }.joinToString("\n\n")
}

/** Plain-text share — the baseline everywhere. */
fun shareCard(context: Context, card: ShareCard) {
    shareText(context, card.render())
}

/**
 * Renders [card] in [template] to a PNG in the cache directory and offers it to the share sheet.
 * Falls back to plain text when the bitmap can't be written, so the action never dead-ends.
 */
fun shareCardImage(
    context: Context,
    card: ShareCard,
    template: ShareTemplate = SettingsStore.shareTemplate.value
) {
    val file = runCatching { writeCardImage(context, card, template) }
        .onFailure { Log.w(TAG, "Couldn’t render the share image", it) }
        .getOrNull()
    if (file == null) {
        shareCard(context, card)
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    }.onFailure { Log.w(TAG, "Couldn’t expose the share image", it) }.getOrNull()
    if (uri == null) {
        shareCard(context, card)
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, card.render())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

// region Design studio
//
// Every template renders the same ShareCard fields over its own background and branding. The
// artwork each one is drawn from sits in `assets/share_refs` under a matching name.

private const val CARD_WIDTH = 1080

private const val BRAND = "Be Ummati"
private const val TAGLINE = "Soldier of Allah"
private const val JOIN_US = "J O I N   U S   A N D   S H A R E   I T"
private const val HANDLES = "@beummatireminders     @beummati"

private const val BRAND_RED = 0xFFD8262C.toInt()
private const val CORAL = 0xFFE85A4F.toInt()
private const val PARCHMENT = 0xFFF6EFE2.toInt()
private const val CREAM = 0xFFF5EFE6.toInt()
private const val INK = 0xFF23201B.toInt()
private const val BRASS = 0xFF8A6A2F.toInt()
private const val FOREST_GREEN = 0xFF10322C.toInt()
private const val SLATE = 0xFF2F3E3B.toInt()

private enum class Role { TITLE, SUBHEAD, ARABIC, TRANSLIT, ENGLISH, URDU }

/** Roles that sit above the fold on the split templates. */
private val UPPER_ROLES = setOf(Role.TITLE, Role.SUBHEAD, Role.ARABIC)

private enum class BadgeSpot { BOTTOM_LEFT, TOP_RIGHT }

private data class Ink(
    val size: Float,
    val color: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val rtl: Boolean = false,
    val serif: Boolean = true,
    val center: Boolean = false,
    val alpha: Float = 1f,
    val shadow: Boolean = false
)

/**
 * A template, reduced to the handful of knobs the renderer needs: what each role looks like, how
 * the branding sits, and how the background is painted.
 *
 * [split] tells the renderer to hand [background] the y where [UPPER_ROLES] end, so a template can
 * flip its colours across that line.
 */
private class Design(
    val inks: Map<Role, Ink>,
    val badge: BadgeSpot = BadgeSpot.BOTTOM_LEFT,
    val taglineInk: Int = INK,
    val joinUs: Boolean = false,
    val joinUsInk: Int = INK,
    val bodyBox: Int = 0,
    val railWidth: Float = 0f,
    val split: Boolean = false,
    val background: (canvas: Canvas, width: Int, height: Int, splitY: Float, scale: Float) -> Unit
)

private fun designFor(template: ShareTemplate): Design = when (template) {
    ShareTemplate.PARCHMENT -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, INK, bold = true),
            Role.SUBHEAD to Ink(32f, BRASS, bold = true),
            Role.ARABIC to Ink(52f, INK, rtl = true),
            Role.TRANSLIT to Ink(32f, INK, italic = true, alpha = 0.7f),
            Role.ENGLISH to Ink(38f, INK),
            Role.URDU to Ink(40f, INK, rtl = true)
        ),
        taglineInk = BRASS,
        railWidth = 10f
    ) { canvas, width, height, _, scale ->
        canvas.drawColor(PARCHMENT)
        canvas.fill(0f, 0f, 10f * scale, height.toFloat(), BRASS)
        canvas.fill(width - 10f * scale, 0f, width.toFloat(), height.toFloat(), BRASS, alpha = 0.25f)
    }

    ShareTemplate.NIGHT_CORAL -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, 0xFFFFFFFF.toInt(), bold = true, serif = false),
            Role.SUBHEAD to Ink(30f, CORAL, bold = true, serif = false),
            Role.ARABIC to Ink(52f, 0xFFFFFFFF.toInt(), rtl = true),
            Role.TRANSLIT to Ink(31f, CORAL, italic = true, serif = false),
            Role.ENGLISH to Ink(40f, 0xFFF3F1EE.toInt(), serif = false),
            Role.URDU to Ink(40f, 0xFFF3F1EE.toInt(), rtl = true)
        ),
        taglineInk = 0xFFFFFFFF.toInt()
    ) { canvas, width, height, _, scale ->
        canvas.gradient(width, height, 0xFF242424.toInt(), 0xFF111111.toInt())
        canvas.fill(0f, 0f, width.toFloat(), 8f * scale, CORAL)
    }

    ShareTemplate.QUOTE_WARM -> Design(
        inks = mapOf(
            Role.TITLE to Ink(48f, 0xFFFFFFFF.toInt(), bold = true, serif = false),
            Role.SUBHEAD to Ink(30f, 0xFFFFE9C9.toInt(), bold = true, serif = false),
            Role.ARABIC to Ink(52f, 0xFFFFFFFF.toInt(), rtl = true),
            Role.TRANSLIT to Ink(31f, 0xFFFFE9C9.toInt(), italic = true, serif = false),
            Role.ENGLISH to Ink(40f, 0xFFFFFFFF.toInt(), serif = false),
            Role.URDU to Ink(40f, 0xFFFFFFFF.toInt(), rtl = true)
        ),
        badge = BadgeSpot.TOP_RIGHT,
        joinUs = true,
        joinUsInk = 0xFFFFFFFF.toInt()
    ) { canvas, width, height, _, _ ->
        canvas.gradient(width, height, 0xFFF0871B.toInt(), 0xFFD1560A.toInt())
    }

    ShareTemplate.FOREST -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, 0xFFF1EFE6.toInt(), bold = true, serif = false),
            Role.SUBHEAD to Ink(30f, 0xFF63C7B2.toInt(), bold = true, serif = false),
            Role.ARABIC to Ink(52f, 0xFFF1EFE6.toInt(), rtl = true),
            Role.TRANSLIT to Ink(31f, 0xFF63C7B2.toInt(), italic = true, serif = false),
            Role.ENGLISH to Ink(38f, 0xFFF1EFE6.toInt(), serif = false),
            Role.URDU to Ink(40f, 0xFFF1EFE6.toInt(), rtl = true)
        ),
        taglineInk = 0xFFF1EFE6.toInt(),
        bodyBox = 0x14FFFFFF
    ) { canvas, width, height, _, _ ->
        canvas.gradient(width, height, 0xFF14403A.toInt(), FOREST_GREEN)
    }

    ShareTemplate.SPLIT_DUAL -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, FOREST_GREEN, bold = true, serif = false),
            Role.SUBHEAD to Ink(30f, BRAND_RED, bold = true, serif = false),
            Role.ARABIC to Ink(52f, FOREST_GREEN, rtl = true),
            Role.TRANSLIT to Ink(31f, 0xFFDCCFA8.toInt(), italic = true, serif = false),
            Role.ENGLISH to Ink(38f, CREAM, serif = false),
            Role.URDU to Ink(40f, CREAM, rtl = true)
        ),
        taglineInk = CREAM,
        split = true
    ) { canvas, width, height, splitY, _ ->
        canvas.fill(0f, 0f, width.toFloat(), splitY, CREAM)
        canvas.fill(0f, splitY, width.toFloat(), height.toFloat(), FOREST_GREEN)
    }

    ShareTemplate.DHIKR_SLATE -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, CREAM, bold = true, serif = false, center = true),
            Role.SUBHEAD to Ink(30f, 0xFFC9A227.toInt(), bold = true, serif = false, center = true),
            Role.ARABIC to Ink(56f, CREAM, rtl = true),
            Role.TRANSLIT to Ink(32f, 0xFF5B5347.toInt(), italic = true, center = true),
            Role.ENGLISH to Ink(38f, INK, center = true),
            Role.URDU to Ink(40f, INK, rtl = true)
        ),
        taglineInk = INK,
        split = true
    ) { canvas, width, height, splitY, scale ->
        canvas.fill(0f, 0f, width.toFloat(), height.toFloat(), CREAM)
        canvas.fill(0f, 0f, width.toFloat(), splitY, SLATE)
        // Faint diagonal weave so the band reads as fabric rather than flat fill.
        val thread = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            this.alpha = 10
            strokeWidth = 2f * scale
        }
        var x = -height.toFloat()
        while (x < width) {
            canvas.drawLine(x, splitY, x + splitY, 0f, thread)
            x += 22f * scale
        }
    }

    ShareTemplate.VERSE_BANNER -> Design(
        inks = mapOf(
            Role.TITLE to Ink(44f, FOREST_GREEN, bold = true),
            Role.SUBHEAD to Ink(30f, BRASS, bold = true),
            Role.ARABIC to Ink(54f, INK, rtl = true),
            Role.TRANSLIT to Ink(31f, BRASS, italic = true),
            Role.ENGLISH to Ink(38f, INK),
            Role.URDU to Ink(40f, INK, rtl = true)
        ),
        taglineInk = FOREST_GREEN,
        railWidth = 150f
    ) { canvas, width, height, _, scale ->
        canvas.drawColor(PARCHMENT)
        canvas.gradient(
            width = (150f * scale).toInt(),
            height = height,
            top = 0xFF1F5A4A.toInt(),
            bottom = FOREST_GREEN
        )
        canvas.fill(150f * scale, 0f, 156f * scale, height.toFloat(), BRASS, alpha = 0.55f)
    }

    ShareTemplate.PLUSH_LIGHT -> Design(
        inks = mapOf(
            Role.TITLE to Ink(46f, 0xFF2B2620.toInt(), bold = true, center = true, shadow = true),
            Role.SUBHEAD to Ink(30f, 0xFF8C7C63.toInt(), bold = true, center = true),
            Role.ARABIC to Ink(56f, 0xFF2B2620.toInt(), rtl = true, shadow = true),
            Role.TRANSLIT to Ink(32f, 0xFF8C7C63.toInt(), italic = true, center = true),
            Role.ENGLISH to Ink(40f, 0xFF2B2620.toInt(), italic = true, center = true, shadow = true),
            Role.URDU to Ink(40f, 0xFF2B2620.toInt(), rtl = true)
        ),
        taglineInk = 0xFF8C7C63.toInt()
    ) { canvas, width, height, _, scale ->
        canvas.gradient(width, height, 0xFFFBF7F0.toInt(), CREAM)
        // A hairline inset frame, like the stitched edge of the felt boards.
        val frame = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = 0xFFD9CDB8.toInt()
            isAntiAlias = true
        }
        val m = 34f * scale
        canvas.drawRoundRect(
            RectF(m, m, width - m, height - m),
            26f * scale,
            26f * scale,
            frame
        )
    }
}

/**
 * Lays [card] out in [template]. Sizes in [Design] are quoted against [CARD_WIDTH] and scaled to
 * [width], so the same code renders a share-ready PNG and the small previews in Settings.
 */
fun renderShareCard(
    card: ShareCard,
    template: ShareTemplate = SettingsStore.shareTemplate.value,
    width: Int = CARD_WIDTH
): Bitmap {
    val scale = width / CARD_WIDTH.toFloat()
    val design = designFor(template)
    val pad = 72f * scale
    val gap = 36f * scale
    val boxPad = 22f * scale
    val contentLeft = pad + design.railWidth * scale
    val inner = (width - contentLeft - pad).coerceAtLeast(1f)

    val parts = buildList {
        if (card.title.isNotBlank()) add(Role.TITLE to card.title.trim())
        card.subheading.takeIf { it.isNotBlank() }?.let { add(Role.SUBHEAD to it) }
        if (SettingsStore.shareArabic.value && card.arabic.isNotBlank()) add(Role.ARABIC to card.arabic.trim())
        if (card.transliteration.isNotBlank() && SettingsStore.showTransliteration.value) {
            add(Role.TRANSLIT to card.transliteration.trim())
        }
        if (SettingsStore.shareEnglish.value && card.english.isNotBlank()) add(Role.ENGLISH to card.english.trim())
        if (SettingsStore.shareUrdu.value && card.urdu.isNotBlank()) add(Role.URDU to card.urdu.trim())
    }.ifEmpty { listOf(Role.ENGLISH to BRAND) }

    val boxed = design.bodyBox != 0
    val blocks = parts.map { (role, text) -> role to layout(text, inner, design.inks.getValue(role), scale) }

    val topInset = if (design.badge == BadgeSpot.TOP_RIGHT) badgeHeight(scale, twoLine = true) + 28f * scale else 0f
    val brandHeight = brandingHeight(design, scale)

    // Walk the blocks once to fix their y positions, then we know the canvas height and the split.
    var y = pad + topInset
    val tops = FloatArray(blocks.size)
    var splitY = 0f
    blocks.forEachIndexed { index, (role, block) ->
        tops[index] = y + if (boxed) boxPad else 0f
        y += block.height + if (boxed) boxPad * 2 else 0f
        if (design.split && role in UPPER_ROLES) splitY = y + gap / 2f
        if (index != blocks.lastIndex) y += gap
    }
    val height = (y + 56f * scale + brandHeight + pad).toInt().coerceAtLeast(1)
    if (design.split && splitY <= 0f) splitY = height / 2f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    design.background(canvas, width, height, splitY, scale)

    blocks.forEachIndexed { index, (_, block) ->
        if (boxed) {
            canvas.drawRoundRect(
                RectF(contentLeft - boxPad, tops[index] - boxPad, width - pad + boxPad, tops[index] + block.height + boxPad),
                20f * scale,
                20f * scale,
                Paint().apply { color = design.bodyBox; isAntiAlias = true }
            )
        }
        canvas.save()
        canvas.translate(contentLeft, tops[index])
        block.draw(canvas)
        canvas.restore()
    }

    drawBranding(canvas, design, width, height, contentLeft, pad, scale)
    return bitmap
}

private fun brandingHeight(design: Design, scale: Float): Float {
    var total = 0f
    if (design.badge == BadgeSpot.BOTTOM_LEFT) total += badgeHeight(scale, twoLine = true)
    if (design.joinUs) {
        if (total > 0f) total += 20f * scale
        total += 62f * scale + 14f * scale + 38f * scale
    }
    return total
}

private fun badgeHeight(scale: Float, twoLine: Boolean): Float {
    val line = 62f * scale
    return if (twoLine) line + 46f * scale else line
}

/** [left] is the content edge, so templates with a colour rail keep their branding clear of it. */
private fun drawBranding(
    canvas: Canvas,
    design: Design,
    width: Int,
    height: Int,
    left: Float,
    pad: Float,
    scale: Float
) {
    val label = TextPaint().apply {
        isAntiAlias = true
        textSize = 30f * scale
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val pill = Paint().apply { color = BRAND_RED; isAntiAlias = true }

    when (design.badge) {
        BadgeSpot.TOP_RIGHT -> {
            val w = maxOf(label.measureText(BRAND), label.measureText(TAGLINE)) + 44f * scale
            val left = width - pad - w
            canvas.drawRect(left, pad - 18f * scale, width - pad, pad - 18f * scale + badgeHeight(scale, true), pill)
            label.color = 0xFFFFFFFF.toInt()
            canvas.drawText(BRAND, left + 22f * scale, pad + 20f * scale, label)
            canvas.drawText(TAGLINE, left + 22f * scale, pad + 64f * scale, label)
        }
        BadgeSpot.BOTTOM_LEFT -> {
            val footer = if (design.joinUs) 62f * scale + 14f * scale + 38f * scale + 20f * scale else 0f
            val top = height - pad - footer - badgeHeight(scale, true)
            val w = label.measureText(BRAND) + 40f * scale
            canvas.drawRect(left, top, left + w, top + 58f * scale, pill)
            label.color = 0xFFFFFFFF.toInt()
            canvas.drawText(BRAND, left + 20f * scale, top + 40f * scale, label)
            label.color = design.taglineInk
            canvas.drawText(TAGLINE, left, top + 58f * scale + 40f * scale, label)
        }
    }

    if (!design.joinUs) return

    val strip = TextPaint().apply {
        isAntiAlias = true
        textSize = 28f * scale
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val stripWidth = strip.measureText(JOIN_US) + 56f * scale
    val stripTop = height - pad - 38f * scale - 14f * scale - 54f * scale
    val stripLeft = (width - stripWidth) / 2f
    canvas.drawRect(stripLeft, stripTop, stripLeft + stripWidth, stripTop + 54f * scale, pill)
    canvas.drawText(JOIN_US, stripLeft + 28f * scale, stripTop + 36f * scale, strip)

    val handles = TextPaint().apply {
        isAntiAlias = true
        textSize = 30f * scale
        color = design.joinUsInk
        typeface = Typeface.SANS_SERIF
    }
    canvas.drawText(
        HANDLES,
        (width - handles.measureText(HANDLES)) / 2f,
        height - pad - 4f * scale,
        handles
    )
}

private fun layout(text: String, width: Float, ink: Ink, scale: Float): StaticLayout {
    val size = ink.size * scale
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        color = ink.color
        alpha = (ink.alpha * 255).toInt().coerceIn(0, 255)
        typeface = Typeface.create(
            if (ink.serif) Typeface.SERIF else Typeface.SANS_SERIF,
            when {
                ink.bold -> Typeface.BOLD
                ink.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        )
        if (ink.shadow) setShadowLayer(8f * scale, 0f, 3f * scale, 0x33000000)
    }
    val alignment = when {
        ink.rtl -> Layout.Alignment.ALIGN_OPPOSITE
        ink.center -> Layout.Alignment.ALIGN_CENTER
        else -> Layout.Alignment.ALIGN_NORMAL
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setTextDirection(if (ink.rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setLineSpacing(size * (if (ink.rtl) 0.55f else 0.3f), 1f)
        .setIncludePad(false)
        .build()
}

private fun Canvas.fill(left: Float, top: Float, right: Float, bottom: Float, color: Int, alpha: Float = 1f) {
    drawRect(left, top, right, bottom, Paint().apply {
        this.color = color
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    })
}

private fun Canvas.gradient(width: Int, height: Int, top: Int, bottom: Int) {
    drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    })
}

private fun writeCardImage(context: Context, card: ShareCard, template: ShareTemplate): File {
    val bitmap = renderShareCard(card, template)
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    // Keep recent cards around: a receiving app may still be reading the one shared a moment ago.
    val stale = System.currentTimeMillis() - 60 * 60_000L
    dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
    val file = File(dir, "be-ummati-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

// endregion
