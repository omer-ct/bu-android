package com.codefixr.beummati.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
}

fun cleanHtml(raw: String): String =
    if (raw.contains('<') || raw.contains('&')) {
        HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    } else {
        raw.trim()
    }

private fun JsonElement.stringOrEmpty(): String =
    runCatching { jsonPrimitive.content }.getOrDefault("")

/** Null-safe object access for payloads where a field may be absent or JSON `null`. */
private fun JsonElement?.objectOrNull(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()

object QuranApi {
    private const val BASE = "https://api.alquran.cloud/v1"

    suspend fun surahs(): List<Surah> = try {
        val text = Http.getCached("$BASE/surah", "quran_surahs.json")
        val data = Catalogs.json.parseToJsonElement(text).jsonObject["data"] ?: JsonArray(emptyList())
        Catalogs.json.decodeFromJsonElement<List<Surah>>(data).ifEmpty { fallbackSurahs() }
    } catch (e: Exception) {
        fallbackSurahs()
    }

    /** Same pattern as iOS: Uthmani Arabic + Saheeh International in one request. */
    suspend fun surah(number: Int): SurahDetail {
        val text = Http.getCached(
            "$BASE/surah/$number/editions/quran-uthmani,en.sahih",
            "quran_surah_$number.json"
        )
        return withContext(Dispatchers.Default) {
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
            SurahDetail(surah, ayahs)
        }
    }

    /** A single ayah by `surah:ayah` key — used by the Home reminder lanes. */
    suspend fun ayah(key: String): Ayah {
        val text = Http.getCached(
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

    /** Word-by-word Arabic with English gloss from quran.com, keyed by `surah:ayah`. */
    suspend fun words(key: String): List<QuranWord> {
        val text = Http.getCached(
            "https://api.quran.com/api/v4/verses/by_key/$key" +
                "?language=en&words=true&word_fields=text_uthmani,translation,transliteration",
            "quran_words_${key.replace(':', '_')}.json"
        )
        return withContext(Dispatchers.Default) {
            val words = Catalogs.json.parseToJsonElement(text)
                .jsonObject["verse"]?.jsonObject?.get("words")?.jsonArray
                ?: throw IOException("Unexpected words response for $key")
            words.mapIndexedNotNull { i, element ->
                val word = element.objectOrNull() ?: return@mapIndexedNotNull null
                if (word["char_type_name"]?.stringOrEmpty() == "end") return@mapIndexedNotNull null
                val arabic = (word["text_uthmani"] ?: word["text"])?.stringOrEmpty().orEmpty()
                if (arabic.isBlank() || arabic == "null") return@mapIndexedNotNull null
                QuranWord(
                    position = word["position"]?.stringOrEmpty()?.toIntOrNull() ?: (i + 1),
                    arabic = arabic,
                    translation = cleanHtml(word["translation"].objectOrNull()?.get("text")?.stringOrEmpty().orEmpty()),
                    transliteration = word["transliteration"].objectOrNull()?.get("text")?.stringOrEmpty().orEmpty()
                )
            }
        }
    }

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

    /** Urdu Ibn Kathir keyed by ayah number. */
    suspend fun urduIbnKathir(surah: Int): Map<Int, String> {
        val text = Http.getCached("$BASE/$URDU_IBN_KATHIR/$surah.json", "tafsir_ur_$surah.json")
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

    private data class RawHadith(val number: String, val sortKey: Double, val text: String)

    /** Hadiths in a kitāb/section, merged across Arabic, English and (when available) Urdu editions. */
    suspend fun chapter(book: String, index: Int): List<HadithItem> = coroutineScope {
        val hasUrdu = Catalogs.hadithBook(book)?.hasUrdu == true
        val en = async { fetchSection("eng-$book", index) }
        val ar = async { fetchSection("ara-$book", index) }
        val ur = async { if (hasUrdu) runCatching { fetchSection("urd-$book", index) }.getOrDefault(emptyList()) else emptyList() }
        merge(book, ar = ar.await(), en = en.await(), ur = ur.await())
    }

    private suspend fun fetchSection(edition: String, section: Int): List<RawHadith> {
        val text = Http.getCached(
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
            RawHadith(label, sort, cleanHtml(o["text"]?.stringOrEmpty().orEmpty()))
        }
    }

    private fun merge(book: String, ar: List<RawHadith>, en: List<RawHadith>, ur: List<RawHadith>): List<HadithItem> {
        val keys = (ar + en + ur).associate { it.number to it.sortKey }
        val arBy = ar.associateBy { it.number }
        val enBy = en.associateBy { it.number }
        val urBy = ur.associateBy { it.number }
        return keys.entries.sortedBy { it.value }.map { (num, _) ->
            HadithItem(
                book = book,
                number = num,
                arabic = arBy[num]?.text.orEmpty(),
                english = enBy[num]?.text.orEmpty(),
                urdu = urBy[num]?.text.orEmpty()
            )
        }.filter { it.arabic.isNotBlank() || it.english.isNotBlank() }
    }
}
