package com.codefixr.beummati.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.codefixr.beummati.data.PrayerService

class NextPrayerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val content = loadContent(context)
        provideContent { WidgetShell(context, content.label, content.title, content.subtitle, route = "salah") }
    }

    private fun loadContent(context: Context): WidgetCopy {
        runCatching { PrayerService.init(context) }
        val next = runCatching { PrayerService.nextPrayer() }.getOrNull()
        if (next == null) {
            return WidgetCopy(
                label = "PRAYER",
                title = "Open Be Ummati",
                subtitle = "Prayer times will appear here",
            )
        }
        val whenLabel = if (next.isTomorrow) "Tomorrow" else "In ${next.countdown}"
        return WidgetCopy(
            label = "NEXT PRAYER",
            title = "${next.name} · ${next.time}",
            subtitle = whenLabel,
        )
    }

    private data class WidgetCopy(
        val label: String,
        val title: String,
        val subtitle: String,
    )
}

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
