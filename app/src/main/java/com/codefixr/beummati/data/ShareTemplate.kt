package com.codefixr.beummati.data

/**
 * Premium Daily Quran share designs (Instagram 4:5).
 * Mapped 1:1 to [com.codefixr.beummati.ui.DailyQuranLab.Style].
 */
enum class ShareTemplate(val label: String, val blurb: String) {
    MIHRAB("Mihrab", "Lit prayer niche with brass"),
    FOLIO("Folio", "Stone wall + emerald slab"),
    FAJR("Fajr", "Pre-dawn sky and dunes"),
    KUFIC_CIRCUIT("Kufic circuit", "Dark lattice, technical geometry"),
    INK_BLOOM("Ink bloom", "Indigo wash on paper"),
    ZELLIJ_STACK("Zellij", "Moroccan tile colour bands"),
    JADE_VELVET("Jade velvet", "Deep jade + champagne type"),
    CYANOTYPE("Cyanotype", "Prussian blue sun-print"),
    BASALT("Basalt", "Brutalist cut stone"),
    NACRE("Nacre", "Soft pearl iridescence"),
    TERRAZZO_BONE("Terrazzo", "Speckled bone slab"),
    OXBLOOD_TAZHIB("Oxblood", "Manuscript gold corners"),
    CONTOUR_TIDE("Contour", "Topographic petrol lines"),
    RISO_DUO("Riso duo", "Cobalt + tangerine print"),
    NIGHT_GIRIH("Night girih", "Midnight geometry constellation"),
    KEYSTONE("Keystone", "Header strip · accent rail · info panel"),
    DATUM("Datum", "Side rail · numbered translation stack"),
    MASTHEAD("Masthead", "Editorial rules · display reference"),
    CASCADE("Cascade", "Stepped colour bands · layered read"),
    SIGNAL("Signal", "Badge · bold bar · poster hierarchy")
}
