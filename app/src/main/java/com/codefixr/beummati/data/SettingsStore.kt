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

/** Named colour themes — port of iOS `AppThemeKind`. */
enum class AppThemeKind(val label: String, val blurb: String) {
    MANUSCRIPT("Manuscript", "Parchment · teal · brass"),
    MIDNIGHT("Midnight", "Dark night · gold accents"),
    EMERALD("Emerald", "Deep green · cream"),
    OCEAN("Ocean", "Cool blue · soft mist"),
    UMMATI("Ummati", "Red · white · charcoal (brand)"),
    SOFT_DAY("Soft Day", "Light airy · sage")
}

/** Qur’an reader layout — port of iOS `QuranReadMode`. */
enum class QuranReadMode(val label: String) {
    MUSHAF("Mushaf"),
    TRANSLATION("Translation")
}

/** List (scroll) vs Slide (one item per page, swipe). Used across Qur’an, Hadith, dhikr, etc. */
enum class ContentBrowseMode(val label: String) {
    LIST("List"),
    SLIDE("Slide")
}

/** Colour treatment for the lecture player, mirroring the iOS `LecturePlayerView` skins. */
enum class PlayerSkin(val label: String, val blurb: String) {
    DARK("Dark", "Deep charcoal with the brass accent"),
    LIGHT("Light", "Parchment and ink, easy in daylight"),
    SOUNDCLOUD("SoundCloud", "Warm orange over near-black")
}

/**
 * Share card designs live in [ShareTemplate].
 */

/** Appearance + reading preferences. Port of the iOS `ThemeStore` / `ReadingSettings`. */
object SettingsStore {
    const val DEFAULT_ARABIC_SIZE = 24f
    const val MIN_ARABIC_SIZE = 16f
    const val MAX_ARABIC_SIZE = 42f

    const val DEFAULT_ENGLISH_SIZE = 16f
    const val MIN_ENGLISH_SIZE = 12f
    const val MAX_ENGLISH_SIZE = 28f

    const val DEFAULT_URDU_SIZE = 20f
    const val MIN_URDU_SIZE = 12f
    const val MAX_URDU_SIZE = 32f

    const val MAX_ARABIC_LINE_SPACING = 10f
    const val MAX_URDU_LINE_SPACING = 14f

    const val DEFAULT_ENGLISH_TRANSLATION = 85
    const val DEFAULT_URDU_TRANSLATION = 54

    /** Offered as playback defaults; kept in step with `LecturePlayerSession.rates`. */
    val RATE_OPTIONS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

    private const val PREFS = "beummati.settings"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_THEME_KIND = "themeKind"
    private const val KEY_QURAN_READ_MODE = "quranReadMode"
    private const val KEY_CONTENT_BROWSE_MODE = "contentBrowseMode"
    private const val KEY_ARABIC_SIZE = "arabicSize"
    private const val KEY_SHOW_URDU = "showUrdu"
    private const val KEY_SHOW_TRANSLITERATION = "showTransliteration"
    private const val KEY_PLAYER_SKIN = "playerSkin"
    private const val KEY_SHARE_TEMPLATE = "shareTemplate"
    private const val KEY_SHARE_COLOR_MOOD = "shareColorMood"
    private const val KEY_DEFAULT_RATE = "defaultRate"
    private const val KEY_READING_LANGUAGE = "readingLanguage"
    private const val KEY_SHOW_ARABIC = "showArabic"
    private const val KEY_SHOW_ENGLISH = "showEnglish"
    private const val KEY_EN_TRANSLATION = "enTranslationId"
    private const val KEY_UR_TRANSLATION = "urTranslationId"
    private const val KEY_SHARE_ARABIC = "shareArabic"
    private const val KEY_SHARE_ENGLISH = "shareEnglish"
    private const val KEY_SHARE_URDU = "shareUrdu"
    private const val KEY_TEXT_ALIGN = "textAlign"
    private const val KEY_SHOW_LANG_LABELS = "showLangLabels"
    private const val KEY_WORD_BY_WORD_LANG = "wordByWordLang"
    private const val KEY_ARABIC_FONT = "arabicFont"
    private const val KEY_ENGLISH_FONT = "englishFont"
    private const val KEY_URDU_FONT = "urduFont"
    private const val KEY_ENGLISH_SIZE = "englishSize"
    private const val KEY_URDU_SIZE = "urduSize"
    private const val KEY_ARABIC_LINE_SPACING = "arabicLineSpacing"
    private const val KEY_URDU_LINE_SPACING = "urduLineSpacing"
    private const val KEY_ARABIC_COLOR = "arabicColor"
    private const val KEY_ENGLISH_COLOR = "englishColor"
    private const val KEY_URDU_COLOR = "urduColor"

