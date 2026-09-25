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
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.codefixr.beummati.data.DailyQuranPack
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.ShareColorMood
import com.codefixr.beummati.data.ShareTemplate
import java.io.File
import java.io.FileOutputStream

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
    template: ShareTemplate = SettingsStore.shareTemplate.value,
    mood: ShareColorMood = SettingsStore.shareColorMood.value
) {
    val files = runCatching { writeCardImages(context, card, template, mood) }
        .onFailure { Log.w(TAG, "Couldn’t render the share image", it) }
        .getOrNull()
        .orEmpty()
    if (files.isEmpty()) {
        shareCard(context, card)
        return
    }
    if (files.size == 1) {
        shareImageFile(context, files.first(), card)
        return
    }
    // Split Daily Quran: share both parts as a multi-image send when the host supports it.
    val uris = files.mapNotNull { file ->
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        }.onFailure { Log.w(TAG, "Couldn’t expose ${file.name}", it) }.getOrNull()
    }
    if (uris.isEmpty()) {
        shareCard(context, card)
        return
    }
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        putExtra(Intent.EXTRA_TEXT, card.render())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

private fun shareImageFile(context: Context, file: File, card: ShareCard) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
    }.onFailure { Log.w(TAG, "Couldn’t expose the share image", it) }.getOrNull()
    if (uri == null) {
        shareCard(context, card)
        return
    }
    val mime = if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) {
        "image/jpeg"
    } else {
        "image/png"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
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

/** Cards aim for the 4:5 portrait the reference artwork uses, and only grow past it if forced. */
private const val CARD_RATIO = 1.25f
private const val CARD_MIN_RATIO = 1f

private const val BRAND = "Be Ummati"
private const val TAGLINE = "Soldier of Allah"
private const val JOIN_US = "JOIN US AND SHARE IT"
private const val HANDLE_LEFT = "@beummatireminders"
private const val HANDLE_RIGHT = "@beummati"

private const val BRAND_RED = 0xFFD8262C.toInt()
private const val CORAL = 0xFFE85A4F.toInt()
private const val PARCHMENT = 0xFFF6EFE2.toInt()
private const val CREAM = 0xFFF5EFE6.toInt()
private const val INK = 0xFF23201B.toInt()
private const val BRASS = 0xFF8A6A2F.toInt()
private const val FOREST_GREEN = 0xFF10322C.toInt()
private const val SLATE = 0xFF2F3E3B.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private const val W_LIGHT = 300
private const val W_NORMAL = 400
private const val W_MEDIUM = 500
private const val W_SEMIBOLD = 600
private const val W_BOLD = 700

// Branding metrics, quoted against CARD_WIDTH like everything else in this region.
private const val EDGE = 56f
private const val PILL_HEIGHT = 52f
private const val PILL_TO_TAG = 12f
private const val TAG_HEIGHT = 30f
private const val STRIP_HEIGHT = 58f
private const val HANDLE_HEIGHT = 34f
private const val BAND_GAP = 24f
private const val TOP_BADGE_HEIGHT = 96f
private const val BADGE_RADIUS = 7f

/** Reading order: the reference line closes the card, the way it does on the reference artwork. */
private enum class Role { TITLE, ARABIC, TRANSLIT, ENGLISH, URDU, SUBHEAD }

/** Roles that sit above the fold on the split templates. */
private val UPPER_ROLES = setOf(Role.TITLE, Role.ARABIC, Role.TRANSLIT)

/** Roles a template's translucent body box is drawn behind. */
private val BODY_ROLES = setOf(Role.ARABIC, Role.TRANSLIT, Role.ENGLISH, Role.URDU)

private enum class BadgeSpot { BOTTOM_LEFT, TOP_RIGHT }

private data class Ink(
    val size: Float,
    val color: Int,
    val weight: Int = W_NORMAL,
    val italic: Boolean = false,
    val rtl: Boolean = false,
    val serif: Boolean = true,
    val center: Boolean = false,
    val caps: Boolean = false,
    /** Extra tracking in ems — the caps reference lines want a lot, body text barely any. */
    val tracking: Float = 0f,
    /** Leading added between lines, as a fraction of the type size. */
    val lead: Float = 0.16f,
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
    val badge: BadgeSpot? = BadgeSpot.BOTTOM_LEFT,
    val taglineInk: Int = INK,
    val joinUs: Boolean = false,
    val handles: Boolean = false,
    val handleInk: Int = INK,
    val bodyBox: Int = 0,
    val railWidth: Float = 0f,
    val split: Boolean = false,
    val background: (canvas: Canvas, width: Int, height: Int, splitY: Float, scale: Float) -> Unit
)

private fun designFor(template: ShareTemplate): Design {
    // Legacy Design studio retired — share renders via [DailyQuranLab].
    error("Legacy design studio removed for $template")
}

/**
 * Lays [card] out in [template]. Sizes in [Design] are quoted against [CARD_WIDTH] and scaled to
 * [width], so the same code renders a share-ready PNG and the small previews in Settings.
 */
fun renderShareCard(
    card: ShareCard,
    template: ShareTemplate = SettingsStore.shareTemplate.value,
    width: Int = CARD_WIDTH,
    context: Context? = null,
    mood: ShareColorMood = SettingsStore.shareColorMood.value
): Bitmap {
    val ctx = context ?: error("Share render needs a Context for fonts")
    return DailyQuranLab.renderForShare(ctx, card, template, width, mood)
}

/** Total height of the laid-out blocks, including body-box padding and the gaps between them. */
private fun stackHeight(
    blocks: List<Pair<Role, StaticLayout>>,
    design: Design,
    boxPad: Float,
    scale: Float
): Float {
    var total = 0f
    blocks.forEachIndexed { index, (role, block) ->
        total += block.height
        if (design.bodyBox != 0 && role in BODY_ROLES) total += boxPad * 2f
        if (index != blocks.lastIndex) total += gapBetween(role, blocks[index + 1].first, scale)
    }
    return total
}

/** A fixed rhythm rather than one gap everywhere: the title hugs its body, the reference stands off. */
private fun gapBetween(above: Role, below: Role, scale: Float): Float = scale * when {
    above == Role.TITLE -> 28f
    below == Role.SUBHEAD -> 48f
    above == Role.ARABIC && below == Role.TRANSLIT -> 28f
    else -> 36f
}

/** Height the branding occupies above the bottom inset — mirrors the stack [drawBranding] draws. */
private fun bottomBandHeight(design: Design, scale: Float): Float {
    var total = 0f
    if (design.handles) total += HANDLE_HEIGHT * scale
    if (design.joinUs) {
        if (total > 0f) total += BAND_GAP * scale
        total += STRIP_HEIGHT * scale
    }
    if (design.badge == BadgeSpot.BOTTOM_LEFT) {
        if (total > 0f) total += BAND_GAP * scale
        total += (PILL_HEIGHT + PILL_TO_TAG + TAG_HEIGHT) * scale
    }
    return total
}

/**
 * Draws the brand mark and the optional footer, stacked upwards from the bottom inset in the same
 * order [bottomBandHeight] measures. Everything is placed against the canvas edges rather than the
 * content column, which is where the mark sits on the reference artwork; [railInset] only nudges
 * it clear of a template's colour rail.
 */
private fun drawBranding(
    canvas: Canvas,
    design: Design,
    width: Int,
    height: Int,
    railInset: Float,
    scale: Float
) {
    val edge = EDGE * scale
    val radius = BADGE_RADIUS * scale
    val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_RED }
    var bottom = height - edge

    if (design.handles) {
        val handle = brandPaint(22f * scale, design.handleInk, W_NORMAL, tracking = 0.02f)
        val rowTop = bottom - HANDLE_HEIGHT * scale
        val split = 110f * scale
        val rightWidth = handle.measureText(HANDLE_RIGHT)
        val total = handle.measureText(HANDLE_LEFT) + split + rightWidth
        val start = (width - total) / 2f
        val baseline = baselineIn(handle, rowTop, HANDLE_HEIGHT * scale)
        canvas.drawText(HANDLE_LEFT, start, baseline, handle)
        canvas.drawText(HANDLE_RIGHT, start + total - rightWidth, baseline, handle)
        bottom = rowTop - BAND_GAP * scale
    }

    if (design.joinUs) {
        val cta = brandPaint(25f * scale, WHITE, W_BOLD, tracking = 0.1f)
        val stripHeight = STRIP_HEIGHT * scale
        val stripTop = bottom - stripHeight
        val stripWidth = cta.measureText(JOIN_US) + 64f * scale
        val stripLeft = (width - stripWidth) / 2f
        canvas.drawRoundRect(RectF(stripLeft, stripTop, stripLeft + stripWidth, bottom), radius, radius, red)
        canvas.drawText(JOIN_US, stripLeft + 32f * scale, baselineIn(cta, stripTop, stripHeight), cta)
        bottom = stripTop - BAND_GAP * scale
    }

    when (design.badge) {
        BadgeSpot.BOTTOM_LEFT -> {
            val left = maxOf(edge, railInset + 28f * scale)
            val tagline = brandPaint(20f * scale, design.taglineInk, W_NORMAL)
            val taglineTop = bottom - TAG_HEIGHT * scale
            canvas.drawText(TAGLINE, left, baselineIn(tagline, taglineTop, TAG_HEIGHT * scale), tagline)

            val name = brandPaint(24f * scale, WHITE, W_BOLD, tracking = 0.02f)
            val pillHeight = PILL_HEIGHT * scale
            val pillTop = taglineTop - PILL_TO_TAG * scale - pillHeight
            val pillWidth = name.measureText(BRAND) + 40f * scale
            canvas.drawRoundRect(RectF(left, pillTop, left + pillWidth, pillTop + pillHeight), radius, radius, red)
            canvas.drawText(BRAND, left + 20f * scale, baselineIn(name, pillTop, pillHeight), name)
        }
        BadgeSpot.TOP_RIGHT -> {
            val name = brandPaint(24f * scale, WHITE, W_BOLD, tracking = 0.06f)
            val tagline = brandPaint(20f * scale, WHITE, W_NORMAL, tracking = 0.06f)
            val badgeHeight = TOP_BADGE_HEIGHT * scale
            val badgeWidth = maxOf(name.measureText(BRAND), tagline.measureText(TAGLINE)) + 52f * scale
            val badgeLeft = width - edge - badgeWidth
            canvas.drawRoundRect(
                RectF(badgeLeft, edge, badgeLeft + badgeWidth, edge + badgeHeight),
                radius,
                radius,
                red
            )
            val centreX = badgeLeft + badgeWidth / 2f
            canvas.drawText(BRAND, centreX - name.measureText(BRAND) / 2f, baselineIn(name, edge + 14f * scale, 34f * scale), name)
            canvas.drawText(
                TAGLINE,
                centreX - tagline.measureText(TAGLINE) / 2f,
                baselineIn(tagline, edge + badgeHeight - 44f * scale, 30f * scale),
                tagline
            )
        }
        null -> Unit
    }
}

