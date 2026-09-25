package com.codefixr.beummati.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import kotlin.math.cos
import kotlin.math.sin

/**
 * Post-render colour grade for share images — works on every design without
 * rewriting the hand-painted palettes.
 */
enum class ShareColorMood(
    val label: String,
    /** Swatch shown in the studio picker (ARGB). */
    val swatch: Int,
    /** Hue rotate in degrees (0 = none). */
    private val hueDegrees: Float = 0f,
    /** 1 = unchanged; 0 = greyscale. */
    private val saturation: Float = 1f,
    /** Soft wash overlaid after the matrix (0 = skip). */
    private val wash: Int = 0
) {
    DEFAULT("Default", 0xFF8A8A8A.toInt()),
    WARM("Warm", 0xFFC47A3A.toInt(), hueDegrees = -18f, saturation = 1.12f, wash = 0x38C47A3A),
    COOL("Cool", 0xFF3A7AB8.toInt(), hueDegrees = 22f, saturation = 1.08f, wash = 0x303A7AB8),
    GOLD("Gold", 0xFFD4AF5A.toInt(), hueDegrees = -28f, saturation = 1.22f, wash = 0x40D4AF5A),
    EMERALD("Emerald", 0xFF1B6B4A.toInt(), hueDegrees = 95f, saturation = 1.15f, wash = 0x351B6B4A),
    ROSE("Rose", 0xFFB23A5A.toInt(), hueDegrees = -48f, saturation = 1.18f, wash = 0x38B23A5A),
    MIDNIGHT("Midnight", 0xFF1A2744.toInt(), hueDegrees = 205f, saturation = 0.92f, wash = 0x55121A2E),
    SAND("Sand", 0xFFD8C9A8.toInt(), hueDegrees = -10f, saturation = 0.62f, wash = 0x35E8DCC8),
    MONO("Mono", 0xFF2A2A2A.toInt(), saturation = 0f);

    fun apply(src: Bitmap): Bitmap {
        if (this == DEFAULT) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix())
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        if (wash != 0) {
            canvas.drawColor(wash, PorterDuff.Mode.OVERLAY)
        }
        return out
    }

    private fun matrix(): ColorMatrix {
        val sat = ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 2f)) }
        if (hueDegrees == 0f) return sat
        val hue = ColorMatrix().apply { setHueRotate(hueDegrees) }
        sat.postConcat(hue)
        return sat
    }
}

/** Hue-rotate ColorMatrix (CSS-style), degrees clockwise. */
private fun ColorMatrix.setHueRotate(degrees: Float) {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    set(
        floatArrayOf(
            lr + cos * (1 - lr) + sin * (-lr),
            lg + cos * (-lg) + sin * (-lg),
            lb + cos * (-lb) + sin * (1 - lb),
            0f, 0f,
            lr + cos * (-lr) + sin * 0.143f,
            lg + cos * (1 - lg) + sin * 0.140f,
            lb + cos * (-lb) + sin * (-0.283f),
            0f, 0f,
            lr + cos * (-lr) + sin * (-(1 - lr)),
            lg + cos * (-lg) + sin * lg,
            lb + cos * (1 - lb) + sin * lb,
            0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}