    private lateinit var prefs: SharedPreferences

    private val _appearance = MutableStateFlow(AppAppearance.SYSTEM)
    val appearance: StateFlow<AppAppearance> = _appearance.asStateFlow()

    private val _themeKind = MutableStateFlow(AppThemeKind.MANUSCRIPT)
    val themeKind: StateFlow<AppThemeKind> = _themeKind.asStateFlow()

    private val _quranReadMode = MutableStateFlow(QuranReadMode.MUSHAF)
    val quranReadMode: StateFlow<QuranReadMode> = _quranReadMode.asStateFlow()

    private val _contentBrowseMode = MutableStateFlow(ContentBrowseMode.LIST)
    val contentBrowseMode: StateFlow<ContentBrowseMode> = _contentBrowseMode.asStateFlow()

    private val _arabicFontSize = MutableStateFlow(DEFAULT_ARABIC_SIZE)
    val arabicFontSize: StateFlow<Float> = _arabicFontSize.asStateFlow()

    private val _englishFontSize = MutableStateFlow(DEFAULT_ENGLISH_SIZE)
    val englishFontSize: StateFlow<Float> = _englishFontSize.asStateFlow()

    private val _urduFontSize = MutableStateFlow(DEFAULT_URDU_SIZE)
    val urduFontSize: StateFlow<Float> = _urduFontSize.asStateFlow()

    private val _arabicLineSpacing = MutableStateFlow(0f)
    val arabicLineSpacing: StateFlow<Float> = _arabicLineSpacing.asStateFlow()

    private val _urduLineSpacing = MutableStateFlow(2f)
    val urduLineSpacing: StateFlow<Float> = _urduLineSpacing.asStateFlow()

    private val _readingLanguage = MutableStateFlow(AppReadingLanguage.ALL_THREE)
    val readingLanguage: StateFlow<AppReadingLanguage> = _readingLanguage.asStateFlow()

    private val _showArabic = MutableStateFlow(true)
    val showArabic: StateFlow<Boolean> = _showArabic.asStateFlow()

    private val _showEnglish = MutableStateFlow(true)
    val showEnglish: StateFlow<Boolean> = _showEnglish.asStateFlow()

    private val _showUrdu = MutableStateFlow(true)
    val showUrdu: StateFlow<Boolean> = _showUrdu.asStateFlow()

    private val _showTransliteration = MutableStateFlow(true)
    val showTransliteration: StateFlow<Boolean> = _showTransliteration.asStateFlow()

    private val _englishTranslationId = MutableStateFlow(DEFAULT_ENGLISH_TRANSLATION)
    val englishTranslationId: StateFlow<Int> = _englishTranslationId.asStateFlow()

    private val _urduTranslationId = MutableStateFlow(DEFAULT_URDU_TRANSLATION)
    val urduTranslationId: StateFlow<Int> = _urduTranslationId.asStateFlow()

    private val _shareArabic = MutableStateFlow(true)
    val shareArabic: StateFlow<Boolean> = _shareArabic.asStateFlow()

    private val _shareEnglish = MutableStateFlow(true)
    val shareEnglish: StateFlow<Boolean> = _shareEnglish.asStateFlow()

