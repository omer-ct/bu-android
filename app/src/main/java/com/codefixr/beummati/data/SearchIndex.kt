package com.codefixr.beummati.data

enum class SearchKind(val label: String) {
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

/** Query over everything bundled in assets: library, hadith index, quotes, duas, sahaba. */
object SearchIndex {
    private const val PER_KIND_LIMIT = 15

    fun search(query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val q = needle.lowercase()

        fun String.hit(): Boolean = contains(q, ignoreCase = true)

        val hits = mutableListOf<SearchHit>()

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
            .flatMap { book -> book.chapters.map { book to it } }
            .filter { (book, chapter) -> chapter.name.hit() || book.name.hit() }
            .take(PER_KIND_LIMIT)
            .map { (book, chapter) ->
                SearchHit(
                    kind = SearchKind.HADITH,
                    title = chapter.name,
                    subtitle = book.name,
                    body = if (chapter.count > 0) "${chapter.count} hadith" else "",
                    destination = Destination.HadithChapter(book.slug, chapter.index)
                )
            }

        hits += Catalogs.scholarQuotes
            .filter { it.english.hit() || it.author.hit() || it.title.hit() || it.theme.hit() || it.urdu.contains(needle) }
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
                category.titleEn.hit() || dua.english.hit() || dua.transliteration.hit() ||
                    dua.arabic.contains(needle) || category.titleAr.contains(needle)
            }
            .take(PER_KIND_LIMIT)
            .map { (category, dua) ->
                SearchHit(
                    kind = SearchKind.DUA,
                    title = category.titleEn,
                    subtitle = dua.reference,
                    body = dua.english.ifBlank { dua.transliteration },
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

    private fun destinationFor(series: LibrarySeries): Destination = when (series.kind) {
        "sahaba" -> Destination.Sahaba
        else -> Destination.Series(series.id)
    }
}
