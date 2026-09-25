package com.codefixr.beummati.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.codefixr.beummati.MainActivity
import com.codefixr.beummati.data.DeepLinks
import com.codefixr.beummati.ui.theme.Brass
import com.codefixr.beummati.ui.theme.Ink
import com.codefixr.beummati.ui.theme.InkSecondary
import com.codefixr.beummati.ui.theme.Parchment
import com.codefixr.beummati.ui.theme.Teal

internal val WidgetColorProviders = ColorProviders(
    light = androidx.compose.material3.lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        secondary = Brass,
        background = Parchment,
        onBackground = Ink,
        onSurface = Ink,
        onSurfaceVariant = InkSecondary,
    ),
    dark = androidx.compose.material3.darkColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        secondary = Brass,
        background = Color(0xFF1A1816),
        onBackground = Parchment,
        onSurface = Parchment,
        onSurfaceVariant = InkSecondary,
    ),
)

internal fun openAppAction(context: Context): Action = openRouteAction(context, "home")

internal fun openRouteAction(context: Context, route: String): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        DeepLinks.putRoute(this, route)
        data = when {
            route.startsWith("quran/") -> {
                val parts = route.removePrefix("quran/").substringBefore('?')
                val n = parts.toIntOrNull() ?: 1
                val ayah = Regex("""ayah=(\d+)""").find(route)?.groupValues?.getOrNull(1)?.toIntOrNull()
                DeepLinks.surah(n, ayah)
            }
            route == "salah" -> DeepLinks.salah()
            else -> DeepLinks.home()
        }
    }
    return actionStartActivity(intent)
}

@Composable
internal fun WidgetShell(
    context: Context,
    label: String,
    title: String,
    subtitle: String,
    route: String = "home",
) {
    GlanceTheme(colors = WidgetColorProviders) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(16.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(openRouteAction(context, route)),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
                    maxLines = 3,
                )
            }
        }
    }
}
