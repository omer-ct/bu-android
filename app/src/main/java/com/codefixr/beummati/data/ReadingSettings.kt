package com.codefixr.beummati.data

import com.codefixr.beummati.R

/** Preset language combinations, mirroring the iOS `AppReadingLanguage`. */
enum class AppReadingLanguage(val label: String) {
    ALL_THREE("Arabic + English + Urdu"),
    ARABIC_ENGLISH("Arabic + English"),
    ARABIC_URDU("Arabic + Urdu");

    val showsEnglish: Boolean get() = this != ARABIC_URDU
    val showsUrdu: Boolean get() = this != ARABIC_ENGLISH
}

/** How translation prose is aligned. Arabic and Urdu always stay right-aligned. */
enum class TextAlignMode(val label: String) {
    CENTER("Center"),
    NATURAL("Natural (RTL)"),
    LEADING("Leading")
}

/** Gloss language for word-by-word (quran.com `language=` on the words endpoint). */
enum class WordByWordLang(val label: String, val apiCodes: List<String>) {
    ENGLISH("English", listOf("en")),
    URDU("Urdu", listOf("ur")),
    BOTH("English + Urdu", listOf("en", "ur"));

    companion object {
        fun named(raw: String?): WordByWordLang? =
            raw?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

/**
 * Typefaces offered per script. [fontRes] points at a bundled file in `res/font`; the entries
 * without one fall back to a platform family so the pickers still work on a slim build.
 */
enum class ScriptFont(val label: String, val fontRes: Int? = null) {
    SYSTEM("System"),
    SERIF("Serif"),
    MONO("Monospaced"),

    INDO_PAK("Indo-Pak Nastaleeq", R.font.indopak_nastaleeq),
    UTHMANI("Uthmanic Hafs", R.font.uthmanic_hafs),
    ME_QURAN("me Quran", R.font.me_quran),
    AMIRI("Amiri", R.font.amiri),
    NOTO_NASKH("Noto Naskh Arabic", R.font.noto_naskh_arabic),
    SCHEHERAZADE("Scheherazade New", R.font.scheherazade_new),
    LATEEF("Lateef", R.font.lateef),
    HARMATTAN("Harmattan", R.font.harmattan),
    IBM_PLEX("IBM Plex Sans Arabic", R.font.ibm_plex_sans_arabic),
    REEM_KUFI("Reem Kufi", R.font.reem_kufi),
    MEHR_NASTALIQ("Mehr Nastaliq", R.font.mehr_nastaliq),
    NOTO_NASTALIQ("Noto Nastaliq Urdu", R.font.noto_nastaliq_urdu),
    GULZAR("Gulzar", R.font.gulzar),
    FRAUNCES("Fraunces", R.font.fraunces),
    INSTRUMENT_SERIF("Instrument Serif", R.font.instrument_serif);

    /**
     * Nastaliq and Indo-Pak faces are drawn for Indo-Pak orthography, so they read the
     * `text_indopak` copy of an ayah. Everything else takes Uthmani, as on iOS.
     */
    val prefersIndoPak: Boolean
        get() = this == INDO_PAK || this == MEHR_NASTALIQ || this == NOTO_NASTALIQ || this == GULZAR

    companion object {
        val arabicChoices = listOf(
            INDO_PAK, UTHMANI, ME_QURAN, AMIRI, NOTO_NASKH, SCHEHERAZADE,
            LATEEF, HARMATTAN, IBM_PLEX, REEM_KUFI, MEHR_NASTALIQ, NOTO_NASTALIQ, SYSTEM
        )
        val urduChoices = listOf(
            GULZAR, MEHR_NASTALIQ, NOTO_NASTALIQ, INDO_PAK, NOTO_NASKH,
            AMIRI, SCHEHERAZADE, LATEEF, SYSTEM, SERIF
        )
        val englishChoices = listOf(SYSTEM, SERIF, MONO, FRAUNCES, INSTRUMENT_SERIF, IBM_PLEX)

        fun named(raw: String?): ScriptFont? = raw?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

/** A quran.com translation resource. IDs match the iOS `QuranAPI` choice lists. */
data class TranslationChoice(val id: Int, val label: String)

object TranslationChoices {
    val english = listOf(
        TranslationChoice(20, "Saheeh International"),
        TranslationChoice(85, "Abdel Haleem"),
        TranslationChoice(149, "Bridges’ translation"),
        TranslationChoice(84, "Taqi Usmani"),
        TranslationChoice(95, "Maududi (English)"),
        TranslationChoice(22, "Yusuf Ali"),
        TranslationChoice(19, "Pickthall"),
        TranslationChoice(203, "Hilali & Khan")
    )
    val urdu = listOf(
        TranslationChoice(54, "Junagarhi"),
        TranslationChoice(234, "Jalandhari"),
        TranslationChoice(97, "Maududi (Tafheem)"),
        TranslationChoice(158, "Bayan-ul-Quran"),
        TranslationChoice(151, "Tafsir-e-Usmani"),
        TranslationChoice(819, "Wahiduddin Khan")
    )

    fun englishLabel(id: Int): String = english.firstOrNull { it.id == id }?.label ?: "Translation $id"
    fun urduLabel(id: Int): String = urdu.firstOrNull { it.id == id }?.label ?: "ترجمہ $id"
}

/**
 * Text colours offered per script. `0` keeps the theme's own ink so light and dark both stay
 * readable; the rest are the palette the share cards already use.
 */
object ScriptColors {
    const val THEME_DEFAULT = 0

    val swatches = listOf(
        THEME_DEFAULT to "Theme",
        0xFF29241F.toInt() to "Ink",
        0xFF8A6A2F.toInt() to "Brass",
        0xFF10322C.toInt() to "Forest",
        0xFF2F3E3B.toInt() to "Slate",
        0xFFD8262C.toInt() to "Red"
    )
}