private fun brandPaint(size: Float, colour: Int, weight: Int, tracking: Float = 0f) =
    TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        color = colour
        letterSpacing = tracking
        typeface = typefaceFor(serif = false, weight = weight, italic = false)
    }

/** Baseline that puts [paint]'s text on the optical centre line of a box. */
private fun baselineIn(paint: Paint, top: Float, boxHeight: Float): Float {
    val metrics = paint.fontMetrics
    return top + boxHeight / 2f - (metrics.ascent + metrics.descent) / 2f
}

/**
 * Real weights where the platform has them — the templates lean on 300/400/500/600 and go bold
 * only for the brand pill, so mapping everything onto [Typeface.BOLD] flattens the hierarchy.
 */
private fun typefaceFor(serif: Boolean, weight: Int, italic: Boolean): Typeface {
    val base = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Typeface.create(base, weight, italic)
    val style = when {
        italic && weight >= W_SEMIBOLD -> Typeface.BOLD_ITALIC
        italic -> Typeface.ITALIC
        weight >= W_SEMIBOLD -> Typeface.BOLD
        else -> Typeface.NORMAL
    }
    return Typeface.create(base, style)
}

private fun layout(text: String, width: Float, ink: Ink, scale: Float, typeScale: Float): StaticLayout {
    val size = ink.size * scale * typeScale
    val body = if (ink.caps) text.uppercase() else text
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        isLinearText = true
        textSize = size
        color = ink.color
        alpha = (ink.alpha * 255).toInt().coerceIn(0, 255)
        letterSpacing = ink.tracking
        typeface = typefaceFor(ink.serif, ink.weight, ink.italic)
        if (ink.shadow) setShadowLayer(10f * scale, 0f, 3f * scale, 0x26000000)
    }
    val alignment = when {
        ink.center -> Layout.Alignment.ALIGN_CENTER
        ink.rtl -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }
    return StaticLayout.Builder.obtain(body, 0, body.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setTextDirection(if (ink.rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setLineSpacing(size * ink.lead, 1f)
        .setIncludePad(false)
        .build()
}

private fun Canvas.fill(left: Float, top: Float, right: Float, bottom: Float, color: Int, alpha: Float = 1f) {
    drawRect(left, top, right, bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    })
}

private fun Canvas.gradient(width: Int, height: Int, top: Int, bottom: Int) {
    drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    })
}

private fun writeCardImages(
    context: Context,
    card: ShareCard,
    template: ShareTemplate,
    mood: ShareColorMood = SettingsStore.shareColorMood.value
): List<File> {
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val stale = System.currentTimeMillis() - 60 * 60_000L
    dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
    return DailyQuranLab.writeShareFiles(context, card, template, dir, mood)
}

// endregion
