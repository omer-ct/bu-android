package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppAppearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Colour treatment for the lecture player, mirroring the iOS `LecturePlayerView` skins. */
enum class PlayerSkin(val label: String, val blurb: String) {
    DARK("Dark", "Deep charcoal with the brass accent"),
    LIGHT("Light", "Parchment and ink, easy in daylight"),
    SOUNDCLOUD("SoundCloud", "Warm orange over near-black")
}

/**
 * Share card designs. Each renders the same [com.codefixr.beummati.ui.ShareCard] fields; the
 * reference artwork they are drawn from lives in `assets/share_refs`.
 */
enum class ShareTemplate(val label: String, val blurb: String) {
    PARCHMENT("Parchment", "Cream paper with a brass rule"),
    NIGHT_CORAL("Night coral", "Charcoal with coral highlights"),
    QUOTE_WARM("Warm quote", "Orange card with a join-us strip"),
    FOREST("Forest", "Deep teal with translucent text boxes"),
    SPLIT_DUAL("Split", "Cream over green, colours flipped"),
    DHIKR_SLATE("Dhikr slate", "Solid band for Arabic and transliteration"),
    VERSE_BANNER("Verse banner", "Colour block beside the ayah"),
    PLUSH_LIGHT("Plush light", "Soft cream with gentle shadows")
}

/** Appearance + reading preferences. Simplified port of the iOS `ThemeStore` / `ReadingSettings`. */
object SettingsStore {
    const val DEFAULT_ARABIC_SIZE = 24f
    const val MIN_ARABIC_SIZE = 16f
    const val MAX_ARABIC_SIZE = 40f

    /** Offered as playback defaults; kept in step with `LecturePlayerSession.rates`. */
    val RATE_OPTIONS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

    private const val PREFS = "beummati.settings"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_ARABIC_SIZE = "arabicSize"
    private const val KEY_SHOW_URDU = "showUrdu"
    private const val KEY_SHOW_TRANSLITERATION = "showTransliteration"
    private const val KEY_PLAYER_SKIN = "playerSkin"
    private const val KEY_SHARE_TEMPLATE = "shareTemplate"
    private const val KEY_DEFAULT_RATE = "defaultRate"

    private lateinit var prefs: SharedPreferences

    private val _appearance = MutableStateFlow(AppAppearance.SYSTEM)
    val appearance: StateFlow<AppAppearance> = _appearance.asStateFlow()

    private val _arabicFontSize = MutableStateFlow(DEFAULT_ARABIC_SIZE)
    val arabicFontSize: StateFlow<Float> = _arabicFontSize.asStateFlow()

    private val _showUrdu = MutableStateFlow(true)
    val showUrdu: StateFlow<Boolean> = _showUrdu.asStateFlow()

    private val _showTransliteration = MutableStateFlow(true)
    val showTransliteration: StateFlow<Boolean> = _showTransliteration.asStateFlow()

    private val _playerSkin = MutableStateFlow(PlayerSkin.DARK)
    val playerSkin: StateFlow<PlayerSkin> = _playerSkin.asStateFlow()

    private val _shareTemplate = MutableStateFlow(ShareTemplate.PARCHMENT)
    val shareTemplate: StateFlow<ShareTemplate> = _shareTemplate.asStateFlow()

    private val _defaultRate = MutableStateFlow(1.0f)
    val defaultRate: StateFlow<Float> = _defaultRate.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _appearance.value = prefs.getString(KEY_APPEARANCE, null)
            ?.let { raw -> AppAppearance.entries.firstOrNull { it.name == raw } }
            ?: AppAppearance.SYSTEM
        _arabicFontSize.value = prefs.getFloat(KEY_ARABIC_SIZE, DEFAULT_ARABIC_SIZE)
            .coerceIn(MIN_ARABIC_SIZE, MAX_ARABIC_SIZE)
        _showUrdu.value = prefs.getBoolean(KEY_SHOW_URDU, true)
        _showTransliteration.value = prefs.getBoolean(KEY_SHOW_TRANSLITERATION, true)
        _playerSkin.value = prefs.getString(KEY_PLAYER_SKIN, null)
            ?.let { raw -> PlayerSkin.entries.firstOrNull { it.name == raw } }
            ?: PlayerSkin.DARK
        _shareTemplate.value = prefs.getString(KEY_SHARE_TEMPLATE, null)
            ?.let { raw -> ShareTemplate.entries.firstOrNull { it.name == raw } }
            ?: ShareTemplate.PARCHMENT
        _defaultRate.value = prefs.getFloat(KEY_DEFAULT_RATE, 1.0f)
    }

    fun setAppearance(value: AppAppearance) {
        _appearance.value = value
        prefs.edit().putString(KEY_APPEARANCE, value.name).apply()
    }

    fun setArabicFontSize(value: Float) {
        val clamped = value.coerceIn(MIN_ARABIC_SIZE, MAX_ARABIC_SIZE)
        _arabicFontSize.value = clamped
        prefs.edit().putFloat(KEY_ARABIC_SIZE, clamped).apply()
    }

    fun setShowUrdu(value: Boolean) {
        _showUrdu.value = value
        prefs.edit().putBoolean(KEY_SHOW_URDU, value).apply()
    }

    fun setShowTransliteration(value: Boolean) {
        _showTransliteration.value = value
        prefs.edit().putBoolean(KEY_SHOW_TRANSLITERATION, value).apply()
    }

    fun setPlayerSkin(value: PlayerSkin) {
        _playerSkin.value = value
        prefs.edit().putString(KEY_PLAYER_SKIN, value.name).apply()
    }

    fun setShareTemplate(value: ShareTemplate) {
        _shareTemplate.value = value
        prefs.edit().putString(KEY_SHARE_TEMPLATE, value.name).apply()
    }

    fun setDefaultRate(value: Float) {
        _defaultRate.value = value
        prefs.edit().putFloat(KEY_DEFAULT_RATE, value).apply()
    }

    fun resetReading() {
        setArabicFontSize(DEFAULT_ARABIC_SIZE)
        setShowUrdu(true)
        setShowTransliteration(true)
    }
}
