package com.codefixr.beummati.data

/**
 * Real share-card colours (not a post-process grade).
 * Null fields mean “keep the design’s own colour”.
 */
data class SharePalette(
    val background: Int? = null,
    val arabic: Int? = null,
    val english: Int? = null,
    val urdu: Int? = null,
    val reference: Int? = null,
    val brand: Int? = null,
    val brandText: String = DEFAULT_BRAND
) {
    /** True when a background tint is applied on top of the template (design stays). */
    val hasBackgroundTint: Boolean get() = background != null

    @Deprecated("Use hasBackgroundTint — backgrounds no longer flatten the template.")
    val usesFlatBackground: Boolean get() = hasBackgroundTint

    fun withBrandText(text: String) = copy(brandText = text.trim().ifBlank { DEFAULT_BRAND })

    companion object {
        const val DEFAULT_BRAND = "BE UMMATI"

        val DESIGN = SharePalette()

        val NIGHT = SharePalette(
            background = 0xFF0B1220.toInt(),
            arabic = 0xFFF2F4F8.toInt(),
            english = 0xFFC9A24A.toInt(),
            urdu = 0xFFCBD9E2.toInt(),
            reference = 0xFF93A0B8.toInt(),
            brand = 0xFFC9A24A.toInt()
        )
        val IVORY = SharePalette(
            background = 0xFFF4F1EA.toInt(),
            arabic = 0xFF14161A.toInt(),
            english = 0xFF2B3A67.toInt(),
            urdu = 0xFF1A1A1A.toInt(),
            reference = 0xFF6C7570.toInt(),
            brand = 0xFFB23A2E.toInt()
        )
        val EMERALD = SharePalette(
            background = 0xFF0B5A45.toInt(),
            arabic = 0xFFE7D7A8.toInt(),
            english = 0xFFF2EDE1.toInt(),
            urdu = 0xFFC4CFC6.toInt(),
            reference = 0xFF9BC3AE.toInt(),
            brand = 0xFFE7D7A8.toInt()
        )
        val BRASS = SharePalette(
            background = 0xFF1A1208.toInt(),
            arabic = 0xFFE7D7A8.toInt(),
            english = 0xFFC79A4B.toInt(),
            urdu = 0xFFD8C9A8.toInt(),
            reference = 0xFFA89060.toInt(),
            brand = 0xFFC79A4B.toInt()
        )
        val ROSE = SharePalette(
            background = 0xFF4A0F16.toInt(),
            arabic = 0xFFF0E6D2.toInt(),
            english = 0xFFD4AF5A.toInt(),
            urdu = 0xFFE8DCC8.toInt(),
            reference = 0xFFC9A24A.toInt(),
            brand = 0xFFD4AF5A.toInt()
        )
        val OCEAN = SharePalette(
            background = 0xFF0A2A43.toInt(),
            arabic = 0xFFF7F9FA.toInt(),
            english = 0xFFD9A441.toInt(),
            urdu = 0xFFCBD9E2.toInt(),
            reference = 0xFF7FE0C4.toInt(),
            brand = 0xFFCBD9E2.toInt()
        )

        val PRESETS: List<Pair<String, SharePalette>> = listOf(
            "Design" to DESIGN,
            "Night" to NIGHT,
            "Ivory" to IVORY,
            "Emerald" to EMERALD,
            "Brass" to BRASS,
            "Rose" to ROSE,
            "Ocean" to OCEAN
        )

        /** Swatches for fine-tuning each role. */
        val SWATCHES: List<Int> = listOf(
            0xFFF2EDE1.toInt(), 0xFFF4F1EA.toInt(), 0xFFE6E3DC.toInt(), 0xFFCBD9E2.toInt(),
            0xFF14161A.toInt(), 0xFF0B1220.toInt(), 0xFF060D0C.toInt(), 0xFF4A0F16.toInt(),
            0xFF0B5A45.toInt(), 0xFF0A2A43.toInt(), 0xFF1B4B8F.toInt(), 0xFF2B3EE0.toInt(),
            0xFFC79A4B.toInt(), 0xFFD4AF5A.toInt(), 0xFFB23A2E.toInt(), 0xFFFF5A36.toInt()
        )
    }
}
