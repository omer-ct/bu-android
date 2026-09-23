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

/** Appearance + reading preferences. Simplified port of the iOS `ThemeStore` / `ReadingSettings`. */
object SettingsStore {
    const val DEFAULT_ARABIC_SIZE = 24f
    const val MIN_ARABIC_SIZE = 16f
    const val MAX_ARABIC_SIZE = 40f

    private const val PREFS = "beummati.settings"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_ARABIC_SIZE = "arabicSize"
    private const val KEY_SHOW_URDU = "showUrdu"
    private const val KEY_SHOW_TRANSLITERATION = "showTransliteration"

    private lateinit var prefs: SharedPreferences

    private val _appearance = MutableStateFlow(AppAppearance.SYSTEM)
    val appearance: StateFlow<AppAppearance> = _appearance.asStateFlow()

    private val _arabicFontSize = MutableStateFlow(DEFAULT_ARABIC_SIZE)
    val arabicFontSize: StateFlow<Float> = _arabicFontSize.asStateFlow()

    private val _showUrdu = MutableStateFlow(true)
    val showUrdu: StateFlow<Boolean> = _showUrdu.asStateFlow()

    private val _showTransliteration = MutableStateFlow(true)
    val showTransliteration: StateFlow<Boolean> = _showTransliteration.asStateFlow()

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

    fun resetReading() {
        setArabicFontSize(DEFAULT_ARABIC_SIZE)
        setShowUrdu(true)
        setShowTransliteration(true)
    }
}
