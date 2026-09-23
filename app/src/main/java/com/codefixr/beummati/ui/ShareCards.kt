package com.codefixr.beummati.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.codefixr.beummati.data.SettingsStore
import java.io.File

private const val TAG = "ShareCards"
private const val SIGNATURE = "— via Be Ummati"

/**
 * One shareable piece of content. Rendered as plain text by [ShareCard.render] and as a small
 * image by [shareCardImage]; both follow the layout of the iOS `ShareText.compose`:
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

    /** Body blocks in reading order, skipping anything empty or switched off in Settings. */
    fun blocks(includeUrdu: Boolean = SettingsStore.showUrdu.value): List<String> = buildList {
        if (arabic.isNotBlank()) add(arabic.trim())
        if (transliteration.isNotBlank() && SettingsStore.showTransliteration.value) add(transliteration.trim())
        if (english.isNotBlank()) add(english.trim())
        if (includeUrdu && urdu.isNotBlank()) add(urdu.trim())
    }

    fun render(includeUrdu: Boolean = SettingsStore.showUrdu.value): String = buildList {
        if (title.isNotBlank()) add(title.trim())
        subheading.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(blocks(includeUrdu))
        add(SIGNATURE)
    }.joinToString("\n\n")
}

/** Plain-text share — the baseline everywhere. */
fun shareCard(context: Context, card: ShareCard) {
    shareText(context, card.render())
}

/**
 * Renders [card] to a PNG in the cache directory and offers it to the share sheet. Falls back to
 * plain text when the bitmap can't be written, so the action never dead-ends.
 */
fun shareCardImage(context: Context, card: ShareCard) {
    val file = runCatching { writeCardImage(context, card) }
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

// region Bitmap card

private const val CARD_WIDTH = 1080
private const val CARD_PADDING = 72f
private const val PARCHMENT = 0xFFF6EFE2.toInt()
private const val INK = 0xFF23201B.toInt()
private const val BRASS = 0xFF8A6A2F.toInt()

private fun writeCardImage(context: Context, card: ShareCard): File {
    val inner = CARD_WIDTH - CARD_PADDING * 2
    val layouts = buildList {
        if (card.title.isNotBlank()) add(layout(card.title.trim(), inner, 46f, INK, bold = true))
        card.subheading.takeIf { it.isNotBlank() }?.let { add(layout(it, inner, 32f, BRASS, bold = true)) }
        if (card.arabic.isNotBlank()) add(layout(card.arabic.trim(), inner, 52f, INK, rtl = true))
        if (card.transliteration.isNotBlank() && SettingsStore.showTransliteration.value) {
            add(layout(card.transliteration.trim(), inner, 32f, INK, alpha = 0.7f, italic = true))
        }
        if (card.english.isNotBlank()) add(layout(card.english.trim(), inner, 38f, INK))
        if (SettingsStore.showUrdu.value && card.urdu.isNotBlank()) {
            add(layout(card.urdu.trim(), inner, 40f, INK, rtl = true))
        }
        add(layout(SIGNATURE, inner, 30f, BRASS, bold = true))
    }

    val gap = 36
    val height = (CARD_PADDING * 2).toInt() + layouts.sumOf { it.height } + gap * (layouts.size - 1).coerceAtLeast(0)
    val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(PARCHMENT)
    // Brass rule down the left edge, echoing the in-app cards.
    canvas.drawRect(0f, 0f, 10f, height.toFloat(), Paint().apply { color = BRASS })

    var y = CARD_PADDING
    layouts.forEach { layout ->
        canvas.save()
        canvas.translate(CARD_PADDING, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + gap
    }

    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    // Keep recent cards around: a receiving app may still be reading the one shared a moment ago.
    val stale = System.currentTimeMillis() - 60 * 60_000L
    dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
    val file = File(dir, "be-ummati-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

private fun layout(
    text: String,
    width: Float,
    size: Float,
    color: Int,
    bold: Boolean = false,
    italic: Boolean = false,
    rtl: Boolean = false,
    alpha: Float = 1f
): StaticLayout {
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        typeface = Typeface.create(
            Typeface.SERIF,
            when {
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        )
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
        .setAlignment(if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setLineSpacing(size * (if (rtl) 0.55f else 0.3f), 1f)
        .setIncludePad(false)
        .build()
}

// endregion