    private val _shareUrdu = MutableStateFlow(true)
    val shareUrdu: StateFlow<Boolean> = _shareUrdu.asStateFlow()

    private val _textAlign = MutableStateFlow(TextAlignMode.LEADING)
    val textAlign: StateFlow<TextAlignMode> = _textAlign.asStateFlow()

    private val _showLangLabels = MutableStateFlow(true)
    val showLangLabels: StateFlow<Boolean> = _showLangLabels.asStateFlow()

    private val _wordByWordLang = MutableStateFlow(WordByWordLang.BOTH)
    val wordByWordLang: StateFlow<WordByWordLang> = _wordByWordLang.asStateFlow()

    private val _arabicFont = MutableStateFlow(ScriptFont.AMIRI)
    val arabicFont: StateFlow<ScriptFont> = _arabicFont.asStateFlow()

    private val _englishFont = MutableStateFlow(ScriptFont.SYSTEM)
    val englishFont: StateFlow<ScriptFont> = _englishFont.asStateFlow()

    private val _urduFont = MutableStateFlow(ScriptFont.NOTO_NASTALIQ)
    val urduFont: StateFlow<ScriptFont> = _urduFont.asStateFlow()

    private val _arabicColor = MutableStateFlow(ScriptColors.THEME_DEFAULT)
    val arabicColor: StateFlow<Int> = _arabicColor.asStateFlow()

    private val _englishColor = MutableStateFlow(ScriptColors.THEME_DEFAULT)
    val englishColor: StateFlow<Int> = _englishColor.asStateFlow()

    private val _urduColor = MutableStateFlow(ScriptColors.THEME_DEFAULT)
    val urduColor: StateFlow<Int> = _urduColor.asStateFlow()

    private val _playerSkin = MutableStateFlow(PlayerSkin.DARK)
    val playerSkin: StateFlow<PlayerSkin> = _playerSkin.asStateFlow()

    private val _shareTemplate = MutableStateFlow(ShareTemplate.MIHRAB)
    val shareTemplate: StateFlow<ShareTemplate> = _shareTemplate.asStateFlow()

    private val _shareColorMood = MutableStateFlow(ShareColorMood.DEFAULT)
    val shareColorMood: StateFlow<ShareColorMood> = _shareColorMood.asStateFlow()

