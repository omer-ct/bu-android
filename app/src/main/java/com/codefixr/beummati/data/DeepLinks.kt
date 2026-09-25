package com.codefixr.beummati.data

import android.content.Intent
import android.net.Uri

/**
 * App deep links used by widgets and external intents.
 *
 * - `beummati://surah/{n}/{ayah?}`
 * - `beummati://salah`
 * - `beummati://home`
 */
object DeepLinks {
    const val SCHEME = "beummati"
    const val EXTRA_ROUTE = "beummati.extra.ROUTE"

    fun surah(surah: Int, ayah: Int? = null): Uri =
        if (ayah != null && ayah > 0) {
            Uri.parse("$SCHEME://surah/$surah/$ayah")
        } else {
            Uri.parse("$SCHEME://surah/$surah")
        }

    fun salah(): Uri = Uri.parse("$SCHEME://salah")
    fun home(): Uri = Uri.parse("$SCHEME://home")

    /** Resolve an [Intent] into a navigation route string, or null. */
    fun routeFrom(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() }?.let { return it }
        val data = intent.data ?: return null
        if (data.scheme != SCHEME) return null
        return when (data.host) {
            "surah" -> {
                val n = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return null
                val ayah = data.pathSegments.getOrNull(1)?.toIntOrNull()
                if (ayah != null && ayah > 0) "quran/$n?ayah=$ayah" else "quran/$n"
            }
            "salah" -> "salah"
            "home" -> "home"
            "qibla" -> "qibla"
            "hifz" -> "hifz"
            "plans" -> "plans"
            else -> null
        }
    }

    fun putRoute(intent: Intent, route: String): Intent =
        intent.putExtra(EXTRA_ROUTE, route)
}
