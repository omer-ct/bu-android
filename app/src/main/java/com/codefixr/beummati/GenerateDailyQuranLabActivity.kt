package com.codefixr.beummati

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.codefixr.beummati.data.DailyQuranPack
import com.codefixr.beummati.data.QuranApi
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.ui.DailyQuranLab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Claude design lab — Mihrab / Folio / Fajr × S/M/L/XL with Nastaliq Urdu.
 *
 * adb shell am start -n com.codefixr.beummati/.GenerateDailyQuranLabActivity
 */
class GenerateDailyQuranLabActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class Sample(val label: String, val surah: Int, val ayah: Int)

    private val samples = listOf(
        Sample("S", 112, 1),
        Sample("M", 42, 50),
        Sample("L", 2, 255),
        Sample("XL", 2, 282)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "Claude lab: Mihrab · Folio · Fajr…", Toast.LENGTH_SHORT).show()
        scope.launch(Dispatchers.IO) {
            val export = File(getExternalFilesDir(null), "daily_quran_lab").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            DailyQuranLab.warmFonts(applicationContext)
            val enId = SettingsStore.englishTranslationId.value
            val urId = SettingsStore.urduTranslationId.value
            var n = 0
            try {
                for (sample in samples) {
                    val detail = QuranApi.surah(sample.surah, enId, urId)
                    val ayah = detail.ayahs.first { it.numberInSurah == sample.ayah }
                    val name = detail.surah.englishName.ifBlank { "Surah ${sample.surah}" }
                    val ref = "AL QURAN SURAH ${name.uppercase()} ${ayah.key}"
                    val fit = DailyQuranLab.chooseFit(ayah.arabic, ayah.english)
                    for (style in DailyQuranLab.Style.entries) {
                        if (fit == DailyQuranLab.Fit.TRANSLATION_ONLY) {
                            val ar = DailyQuranLab.render(
                                applicationContext, style,
                                DailyQuranLab.Content(
                                    arabic = ayah.arabic,
                                    reference = "$ref · 1/2",
                                    fit = DailyQuranLab.Fit.ARABIC_ONLY
                                )
                            )
                            DailyQuranPack.writeJpeg(
                                ar,
                                File(export, "${style.name.lowercase()}_${sample.label}_ar_${"%03d_%03d".format(sample.surah, sample.ayah)}.jpg"),
                                94
                            )
                            ar.recycle()
                            n++
                            val tr = DailyQuranLab.render(
                                applicationContext, style,
                                DailyQuranLab.Content(
                                    urdu = ayah.urdu,
                                    english = ayah.english,
                                    reference = "$ref · 2/2",
                                    fit = DailyQuranLab.Fit.TRANSLATION_ONLY
                                )
                            )
                            DailyQuranPack.writeJpeg(
                                tr,
                                File(export, "${style.name.lowercase()}_${sample.label}_tr_${"%03d_%03d".format(sample.surah, sample.ayah)}.jpg"),
                                94
                            )
                            tr.recycle()
                            n++
                        } else {
                            val bmp = DailyQuranLab.render(
                                applicationContext, style,
                                DailyQuranLab.Content(
                                    arabic = ayah.arabic,
                                    urdu = ayah.urdu,
                                    english = ayah.english,
                                    reference = ref,
                                    fit = DailyQuranLab.Fit.FULL
                                )
                            )
                            DailyQuranPack.writeJpeg(
                                bmp,
                                File(export, "${style.name.lowercase()}_${sample.label}_${"%03d_%03d".format(sample.surah, sample.ayah)}.jpg"),
                                94
                            )
                            bmp.recycle()
                            n++
                        }
                    }
                }
                Log.i(TAG, "Lab done written=$n → ${export.absolutePath}")
                runOnUiThread {
                    Toast.makeText(this@GenerateDailyQuranLabActivity, "Lab done: $n images", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lab failed", e)
                runOnUiThread {
                    Toast.makeText(this@GenerateDailyQuranLabActivity, "Lab failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "DailyQuranLab"
    }
}
