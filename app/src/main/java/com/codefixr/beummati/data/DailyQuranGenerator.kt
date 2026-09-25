package com.codefixr.beummati.data

import android.content.Context
import android.util.Log
import com.codefixr.beummati.ui.DailyQuranCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Batch-renders Daily Quran cards for every ayah (or a surah range) into [DailyQuranPack.packDir].
 * Long ayahs: shrink → translation-only → split into `_a`/`_b` JPEGs.
 */
object DailyQuranGenerator {
    private const val TAG = "DailyQuranGen"

    data class Progress(
        val done: Int,
        val total: Int,
        val surah: Int,
        val ayah: Int,
        val fit: String
    )

    data class Result(
        val written: Int,
        val translationOnly: Int,
        val split: Int,
        val outDir: File
    )

    suspend fun generate(
        context: Context,
        surahRange: IntRange = 1..114,
        /** When set with a single-surah range, only that ayah is rendered. */
        onlyAyah: Int? = null,
        englishId: Int = SettingsStore.englishTranslationId.value,
        urduId: Int = SettingsStore.urduTranslationId.value,
        outDir: File = DailyQuranPack.packDir(context),
        onProgress: (Progress) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        DailyQuranCard.warmFonts(context)
        outDir.mkdirs()
        val counts = QuranAudioCache.SURAH_AYAH_COUNTS
        val total = when {
            onlyAyah != null && surahRange.first == surahRange.last -> 1
            else -> surahRange.sumOf { counts.getOrElse(it - 1) { 0 } }.coerceAtLeast(1)
        }
        var done = 0
        var written = 0
        var translationOnly = 0
        var split = 0

        for (surah in surahRange) {
            val detail = runCatching { QuranApi.surah(surah, englishId, urduId) }
                .onFailure { Log.w(TAG, "Surah $surah failed", it) }
                .getOrNull() ?: continue
            val name = detail.surah.englishName.ifBlank { "Surah $surah" }

            for (ayah in detail.ayahs) {
                if (onlyAyah != null && ayah.numberInSurah != onlyAyah) continue
                val fit = DailyQuranCard.chooseFit(context, ayah.arabic, ayah.urdu, ayah.english)
                val ref = DailyQuranCard.formatReference(name, ayah.key)
                when (fit) {
                    DailyQuranCard.Fit.FULL -> {
                        val bmp = DailyQuranCard.render(
                            context,
                            DailyQuranCard.Content(
                                arabic = ayah.arabic,
                                urdu = ayah.urdu,
                                english = ayah.english,
                                reference = ref,
                                fit = DailyQuranCard.Fit.FULL
                            )
                        )
                        DailyQuranPack.writeJpeg(bmp, File(outDir, DailyQuranPack.fileName(surah, ayah.numberInSurah)))
                        bmp.recycle()
                        written++
                    }
                    DailyQuranCard.Fit.TRANSLATION_ONLY -> {
                        val bmp = DailyQuranCard.render(
                            context,
                            DailyQuranCard.Content(
                                arabic = "",
                                urdu = ayah.urdu,
                                english = ayah.english,
                                reference = ref,
                                fit = DailyQuranCard.Fit.TRANSLATION_ONLY
                            )
                        )
                        DailyQuranPack.writeJpeg(bmp, File(outDir, DailyQuranPack.fileName(surah, ayah.numberInSurah)))
                        bmp.recycle()
                        written++
                        translationOnly++
                    }
                    DailyQuranCard.Fit.SPLIT_A, DailyQuranCard.Fit.SPLIT_B -> {
                        val (a1, a2) = DailyQuranCard.splitArabic(ayah.arabic)
                        // Card 1 — Arabic only (room to breathe). Prefer first half; if tiny, use full.
                        val arabicA = a1.ifBlank { ayah.arabic }
                        val bmpA = DailyQuranCard.render(
                            context,
                            DailyQuranCard.Content(
                                arabic = arabicA,
                                urdu = "",
                                english = "",
                                reference = "$ref · 1/2",
                                fit = DailyQuranCard.Fit.SPLIT_A
                            )
                        )
                        DailyQuranPack.writeJpeg(
                            bmpA,
                            File(outDir, DailyQuranPack.fileName(surah, ayah.numberInSurah, 'a'))
                        )
                        // Primary path resolves part 1.
                        DailyQuranPack.writeJpeg(
                            bmpA,
                            File(outDir, DailyQuranPack.fileName(surah, ayah.numberInSurah))
                        )
                        bmpA.recycle()

                        // Card 2 — translations. If both still too dense, English only.
                        val both = DailyQuranCard.Content(
                            arabic = "",
                            urdu = ayah.urdu,
                            english = ayah.english,
                            reference = "$ref · 2/2",
                            fit = DailyQuranCard.Fit.TRANSLATION_ONLY
                        )
                        val bothFit = DailyQuranCard.chooseFit(context, "", ayah.urdu, ayah.english)
                        val contentB = if (bothFit == DailyQuranCard.Fit.SPLIT_A) {
                            both.copy(urdu = "") // English-only fallback for mega ayahs
                        } else {
                            both
                        }
                        val bmpB = DailyQuranCard.render(context, contentB)
                        DailyQuranPack.writeJpeg(
                            bmpB,
                            File(outDir, DailyQuranPack.fileName(surah, ayah.numberInSurah, 'b'))
                        )
                        bmpB.recycle()
                        written += 2
                        split++
                    }
                }
                done++
                onProgress(Progress(done, total, surah, ayah.numberInSurah, fit.name))
            }
        }
        Result(written, translationOnly, split, outDir)
    }
}
