package com.codefixr.beummati.data

enum class SearchKind(val label: String) {
    QURAN("Qur’an"),
    SERIES("Series"),
    CHAPTER("Chapters"),
    HADITH("Hadith"),
    QUOTE("Scholars"),
    DUA("Duas"),
    SAHABA("Sahaba")
}

data class SearchHit(
    val kind: SearchKind,
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    val destination: Destination
)

/**
 * Local bundled catalogue search, plus live Qur’an (quran.com) and Hadith (islamic.app) text search.
 */
object SearchIndex {
    private const val PER_KIND_LIMIT = 15
    private const val QURAN_LIMIT = 20
    private const val HADITH_TEXT_LIMIT = 20

    /**
     * Full search. Local kinds are instant; Qur’an / Hadith text hits need the network.
     */
    suspend fun search(query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()

        val local = localHits(needle)
        val offlineQuran = offlineQuranHits(needle)
        val quran = quranHits(needle)
        val hadithText = hadithTextHits(needle)
        // Arabic / Urdu queries read best off the cached mushaf, so those hits lead.
        val verses = if (hasArabicScript(needle)) {
            dedupeByDestination(offlineQuran + quran)
        } else {
            dedupeByDestination(quran + offlineQuran)
        }
        // Text hits first so verse / matn matches aren’t buried under catalogue noise.
        return verses + hadithText + local
    }

    private fun dedupeByDestination(hits: List<SearchHit>): List<SearchHit> {
        val seen = HashSet<Destination>()
        return hits.filter { seen.add(it.destination) }
    }

    private fun normalizeArabic(text: String): String {
        val harakat = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
        return harakat.replace(text, "")
    }

    private fun localHits(needle: String): List<SearchHit> {
        val q = needle.lowercase()
        val qAr = normalizeArabic(needle)
        fun String.hit(): Boolean = contains(q, ignoreCase = true)
        fun String.arHit(): Boolean =
            qAr.isNotBlank() && normalizeArabic(this).contains(qAr, ignoreCase = true)

        val hits = mutableListOf<SearchHit>()

        needle.toIntOrNull()?.takeIf { it in 1..114 }?.let { n ->
            hits += SearchHit(
                kind = SearchKind.QURAN,
                title = "Surah $n",
                subtitle = "Open surah",
                body = "",
                destination = Destination.Surah(n)
            )
        }

        hits += Catalogs.library.series
            .filter { it.title.hit() || it.subtitle.orEmpty().hit() }
            .take(PER_KIND_LIMIT)
            .map { series ->
                SearchHit(
                    kind = SearchKind.SERIES,
                    title = series.title,
                    subtitle = series.subtitle.orEmpty(),
                    body = "${series.chapters.size} chapters",
                    destination = destinationFor(series)
                )
            }

        hits += Catalogs.library.series
            .flatMap { series -> series.chapters.map { series to it } }
            .filter { (_, chapter) -> chapter.title.hit() }
            .take(PER_KIND_LIMIT)
            .map { (series, chapter) ->
                SearchHit(
                    kind = SearchKind.CHAPTER,
                    title = chapter.title,
                    subtitle = series.title,
                    body = if (Catalogs.hasAudio(series.id, chapter.id)) "Audio" else "",
                    destination = Destination.LectureChapter(series.id, chapter.id)
                )
            }

        hits += Catalogs.hadith.books
            .filter { it.name.hit() || it.nameUrdu.contains(needle) }
            .take(PER_KIND_LIMIT)
            .map { book ->
                SearchHit(
                    kind = SearchKind.HADITH,
                    title = book.name,
                    subtitle = book.nameUrdu,
                    body = if (book.hadithCount > 0) "${book.hadithCount} hadith" else "",
                    destination = Destination.HadithBook(book.slug)
                )
            }

        hits += Catalogs.hadith.books
            .flatMap { book -> book.chapters.map { book to it } }
            .filter { (book, chapter) ->
                chapter.name.hit() ||
                    chapter.nameUrdu.contains(needle) ||
                    book.name.hit() ||
                    book.nameUrdu.contains(needle)
            }
            .take(PER_KIND_LIMIT)
            .map { (book, chapter) ->
                SearchHit(
                    kind = SearchKind.HADITH,
                    title = chapter.nameUrdu.ifBlank { chapter.name },
                    subtitle = listOf(book.name, chapter.name.takeIf { chapter.nameUrdu.isNotBlank() })
                        .filterNotNull()
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    body = if (chapter.count > 0) "${chapter.count} hadith" else "",
                    destination = Destination.HadithChapter(book.slug, chapter.index)
                )
            }

        hits += Catalogs.scholarQuotes
            .filter {
                it.english.hit() || it.author.hit() || it.title.hit() || it.theme.hit() ||
                    it.urdu.contains(needle) || it.urdu.arHit()
            }
            .take(PER_KIND_LIMIT)
            .map { quote ->
                SearchHit(
                    kind = SearchKind.QUOTE,
                    title = quote.title.ifBlank { quote.author },
                    subtitle = listOf(quote.author, quote.theme).filter { it.isNotBlank() }.joinToString(" · "),
                    body = quote.english,
                    destination = Destination.Scholars
                )
            }

        hits += Catalogs.hisnAlMuslim.categories
            .flatMap { category -> category.duas.map { category to it } }
            .filter { (category, dua) ->
                category.titleEn.hit() || category.titleUr.contains(needle) ||
                    dua.english.hit() || dua.transliteration.hit() ||
                    dua.arabic.contains(needle) || dua.arabic.arHit() ||
                    dua.urdu.contains(needle) || dua.urdu.arHit() ||
                    category.titleAr.contains(needle) || category.titleAr.arHit()
            }
            .take(PER_KIND_LIMIT)
            .map { (category, dua) ->
                SearchHit(
                    kind = SearchKind.DUA,
                    title = category.titleEn,
                    subtitle = listOf(category.titleUr, dua.reference).filter { it.isNotBlank() }.joinToString(" · "),
                    body = dua.english.ifBlank { dua.urdu.ifBlank { dua.transliteration } },
                    destination = Destination.DuaCategory(category.id)
                )
            }

        hits += Catalogs.sahabaStories
            .filter { it.name.hit() || it.title.hit() || it.english.hit() || it.theme.hit() || it.urdu.contains(needle) }
            .take(PER_KIND_LIMIT)
            .map { story ->
                SearchHit(
                    kind = SearchKind.SAHABA,
                    title = story.title.ifBlank { story.name },
                    subtitle = story.name,
                    body = story.english,
                    destination = Destination.Sahaba
                )
            }

        return hits
    }

