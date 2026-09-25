package com.codefixr.beummati.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object Http {
    private const val MAX_REDIRECTS = 8

    /**
     * Opens a GET connection, following redirects manually because
     * [HttpURLConnection] refuses http↔https hops (archive.org does this).
     */
    fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "BeUmmati-Android/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirect without Location from $current")
                current = URL(URL(current), location).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IOException("HTTP $code for $current")
            }
        }
        throw IOException("Too many redirects for $url")
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    /** Network first; on success cache to disk, on failure fall back to the last cached copy. */
    suspend fun getCached(url: String, cacheKey: String): String {
        return try {
            val text = getText(url)
            withContext(Dispatchers.IO) { Catalogs.writeCache(cacheKey, text) }
            text
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { Catalogs.readCache(cacheKey) } ?: throw e
        }
    }

    /** Disk first, network only when nothing is cached. For payloads that never change. */
    suspend fun getCacheFirst(url: String, cacheKey: String): String =
        withContext(Dispatchers.IO) { Catalogs.readCache(cacheKey) } ?: getCached(url, cacheKey)
}

fun cleanHtml(raw: String): String =
    if (raw.contains('<') || raw.contains('&')) {
        HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    } else {
        raw.trim()
    }

private val HARAKAT = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

/** Arabic and Urdu are typed without vowel marks, so both sides are stripped before matching. */
private fun stripHarakat(text: String): String = HARAKAT.replace(text, "")

fun hasArabicScript(text: String): Boolean = text.any { it.code in 0x0600..0x06FF }

private fun JsonElement.stringOrEmpty(): String =
    runCatching { jsonPrimitive.content }.getOrDefault("")

