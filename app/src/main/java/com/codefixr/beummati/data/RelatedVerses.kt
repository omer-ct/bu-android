package com.codefixr.beummati.data

/**
 * Curated thematic cross-links (gentle “see also”, not exhaustive tafsir).
 * Port of iOS `RelatedVerses`.
 */
object RelatedVerses {
    val map: Map<String, List<String>> = mapOf(
        "94:5" to listOf("94:6", "2:286", "65:3"),
        "94:6" to listOf("94:5", "2:214"),
        "2:286" to listOf("94:5", "65:7", "2:185"),
        "2:255" to listOf("3:2", "59:22", "112:1"),
        "2:152" to listOf("13:28", "29:45", "33:41"),
        "2:153" to listOf("2:45", "94:5", "3:200"),
        "3:139" to listOf("47:35", "9:40", "94:5"),
        "3:159" to listOf("9:128", "21:107"),
        "9:51" to listOf("65:3", "3:160", "10:84"),
        "13:28" to listOf("2:152", "39:23", "29:45"),
        "14:7" to listOf("2:152", "16:18"),
        "18:10" to listOf("18:16", "21:87"),
        "21:87" to listOf("21:88", "39:53"),
        "39:53" to listOf("15:56", "12:87", "2:222"),
        "48:1" to listOf("94:1", "110:1"),
        "55:13" to listOf("55:16", "16:18"),
        "65:3" to listOf("9:51", "65:2", "2:286"),
        "67:2" to listOf("67:1", "3:185"),
        "93:5" to listOf("93:4", "94:5"),
        "1:5" to listOf("1:6", "112:1"),
        "1:6" to listOf("1:7", "1:5"),
        "112:1" to listOf("2:163", "112:2", "59:22"),
        "113:1" to listOf("114:1", "113:2"),
        "114:1" to listOf("113:1", "114:2")
    )

    fun related(to: String): List<String> = map[to].orEmpty()

    /** Parses `"2:255"` into surah + ayah, or null if malformed. */
    fun parseKey(key: String): Pair<Int, Int>? {
        val parts = key.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        if (surah < 1 || ayah < 1) return null
        return surah to ayah
    }
}
