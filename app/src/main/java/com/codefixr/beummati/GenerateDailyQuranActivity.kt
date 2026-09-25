package com.codefixr.beummati

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.codefixr.beummati.data.DailyQuranGenerator
import com.codefixr.beummati.data.DailyQuranPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Headless batch renderer for Daily Quran cards.
 *
 * ```
 * adb shell am start -n com.codefixr.beummati/.GenerateDailyQuranActivity \
 *   --ei from 1 --ei to 1
 * ```
 *
 * Writes JPEGs to app filesDir `daily_quran/`, then mirrors to
 * `Android/data/.../files/daily_quran_export/` for `adb pull`.
 */
class GenerateDailyQuranActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val from = intent.getIntExtra("from", 1).coerceIn(1, 114)
        val to = intent.getIntExtra("to", from).coerceIn(from, 114)
        val onlyAyah = intent.getIntExtra("ayah", 0).takeIf { it > 0 }
        val label = if (onlyAyah != null && from == to) "$from:$onlyAyah" else "surah $from–$to"
        Toast.makeText(this, "Daily Quran: generating $label…", Toast.LENGTH_LONG).show()
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                DailyQuranGenerator.generate(
                    context = applicationContext,
                    surahRange = from..to,
                    onlyAyah = onlyAyah,
                    outDir = DailyQuranPack.packDir(applicationContext)
                ) { p ->
                    if (p.done % 25 == 0 || p.done == p.total) {
                        Log.i(TAG, "Daily Quran ${p.done}/${p.total} ${p.surah}:${p.ayah} ${p.fit}")
                    }
                }
            }
            result.onSuccess { r ->
                val export = File(getExternalFilesDir(null), "daily_quran_export").also {
                    it.deleteRecursively()
                    it.mkdirs()
                }
                val prefix = if (from == to) "%03d_".format(from) else null
                r.outDir.listFiles()
                    ?.filter { f -> prefix == null || f.name.startsWith(prefix) }
                    ?.filter { f -> onlyAyah == null || f.name.contains("_%03d".format(onlyAyah)) }
                    ?.forEach { src ->
                        src.copyTo(File(export, src.name), overwrite = true)
                    }
                Log.i(
                    TAG,
                    "Done written=${r.written} translationOnly=${r.translationOnly} split=${r.split} → ${export.absolutePath}"
                )
                runOnUiThread {
                    Toast.makeText(
                        this@GenerateDailyQuranActivity,
                        "Daily Quran done: ${r.written} images (${r.translationOnly} translation-only, ${r.split} split)",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }.onFailure { e ->
                Log.e(TAG, "Generate failed", e)
                runOnUiThread {
                    Toast.makeText(this@GenerateDailyQuranActivity, "Generate failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GenerateDailyQuran"
    }
}