    private val _defaultRate = MutableStateFlow(1.0f)
    val defaultRate: StateFlow<Float> = _defaultRate.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _appearance.value = prefs.getString(KEY_APPEARANCE, null)
            ?.let { raw -> AppAppearance.entries.firstOrNull { it.name == raw } }
            ?: AppAppearance.SYSTEM
        _themeKind.value = prefs.getString(KEY_THEME_KIND, null)
            ?.let { raw -> AppThemeKind.entries.firstOrNull { it.name == raw } }
            ?: AppThemeKind.MANUSCRIPT
        _quranReadMode.value = prefs.getString(KEY_QURAN_READ_MODE, null)
            ?.let { raw -> QuranReadMode.entries.firstOrNull { it.name == raw } }
            ?: QuranReadMode.MUSHAF
        _contentBrowseMode.value = prefs.getString(KEY_CONTENT_BROWSE_MODE, null)
            ?.let { raw -> ContentBrowseMode.entries.firstOrNull { it.name == raw } }
            ?: ContentBrowseMode.LIST
        _arabicFontSize.value = prefs.getFloat(KEY_ARABIC_SIZE, DEFAULT_ARABIC_SIZE)
            .coerceIn(MIN_ARABIC_SIZE, MAX_ARABIC_SIZE)
        _englishFontSize.value = prefs.getFloat(KEY_ENGLISH_SIZE, DEFAULT_ENGLISH_SIZE)
            .coerceIn(MIN_ENGLISH_SIZE, MAX_ENGLISH_SIZE)
        _urduFontSize.value = prefs.getFloat(KEY_URDU_SIZE, DEFAULT_URDU_SIZE)
            .coerceIn(MIN_URDU_SIZE, MAX_URDU_SIZE)
        _arabicLineSpacing.value = prefs.getFloat(KEY_ARABIC_LINE_SPACING, 0f)
            .coerceIn(0f, MAX_ARABIC_LINE_SPACING)
        _urduLineSpacing.value = prefs.getFloat(KEY_URDU_LINE_SPACING, 2f)
            .coerceIn(0f, MAX_URDU_LINE_SPACING)
        _readingLanguage.value = prefs.getString(KEY_READING_LANGUAGE, null)
            ?.let { raw -> AppReadingLanguage.entries.firstOrNull { it.name == raw } }
            ?: AppReadingLanguage.ALL_THREE
        _showArabic.value = prefs.getBoolean(KEY_SHOW_ARABIC, true)
        _showEnglish.value = prefs.getBoolean(KEY_SHOW_ENGLISH, true)
        _showUrdu.value = prefs.getBoolean(KEY_SHOW_URDU, true)
        _showTransliteration.value = prefs.getBoolean(KEY_SHOW_TRANSLITERATION, true)
        _englishTranslationId.value = prefs.getInt(KEY_EN_TRANSLATION, DEFAULT_ENGLISH_TRANSLATION)
        _urduTranslationId.value = prefs.getInt(KEY_UR_TRANSLATION, DEFAULT_URDU_TRANSLATION)
        _shareArabic.value = prefs.getBoolean(KEY_SHARE_ARABIC, true)
        _shareEnglish.value = prefs.getBoolean(KEY_SHARE_ENGLISH, true)
        _shareUrdu.value = prefs.getBoolean(KEY_SHARE_URDU, true)
        _textAlign.value = prefs.getString(KEY_TEXT_ALIGN, null)
            ?.let { raw -> TextAlignMode.entries.firstOrNull { it.name == raw } }
            ?: TextAlignMode.LEADING
        _showLangLabels.value = prefs.getBoolean(KEY_SHOW_LANG_LABELS, true)
        _wordByWordLang.value = WordByWordLang.named(prefs.getString(KEY_WORD_BY_WORD_LANG, null))
            ?: WordByWordLang.BOTH
        _arabicFont.value = ScriptFont.named(prefs.getString(KEY_ARABIC_FONT, null)) ?: ScriptFont.AMIRI
        _englishFont.value = ScriptFont.named(prefs.getString(KEY_ENGLISH_FONT, null)) ?: ScriptFont.SYSTEM
        _urduFont.value = ScriptFont.named(prefs.getString(KEY_URDU_FONT, null)) ?: ScriptFont.NOTO_NASTALIQ
        _arabicColor.value = prefs.getInt(KEY_ARABIC_COLOR, ScriptColors.THEME_DEFAULT)
        _englishColor.value = prefs.getInt(KEY_ENGLISH_COLOR, ScriptColors.THEME_DEFAULT)
        _urduColor.value = prefs.getInt(KEY_URDU_COLOR, ScriptColors.THEME_DEFAULT)
        _playerSkin.value = prefs.getString(KEY_PLAYER_SKIN, null)
            ?.let { raw -> PlayerSkin.entries.firstOrNull { it.name == raw } }
            ?: PlayerSkin.DARK
        _shareTemplate.value = prefs.getString(KEY_SHARE_TEMPLATE, null)
            ?.let { raw -> ShareTemplate.entries.firstOrNull { it.name == raw } }
            ?: ShareTemplate.MIHRAB
        _shareColorMood.value = prefs.getString(KEY_SHARE_COLOR_MOOD, null)
            ?.let { raw -> ShareColorMood.entries.firstOrNull { it.name == raw } }
            ?: ShareColorMood.DEFAULT
        _defaultRate.value = prefs.getFloat(KEY_DEFAULT_RATE, 1.0f)
    }

    fun setAppearance(value: AppAppearance) {
        _appearance.value = value
        prefs.edit().putString(KEY_APPEARANCE, value.name).apply()
    }

    fun setThemeKind(value: AppThemeKind) {
        _themeKind.value = value
        prefs.edit().putString(KEY_THEME_KIND, value.name).apply()
    }

    fun setQuranReadMode(value: QuranReadMode) {
        _quranReadMode.value = value
        prefs.edit().putString(KEY_QURAN_READ_MODE, value.name).apply()
    }

    fun setContentBrowseMode(value: ContentBrowseMode) {
        _contentBrowseMode.value = value
        prefs.edit().putString(KEY_CONTENT_BROWSE_MODE, value.name).apply()
    }

    fun setArabicFontSize(value: Float) {
        val clamped = value.coerceIn(MIN_ARABIC_SIZE, MAX_ARABIC_SIZE)
        _arabicFontSize.value = clamped
        prefs.edit().putFloat(KEY_ARABIC_SIZE, clamped).apply()
    }

    fun setEnglishFontSize(value: Float) {
        val clamped = value.coerceIn(MIN_ENGLISH_SIZE, MAX_ENGLISH_SIZE)
        _englishFontSize.value = clamped
        prefs.edit().putFloat(KEY_ENGLISH_SIZE, clamped).apply()
    }

    fun setUrduFontSize(value: Float) {
        val clamped = value.coerceIn(MIN_URDU_SIZE, MAX_URDU_SIZE)
        _urduFontSize.value = clamped
        prefs.edit().putFloat(KEY_URDU_SIZE, clamped).apply()
    }

    fun setArabicLineSpacing(value: Float) {
        val clamped = value.coerceIn(0f, MAX_ARABIC_LINE_SPACING)
        _arabicLineSpacing.value = clamped
        prefs.edit().putFloat(KEY_ARABIC_LINE_SPACING, clamped).apply()
    }

    fun setUrduLineSpacing(value: Float) {
        val clamped = value.coerceIn(0f, MAX_URDU_LINE_SPACING)
        _urduLineSpacing.value = clamped
        prefs.edit().putFloat(KEY_URDU_LINE_SPACING, clamped).apply()
    }

    /** Presets drive the three show/hide switches; they stay editable afterwards, as on iOS. */
    fun setReadingLanguage(value: AppReadingLanguage) {
        _readingLanguage.value = value
        _showArabic.value = true
        _showEnglish.value = value.showsEnglish
        _showUrdu.value = value.showsUrdu
        prefs.edit()
            .putString(KEY_READING_LANGUAGE, value.name)
            .putBoolean(KEY_SHOW_ARABIC, true)
            .putBoolean(KEY_SHOW_ENGLISH, value.showsEnglish)
            .putBoolean(KEY_SHOW_URDU, value.showsUrdu)
            .apply()
    }

    fun setShowArabic(value: Boolean) {
        _showArabic.value = value
        prefs.edit().putBoolean(KEY_SHOW_ARABIC, value).apply()
    }

    fun setShowEnglish(value: Boolean) {
        _showEnglish.value = value
        prefs.edit().putBoolean(KEY_SHOW_ENGLISH, value).apply()
    }

    fun setShowUrdu(value: Boolean) {
        _showUrdu.value = value
        prefs.edit().putBoolean(KEY_SHOW_URDU, value).apply()
    }

    fun setShowTransliteration(value: Boolean) {
        _showTransliteration.value = value
        prefs.edit().putBoolean(KEY_SHOW_TRANSLITERATION, value).apply()
    }

    fun setEnglishTranslationId(value: Int) {
        _englishTranslationId.value = value
        prefs.edit().putInt(KEY_EN_TRANSLATION, value).apply()
    }

    fun setUrduTranslationId(value: Int) {
        _urduTranslationId.value = value
        prefs.edit().putInt(KEY_UR_TRANSLATION, value).apply()
    }

    fun setShareArabic(value: Boolean) {
        _shareArabic.value = value
        prefs.edit().putBoolean(KEY_SHARE_ARABIC, value).apply()
    }

    fun setShareEnglish(value: Boolean) {
        _shareEnglish.value = value
        prefs.edit().putBoolean(KEY_SHARE_ENGLISH, value).apply()
    }

    fun setShareUrdu(value: Boolean) {
        _shareUrdu.value = value
        prefs.edit().putBoolean(KEY_SHARE_URDU, value).apply()
    }

    fun setTextAlign(value: TextAlignMode) {
        _textAlign.value = value
        prefs.edit().putString(KEY_TEXT_ALIGN, value.name).apply()
    }

    fun setShowLangLabels(value: Boolean) {
        _showLangLabels.value = value
        prefs.edit().putBoolean(KEY_SHOW_LANG_LABELS, value).apply()
    }

    fun setWordByWordLang(value: WordByWordLang) {
        _wordByWordLang.value = value
        prefs.edit().putString(KEY_WORD_BY_WORD_LANG, value.name).apply()
    }

    fun setArabicFont(value: ScriptFont) {
        _arabicFont.value = value
        prefs.edit().putString(KEY_ARABIC_FONT, value.name).apply()
    }

    fun setEnglishFont(value: ScriptFont) {
        _englishFont.value = value
        prefs.edit().putString(KEY_ENGLISH_FONT, value.name).apply()
    }

    fun setUrduFont(value: ScriptFont) {
        _urduFont.value = value
        prefs.edit().putString(KEY_URDU_FONT, value.name).apply()
    }

    fun setArabicColor(value: Int) {
        _arabicColor.value = value
        prefs.edit().putInt(KEY_ARABIC_COLOR, value).apply()
    }

    fun setEnglishColor(value: Int) {
        _englishColor.value = value
        prefs.edit().putInt(KEY_ENGLISH_COLOR, value).apply()
    }

    fun setUrduColor(value: Int) {
        _urduColor.value = value
        prefs.edit().putInt(KEY_URDU_COLOR, value).apply()
    }

    fun setPlayerSkin(value: PlayerSkin) {
        _playerSkin.value = value
        prefs.edit().putString(KEY_PLAYER_SKIN, value.name).apply()
    }

    fun setShareTemplate(value: ShareTemplate) {
        _shareTemplate.value = value
        prefs.edit().putString(KEY_SHARE_TEMPLATE, value.name).apply()
    }

    fun setShareColorMood(value: ShareColorMood) {
        _shareColorMood.value = value
        prefs.edit().putString(KEY_SHARE_COLOR_MOOD, value.name).apply()
    }

    fun setDefaultRate(value: Float) {
        _defaultRate.value = value
        prefs.edit().putFloat(KEY_DEFAULT_RATE, value).apply()
    }

    fun resetReading() {
        setReadingLanguage(AppReadingLanguage.ALL_THREE)
        setArabicFontSize(DEFAULT_ARABIC_SIZE)
        setEnglishFontSize(DEFAULT_ENGLISH_SIZE)
        setUrduFontSize(DEFAULT_URDU_SIZE)
        setArabicLineSpacing(0f)
        setUrduLineSpacing(2f)
        setShowTransliteration(true)
        setEnglishTranslationId(DEFAULT_ENGLISH_TRANSLATION)
        setUrduTranslationId(DEFAULT_URDU_TRANSLATION)
        setShareArabic(true)
        setShareEnglish(true)
        setShareUrdu(true)
        setWordByWordLang(WordByWordLang.BOTH)
        setTextAlign(TextAlignMode.LEADING)
        setShowLangLabels(true)
        setArabicFont(ScriptFont.AMIRI)
        setEnglishFont(ScriptFont.SYSTEM)
        setUrduFont(ScriptFont.NOTO_NASTALIQ)
        setArabicColor(ScriptColors.THEME_DEFAULT)
        setEnglishColor(ScriptColors.THEME_DEFAULT)
        setUrduColor(ScriptColors.THEME_DEFAULT)
    }
}
