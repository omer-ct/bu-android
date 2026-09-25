package com.codefixr.beummati.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent

class AyahOfDayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val day = ayahOfDayWithCache(context)
        val title = day.arabicSnippet ?: "Ayah of the day · open app"
        val subtitle = "Surah ${day.surah} · Ayah ${day.ayah}"
        provideContent {
            WidgetShell(
                context = context,
                label = "AYAH OF THE DAY",
                title = title,
                subtitle = subtitle,
                route = "quran/${day.surah}?ayah=${day.ayah}",
            )
        }
    }
}

class AyahOfDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AyahOfDayWidget()
}
