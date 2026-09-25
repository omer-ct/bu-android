package com.codefixr.beummati.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.codefixr.beummati.ui.DailyQuranCard
import com.codefixr.beummati.ui.ShareCard
import java.io.File
import java.io.FileOutputStream

/**
 * Pre-rendered Daily Quran share cards (`{surah}_{ayah}.jpg`, optional `_b` for split part 2).
 *
 * Lookup order:
 * 1. App files dir pack (full mushaf download / on-device generate)
 * 2. Bundled `assets/daily_quran/` samples
 * 3. `null` → caller live-renders via [DailyQuranCard]
 */
object DailyQuranPack {
    private const val TAG = "DailyQuranPack"
    private const val ASSET_DIR = "daily_quran"
    private const val FILES_DIR = "daily_quran"

    fun packDir(context: Context): File =
        File(context.applicationContext.filesDir, FILES_DIR).also { it.mkdirs() }

    fun fileName(surah: Int, ayah: Int, part: Char? = null): String =
        if (part == null || part == 'a') "%03d_%03d.jpg".format(surah, ayah)
        else "%03d_%03d_%c.jpg".format(surah, ayah, part)

    fun resolve(context: Context, surah: Int, ayah: Int): File? {
        val primary = File(packDir(context), fileName(surah, ayah))
        if (primary.isFile && primary.length() > 0) return primary
        val assetName = "$ASSET_DIR/${fileName(surah, ayah)}"
        return copyAssetIfPresent(context, assetName, File(packDir(context), fileName(surah, ayah)))
    }

    fun resolveParts(context: Context, surah: Int, ayah: Int): List<File> {
        val a = resolve(context, surah, ayah) ?: return emptyList()
        val b = File(packDir(context), fileName(surah, ayah, 'b'))
        val assetB = "$ASSET_DIR/${fileName(surah, ayah, 'b')}"
        val partB = when {
            b.isFile && b.length() > 0 -> b
            else -> copyAssetIfPresent(context, assetB, b)
        }
        return listOfNotNull(a, partB)
    }

    fun parseAyahKey(card: ShareCard): Pair<Int, Int>? {
        val raw = listOf(card.reference, card.title)
            .firstNotNullOfOrNull { Regex("""(\d+)\s*:\s*(\d+)""").find(it)?.groupValues }
            ?: return null
        val s = raw[1].toIntOrNull() ?: return null
        val a = raw[2].toIntOrNull() ?: return null
        if (s !in 1..114 || a < 1) return null
        return s to a
    }

    private fun copyAssetIfPresent(context: Context, assetPath: String, dest: File): File? {
        return runCatching {
            context.assets.open(assetPath).use { input ->
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest.takeIf { it.isFile && it.length() > 0 }
        }.onFailure {
            // Missing sample is normal — most ayahs aren't bundled.
        }.getOrNull()
    }

    fun writeJpeg(bitmap: Bitmap, dest: File, quality: Int = 92) {
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                Log.w(TAG, "JPEG compress failed for ${dest.name}")
            }
        }
    }
}