    private suspend fun hadithTextHits(needle: String): List<SearchHit> {
        val results = runCatching { HadithApi.search(needle, HADITH_TEXT_LIMIT) }.getOrDefault(emptyList())
        val known = Catalogs.hadith.books.associateBy { it.slug }
        return results.mapNotNull { hit ->
            val slug = normalizeHadithSlug(hit.collection) ?: return@mapNotNull null
            val book = known[slug]
            val chapterIndex = hit.bookNumber.takeIf { it > 0 }
                ?: book?.chapters?.firstOrNull()?.index
                ?: return@mapNotNull null
            val body = buildList {
                val g = hit.grades.firstOrNull()?.grade
                if (!g.isNullOrBlank()) add(g)
                add(hit.snippet.ifBlank { hit.english.ifBlank { hit.arabic } })
            }.joinToString(" · ")
            SearchHit(
                kind = SearchKind.HADITH,
                title = "${book?.name ?: slug} · #${hit.hadithNumber}",
                subtitle = hit.chapterTitle.ifBlank { book?.nameUrdu.orEmpty() },
                body = body,
                destination = Destination.HadithChapter(slug, chapterIndex, hit.hadithNumber.toIntOrNull())
            )
        }
    }

    private fun normalizeHadithSlug(collection: String): String? {
        val raw = collection.trim().lowercase()
        return when (raw) {
            "bukhari", "muslim", "tirmidhi", "nasai", "ibnmajah", "malik", "nawawi", "qudsi" -> raw
            "abudawud", "abu-dawud", "abudawood", "abu dawud" -> "abudawud"
            else -> Catalogs.hadith.books.firstOrNull { it.slug.equals(raw, ignoreCase = true) }?.slug
        }
    }

    /** Cached-mushaf hits, so ayah results still appear with no network. */
    private suspend fun offlineQuranHits(needle: String): List<SearchHit> =
        runCatching { QuranApi.searchOffline(needle, QURAN_LIMIT) }
            .getOrDefault(emptyList())
            .map { it.asQuranHit() }

    private fun Ayah.asQuranHit(): SearchHit {
        val body = buildList {
            if (english.isNotBlank()) add(english)
            if (urdu.isNotBlank()) add(urdu)
            val ar = arabic(SettingsStore.arabicFont.value)
            if (ar.isNotBlank()) add(ar)
        }.joinToString("\n")
        return SearchHit(
            kind = SearchKind.QURAN,
            title = key,
            subtitle = "Qur’an",
            body = body,
            destination = Destination.Surah(surah, numberInSurah)
        )
    }

    private suspend fun quranHits(needle: String): List<SearchHit> {
        val englishId = SettingsStore.englishTranslationId.value
        val urduId = SettingsStore.urduTranslationId.value
        val ayahs = runCatching {
            QuranApi.search(needle, englishId, urduId, QURAN_LIMIT)
        }.getOrDefault(emptyList())

        val surahHits = runCatching {
            QuranApi.surahs().filter { s ->
                s.number.toString() == needle ||
                    s.englishName.contains(needle, ignoreCase = true) ||
                    s.englishNameTranslation.contains(needle, ignoreCase = true) ||
                    s.name.contains(needle)
            }.take(8)
        }.getOrDefault(emptyList())

        val fromSurahs = surahHits.map { s ->
            SearchHit(
                kind = SearchKind.QURAN,
                title = s.englishName.ifBlank { "Surah ${s.number}" },
                subtitle = listOf(s.name, s.englishNameTranslation).filter { it.isNotBlank() }.joinToString(" · "),
                body = if (s.numberOfAyahs > 0) "${s.numberOfAyahs} ayahs" else "Surah ${s.number}",
                destination = Destination.Surah(s.number)
            )
        }

        val fromAyahs = ayahs.map { it.asQuranHit() }

        // Prefer ayah hits; keep unique surah rows that aren’t already covered.
        val ayahSurahs = fromAyahs.map { (it.destination as Destination.Surah).number }.toSet()
        return fromAyahs + fromSurahs.filter { it.destination.let { d -> d is Destination.Surah && d.number !in ayahSurahs } }
    }

    private fun destinationFor(series: LibrarySeries): Destination = when (series.kind) {
        "sahaba" -> Destination.Sahaba
        else -> Destination.Series(series.id)
    }
}
