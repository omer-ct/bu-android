package com.codefixr.beummati.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.codefixr.beummati.data.ReadingProgressStore

class LastReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val last = ReadingProgressStore.load(context)
        val (title, subtitle) = if (last == null) {
            "Open Be Ummati" to "Your last reading appears here"
        } else {
            "Surah ${last.surah} · Ayah ${last.ayah}" to "Continue where you left off"
        }
        provideContent {
            WidgetShell(
                context = context,
                label = "LAST READING",
                title = title,
                subtitle = subtitle,
                route = if (last == null) "home" else "quran/${last.surah}?ayah=${last.ayah}",
            )
        }
    }
}

class LastReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastReadingWidget()
}