/** Null-safe object access for payloads where a field may be absent or JSON `null`. */
private fun JsonElement?.objectOrNull(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()

object QuranApi {
    private const val BASE = "https://api.alquran.cloud/v1"
    private const val QURAN_COM = "https://api.quran.com/api/v4"
    private const val TEXT_FIELDS = "text_uthmani,text_indopak"
    private const val PAGE_SIZE = 50
    /** quran.com caps `per_page`, so a long surah needs several pages. Guards a runaway loop. */
    private const val MAX_PAGES = 20

    suspend fun surahs(): List<Surah> = try {
        val text = Http.getCacheFirst("$BASE/surah", "quran_surahs.json")
        val data = Catalogs.json.parseToJsonElement(text).jsonObject["data"] ?: JsonArray(emptyList())
        Catalogs.json.decodeFromJsonElement<List<Surah>>(data).ifEmpty { fallbackSurahs() }
    } catch (e: Exception) {
        fallbackSurahs()
    }

    /**
     * Uthmani + Indo-Pak Arabic with the two chosen translations, from quran.com like iOS does.
     * Falls back to the alquran.cloud pairing (English only) when quran.com can't be reached and
     * nothing is cached, so a reader opened offline still shows something.
     */
    suspend fun surah(number: Int, englishId: Int, urduId: Int): SurahDetail = try {
        val meta = surahs().firstOrNull { it.number == number } ?: Surah(number, englishName = "Surah $number")
        SurahDetail(meta, translatedAyahs(number, englishId, urduId))
    } catch (e: Exception) {
        surah(number)
    }

    private suspend fun translatedAyahs(number: Int, englishId: Int, urduId: Int): List<Ayah> {
        val out = ArrayList<Ayah>()
        var page = 1
        while (page <= MAX_PAGES) {
            val text = Http.getCacheFirst(
                "$QURAN_COM/verses/by_chapter/$number?language=en&translations=$englishId,$urduId" +
                    "&fields=$TEXT_FIELDS&per_page=$PAGE_SIZE&page=$page",
                "quran_v4_${number}_${englishId}_${urduId}_p$page.json"
            )
            val next = withContext(Dispatchers.Default) {
                val root = Catalogs.json.parseToJsonElement(text).jsonObject
                val verses = root["verses"]?.jsonArray ?: JsonArray(emptyList())
                if (verses.isEmpty()) return@withContext null
                verses.mapNotNullTo(out) { parseVerse(it, number, englishId, urduId) }
                root["pagination"].objectOrNull()?.get("next_page")?.stringOrEmpty()?.toIntOrNull()
            }
            page = next ?: break
        }
        if (out.isEmpty()) throw IOException("No ayahs for surah $number")
        return out
    }

    private fun parseVerse(element: JsonElement, surah: Int, englishId: Int, urduId: Int): Ayah? {
        val verse = element.objectOrNull() ?: return null
        val key = verse["verse_key"]?.stringOrEmpty().orEmpty()
        val numberInSurah = verse["verse_number"]?.stringOrEmpty()?.toIntOrNull()
            ?: key.substringAfter(':').toIntOrNull()
            ?: return null
        var english = ""
        var urdu = ""
        for (t in verse["translations"]?.jsonArray.orEmpty()) {
            val translation = t.objectOrNull() ?: continue
            val body = cleanHtml(translation["text"]?.stringOrEmpty().orEmpty())
            when (translation["resource_id"]?.stringOrEmpty()?.toIntOrNull()) {
                englishId -> english = body
                urduId -> urdu = body
            }
        }
        return Ayah(
            surah = key.substringBefore(':').toIntOrNull() ?: surah,
            numberInSurah = numberInSurah,
            arabic = verse["text_uthmani"]?.stringOrEmpty().orEmpty(),
            english = english,
            urdu = urdu,
            arabicIndopak = verse["text_indopak"]?.stringOrEmpty().orEmpty()
        )
    }

    /** Same pattern as iOS: Uthmani Arabic + Saheeh International in one request. */
    suspend fun surah(number: Int): SurahDetail {
        val text = Http.getCacheFirst(
            "$BASE/surah/$number/editions/quran-uthmani,en.sahih",
            "quran_surah_$number.json"
        )
        return withContext(Dispatchers.Default) { parseCloudSurah(number, text) }
    }

    private fun parseCloudSurah(number: Int, text: String): SurahDetail {
        val data = Catalogs.json.parseToJsonElement(text).jsonObject["data"]?.jsonArray
            ?: throw IOException("Unexpected Qur’an response")
        val editions = data.map { it.jsonObject }
        val arabicEdition = editions.firstOrNull { it.editionId() == "quran-uthmani" } ?: editions.first()
        val englishEdition = editions.firstOrNull { it.editionId() == "en.sahih" } ?: editions.getOrNull(1)
        val surah = Catalogs.json.decodeFromJsonElement<Surah>(arabicEdition)
        val english = englishEdition?.ayahTexts().orEmpty()
        val ayahs = arabicEdition.ayahTexts().map { (n, ar) ->
            Ayah(surah = number, numberInSurah = n, arabic = ar, english = english[n].orEmpty())
        }
        return SurahDetail(surah, ayahs)
    }

    /** A single ayah by `surah:ayah` key — used by the Home reminder lanes. */
    suspend fun ayah(key: String): Ayah {
        val text = Http.getCacheFirst(
            "$BASE/ayah/$key/editions/quran-uthmani,en.sahih",
            "quran_ayah_${key.replace(':', '_')}.json"
        )
        return withContext(Dispatchers.Default) {
            val data = Catalogs.json.parseToJsonElement(text).jsonObject["data"]?.jsonArray
                ?: throw IOException("Unexpected ayah response for $key")
            val editions = data.map { it.jsonObject }
            val arabic = editions.firstOrNull { it.editionId() == "quran-uthmani" } ?: editions.first()
            val english = editions.firstOrNull { it.editionId() == "en.sahih" }
            Ayah(
                surah = arabic["surah"].objectOrNull()?.get("number")?.stringOrEmpty()?.toIntOrNull()
                    ?: key.substringBefore(':').toIntOrNull() ?: 1,
                numberInSurah = arabic["numberInSurah"]?.stringOrEmpty()?.toIntOrNull()
                    ?: key.substringAfter(':').toIntOrNull() ?: 1,
                arabic = arabic["text"]?.stringOrEmpty().orEmpty(),
                english = english?.get("text")?.stringOrEmpty().orEmpty()
            )
        }
    }

    /**
     * All ayahs in a juz (parah), Arabic only — Uthmani + Indo-Pak from quran.com.
     * Falls back to alquran.cloud Uthmani when quran.com is unreachable.
     */
    suspend fun juzAyahs(juz: Int): List<Ayah> = try {
        juzAyahsFromQuranCom(juz).ifEmpty { juzAyahsFromCloud(juz) }
    } catch (_: Exception) {
        juzAyahsFromCloud(juz)
    }

    private suspend fun juzAyahsFromQuranCom(juz: Int): List<Ayah> {
        val out = ArrayList<Ayah>()
        var page = 1
        while (page <= MAX_PAGES) {
            val text = Http.getCacheFirst(
                "$QURAN_COM/verses/by_juz/$juz?language=en&fields=$TEXT_FIELDS&per_page=$PAGE_SIZE&page=$page",
                "quran_juz_${juz}_p$page.json"
            )
            val next = withContext(Dispatchers.Default) {
                val root = Catalogs.json.parseToJsonElement(text).jsonObject
                val verses = root["verses"]?.jsonArray ?: JsonArray(emptyList())
                if (verses.isEmpty()) return@withContext null
                for (el in verses) {
                    val verse = el.objectOrNull() ?: continue
                    val key = verse["verse_key"]?.stringOrEmpty().orEmpty()
                    val surah = key.substringBefore(':').toIntOrNull()
                        ?: verse["chapter_id"]?.stringOrEmpty()?.toIntOrNull()
                        ?: continue
                    val n = verse["verse_number"]?.stringOrEmpty()?.toIntOrNull()
                        ?: key.substringAfter(':').toIntOrNull()
                        ?: continue
                    out += Ayah(
                        surah = surah,
                        numberInSurah = n,
                        arabic = verse["text_uthmani"]?.stringOrEmpty().orEmpty(),
                        english = "",
                        arabicIndopak = verse["text_indopak"]?.stringOrEmpty().orEmpty()
                    )
                }
                root["pagination"].objectOrNull()?.get("next_page")?.stringOrEmpty()?.toIntOrNull()
            }
            page = next ?: break
        }
        return out
    }

    private suspend fun juzAyahsFromCloud(juz: Int): List<Ayah> {
        val text = Http.getCacheFirst("$BASE/juz/$juz/quran-uthmani", "quran_juz_${juz}_cloud.json")
        return withContext(Dispatchers.Default) {
            val ayahs = Catalogs.json.parseToJsonElement(text).jsonObject["data"]
                ?.jsonObject?.get("ayahs")?.jsonArray
                ?: throw IOException("Unexpected juz response for $juz")
            ayahs.mapNotNull { el ->
                val o = el.objectOrNull() ?: return@mapNotNull null
                val surah = o["surah"].objectOrNull()?.get("number")?.stringOrEmpty()?.toIntOrNull()
                    ?: return@mapNotNull null
                val n = o["numberInSurah"]?.stringOrEmpty()?.toIntOrNull() ?: return@mapNotNull null
                Ayah(
                    surah = surah,
                    numberInSurah = n,
                    arabic = o["text"]?.stringOrEmpty().orEmpty(),
                    english = ""
                )
            }
        }
    }

    /** One ayah by `surah:ayah` with the chosen English + Urdu translations. */
    suspend fun verse(key: String, englishId: Int, urduId: Int): Ayah {
        val text = Http.getCacheFirst(
            "$QURAN_COM/verses/by_key/$key?language=en&translations=$englishId,$urduId&fields=$TEXT_FIELDS",
            "quran_verse_${key.replace(':', '_')}_${englishId}_$urduId.json"
        )
        return withContext(Dispatchers.Default) {
            val verse = Catalogs.json.parseToJsonElement(text).jsonObject["verse"]
                ?: throw IOException("Unexpected verse response for $key")
            parseVerse(verse, key.substringBefore(':').toIntOrNull() ?: 1, englishId, urduId)
                ?: throw IOException("Could not parse $key")
        }
    }

    /**
     * Search ayahs in English, Urdu and Arabic via quran.com.
     * Latin queries hit English + Urdu translation corpora; Arabic-script queries also search
     * the Arabic mushaf text. Results are merged by verse key and filled with both translations.
     */
    suspend fun search(query: String, englishId: Int, urduId: Int, limit: Int = 20): List<Ayah> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val languages = if (hasArabicScript(needle)) listOf("ar", "ur", "en") else listOf("en", "ur")
        val keys = LinkedHashSet<String>()
        coroutineScope {
            languages.map { lang ->
                async { runCatching { searchVerseKeys(needle, lang) }.getOrDefault(emptyList()) }
            }.awaitAll().forEach { keys.addAll(it) }
        }
        if (keys.isEmpty()) return emptyList()
        return keys.take(limit).mapNotNull { key ->
            runCatching { verse(key, englishId, urduId) }.getOrNull()
        }
    }

    /**
     * Search the surah payloads already on disk — everything the Qur’an offline pack wrote,
     * plus any surah the reader has opened. Arabic-script queries match Urdu, Uthmani and
     * Indo-Pak text ignoring harakat; Latin queries match the English translation.
     * No network at all, so this works as the only search when the phone is offline.
     */
    suspend fun searchOffline(
        needle: String,
        limit: Int = 20,
        englishId: Int = SettingsStore.englishTranslationId.value,
        urduId: Int = SettingsStore.urduTranslationId.value
    ): List<Ayah> {
        val query = needle.trim()
        if (query.length < 2) return emptyList()
        val arabicNeedle = if (hasArabicScript(query)) stripHarakat(query) else ""
        return withContext(Dispatchers.Default) {
            val out = ArrayList<Ayah>()
            for (surah in 1..114) {
                for (ayah in cachedSurahAyahs(surah, englishId, urduId)) {
                    if (ayah.matchesOffline(query, arabicNeedle)) out += ayah
                    if (out.size >= limit) return@withContext out
                }
            }
            out
        }
    }

    /** Ayahs for [surah] already cached — quran.com pages first, then the alquran.cloud pairing. */
    private fun cachedSurahAyahs(surah: Int, englishId: Int, urduId: Int): List<Ayah> {
        val out = ArrayList<Ayah>()
        var page = 1
        while (page <= MAX_PAGES) {
            val text = Catalogs.readCache("quran_v4_${surah}_${englishId}_${urduId}_p$page.json") ?: break
            val verses = runCatching {
                Catalogs.json.parseToJsonElement(text).jsonObject["verses"]?.jsonArray
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: break
            verses.mapNotNullTo(out) { parseVerse(it, surah, englishId, urduId) }
            page++
        }
        if (out.isNotEmpty()) return out
        val cloud = Catalogs.readCache("quran_surah_$surah.json") ?: return emptyList()
        return runCatching { parseCloudSurah(surah, cloud).ayahs }.getOrDefault(emptyList())
    }

    private fun Ayah.matchesOffline(needle: String, arabicNeedle: String): Boolean {
        if (english.isNotBlank() && english.contains(needle, ignoreCase = true)) return true
        if (arabicNeedle.isBlank()) return false
        return stripHarakat(urdu).contains(arabicNeedle) ||
            stripHarakat(arabic).contains(arabicNeedle) ||
            stripHarakat(arabicIndopak).contains(arabicNeedle)
    }

    private suspend fun searchVerseKeys(query: String, language: String): List<String> {
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val text = Http.getText(
            "$QURAN_COM/search?q=$encoded&size=20&page=1&language=$language"
        )
        return withContext(Dispatchers.Default) {
            val results = Catalogs.json.parseToJsonElement(text).jsonObject["search"]
                ?.jsonObject?.get("results")?.jsonArray
                ?: return@withContext emptyList()
            results.mapNotNull { el ->
                el.objectOrNull()?.get("verse_key")?.stringOrEmpty()?.takeIf { it.contains(':') }
            }
        }
    }

    /**
     * Word-by-word Arabic with English and/or Urdu glosses from quran.com, keyed by `surah:ayah`.
     * Cached per language combo so an ayah opened once works offline.
     */
    suspend fun words(key: String, lang: WordByWordLang = WordByWordLang.BOTH): List<QuranWord> {
        val cacheKey = "$key|${lang.name}"
        wordCache[cacheKey]?.let { return it }
        val byCode = lang.apiCodes.associateWith { code -> fetchWordGlossMap(key, code) }
        val english = byCode["en"].orEmpty()
        val urdu = byCode["ur"].orEmpty()
        val positions = (english.keys + urdu.keys).sorted()
        // Prefer arabic/transliteration from whichever payload we actually fetched.
        val shell = byCode.values.firstOrNull().orEmpty()
        return positions.map { pos ->
            val base = shell[pos]
            QuranWord(
                position = pos,
                arabic = base?.arabic.orEmpty(),
                transliteration = base?.transliteration.orEmpty(),
                english = english[pos]?.translation.orEmpty(),
                urdu = urdu[pos]?.translation.orEmpty()
            )
        }.filter { it.arabic.isNotBlank() }.also { wordCache[cacheKey] = it }
    }

    private data class RawWord(
        val arabic: String,
        val translation: String,
        val transliteration: String
    )

    private suspend fun fetchWordGlossMap(key: String, language: String): Map<Int, RawWord> {
        val text = Http.getCacheFirst(
            "https://api.quran.com/api/v4/verses/by_key/$key" +
                "?language=$language&words=true&word_fields=text_uthmani,translation,transliteration",
            "quran_words_${key.replace(':', '_')}_$language.json"
        )
        return withContext(Dispatchers.Default) {
            val words = Catalogs.json.parseToJsonElement(text)
                .jsonObject["verse"]?.jsonObject?.get("words")?.jsonArray
                ?: throw IOException("Unexpected words response for $key ($language)")
            buildMap {
                words.forEachIndexed { i, element ->
                    val word = element.objectOrNull() ?: return@forEachIndexed
                    if (word["char_type_name"]?.stringOrEmpty() == "end") return@forEachIndexed
                    val arabic = (word["text_uthmani"] ?: word["text"])?.stringOrEmpty().orEmpty()
                    if (arabic.isBlank() || arabic == "null") return@forEachIndexed
                    val pos = word["position"]?.stringOrEmpty()?.toIntOrNull() ?: (i + 1)
                    put(
                        pos,
                        RawWord(
                            arabic = arabic,
                            translation = cleanHtml(
                                word["translation"].objectOrNull()?.get("text")?.stringOrEmpty().orEmpty()
                            ),
                            transliteration = word["transliteration"].objectOrNull()
                                ?.get("text")?.stringOrEmpty().orEmpty()
                        )
                    )
                }
            }
        }
    }

    /** Ayahs already expanded this session, so collapsing and reopening is instant. */
    private val wordCache = ConcurrentHashMap<String, List<QuranWord>>()

    private fun JsonObject.editionId(): String =
        this["edition"]?.jsonObject?.get("identifier")?.stringOrEmpty().orEmpty()

    private fun JsonObject.ayahTexts(): Map<Int, String> {
        val list = this["ayahs"]?.jsonArray ?: return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for (el in list) {
            val o = el.jsonObject
            val n = o["numberInSurah"]?.stringOrEmpty()?.toIntOrNull() ?: continue
            out[n] = o["text"]?.stringOrEmpty().orEmpty()
        }
        return out
    }

    private fun fallbackSurahs(): List<Surah> =
        (1..114).map { Surah(number = it, englishName = "Surah $it") }
}

object TafsirApi {
    private const val BASE = "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir"
    private const val URDU_IBN_KATHIR = "ur-tafseer-ibn-e-kaseer"

    /** Urdu Ibn Kathir keyed by ayah number. Prefers the bundled pack when present. */
    suspend fun urduIbnKathir(surah: Int): Map<Int, String> {
        Catalogs.offlineTafsirUrdu(surah)?.takeIf { it.isNotEmpty() }?.let { return it }
        val text = Http.getCacheFirst("$BASE/$URDU_IBN_KATHIR/$surah.json", "tafsir_ur_$surah.json")
        return withContext(Dispatchers.Default) {
            val ayahs = Catalogs.json.parseToJsonElement(text).jsonObject["ayahs"]?.jsonArray
                ?: return@withContext emptyMap()
            val out = sortedMapOf<Int, String>()
            for (el in ayahs) {
                val o = el.jsonObject
                val ayah = o["ayah"]?.stringOrEmpty()?.toIntOrNull() ?: continue
                val body = cleanHtml(o["text"]?.stringOrEmpty().orEmpty())
                if (body.isNotEmpty()) out[ayah] = body
            }
            out
        }
    }
}

object HadithApi {
    private const val BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"
    private const val SEARCH = "https://api.islamic.app/v1/hadith/search"

    private data class RawHadith(
        val number: String,
        val sortKey: Double,
        val text: String,
        val grades: List<HadithGrade> = emptyList()
    )

    /** Full-text hadith search (English / Arabic) across collections. */
    suspend fun search(query: String, limit: Int = 20): List<HadithSearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val encoded = java.net.URLEncoder.encode(needle, Charsets.UTF_8.name())
        val text = Http.getText("$SEARCH?q=$encoded&limit=$limit")
        return withContext(Dispatchers.Default) { parseSearch(text) }
    }

    private fun parseSearch(text: String): List<HadithSearchHit> {
        val results = Catalogs.json.parseToJsonElement(text).jsonObject["data"]
            ?.jsonObject?.get("results")?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val collection = o["collection"]?.stringOrEmpty().orEmpty()
            if (collection.isBlank()) return@mapNotNull null
            val bookNumber = o["bookNumber"]?.stringOrEmpty()?.toIntOrNull() ?: 0
            val hadithNumber = o["hadithNumber"]?.stringOrEmpty().orEmpty()
            val chapterTitle = o["chapterTitle"]?.jsonObject
            val titleEn = chapterTitle?.get("en")?.stringOrEmpty().orEmpty()
            val enObj = o["en"]?.jsonObject
            val en = enObj?.get("text")?.stringOrEmpty().orEmpty()
            val ar = o["ar"]?.jsonObject?.get("text")?.stringOrEmpty().orEmpty()
            val snippet = o["snippet"]?.jsonObject?.get("en")?.stringOrEmpty().orEmpty()
            HadithSearchHit(
                collection = collection,
                bookNumber = bookNumber,
                hadithNumber = hadithNumber,
                chapterTitle = titleEn,
                english = cleanHtml(en),
                arabic = cleanHtml(ar),
                snippet = cleanHtml(snippet),
                grades = parseGrades(enObj?.get("grades"))
            )
        }
    }

    /** Hadiths in a kitāb/section, merged across Arabic, English and (when available) Urdu editions. */
    suspend fun chapter(book: String, index: Int): List<HadithItem> = coroutineScope {
        val hasUrdu = Catalogs.hadithBook(book)?.hasUrdu == true
        val en = async { fetchSection("eng-$book", index) }
        val ar = async { fetchSection("ara-$book", index) }
        val ur = async { if (hasUrdu) runCatching { fetchSection("urd-$book", index) }.getOrDefault(emptyList()) else emptyList() }
        merge(book, ar = ar.await(), en = en.await(), ur = ur.await())
    }

    private suspend fun fetchSection(edition: String, section: Int): List<RawHadith> {
        val text = Http.getCacheFirst(
            "$BASE/editions/$edition/sections/$section.json",
            "hadith_${edition}_$section.json"
        )
        return withContext(Dispatchers.Default) { parse(text) }
    }

    private fun parse(text: String): List<RawHadith> {
        val list = Catalogs.json.parseToJsonElement(text).jsonObject["hadiths"]?.jsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el.jsonObject
            val raw = o["hadithnumber"]?.stringOrEmpty().orEmpty()
            val sort = raw.toDoubleOrNull() ?: return@mapNotNull null
            val label = if (sort == Math.floor(sort)) sort.toLong().toString() else raw
            RawHadith(
                number = label,
                sortKey = sort,
                text = cleanHtml(o["text"]?.stringOrEmpty().orEmpty()),
                grades = parseGrades(o["grades"])
            )
        }
    }

    private fun parseGrades(el: JsonElement?): List<HadithGrade> {
        val arr = el?.jsonArray ?: return emptyList()
        return arr.mapNotNull { g ->
            val o = g.jsonObject
            val scholar = o["name"]?.stringOrEmpty()
                ?: o["graded_by"]?.stringOrEmpty()
                ?: ""
            val grade = o["grade"]?.stringOrEmpty().orEmpty()
            if (grade.isBlank()) null else HadithGrade(scholar = scholar.trim(), grade = grade.trim())
        }
    }

    private fun merge(book: String, ar: List<RawHadith>, en: List<RawHadith>, ur: List<RawHadith>): List<HadithItem> {
        val keys = (ar + en + ur).associate { it.number to it.sortKey }
        val arBy = ar.associateBy { it.number }
        val enBy = en.associateBy { it.number }
        val urBy = ur.associateBy { it.number }
        return keys.entries.sortedBy { it.value }.map { (num, _) ->
            val grades = enBy[num]?.grades.orEmpty()
                .ifEmpty { arBy[num]?.grades.orEmpty() }
                .ifEmpty { urBy[num]?.grades.orEmpty() }
            HadithItem(
                book = book,
                number = num,
                arabic = arBy[num]?.text.orEmpty(),
                english = enBy[num]?.text.orEmpty(),
                urdu = urBy[num]?.text.orEmpty(),
                grades = grades
            )
        }.filter { it.arabic.isNotBlank() || it.english.isNotBlank() }
    }
}
