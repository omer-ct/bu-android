package com.codefixr.beummati.ui.qibla

import android.Manifest
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codefixr.beummati.data.Coordinates
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.Qibla
import com.codefixr.beummati.ui.ContentCard
import com.codefixr.beummati.ui.MutedText
import com.codefixr.beummati.ui.ScreenScaffold
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coords by PrayerService.coordinates.collectAsState()
    var locating by remember { mutableStateOf(false) }

    suspend fun locate() {
        locating = true
        PrayerService.locate(context)
        locating = false
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { locate() }
    }

    LaunchedEffect(Unit) { locate() }

    val heading = rememberTrueHeading(coords)
    val bearing = remember(coords) { Qibla.bearing(coords.latitude, coords.longitude) }
    val distanceKm = remember(coords) { Qibla.distanceKm(coords.latitude, coords.longitude) }
    val turn = heading?.let { Qibla.relativeAngle(bearing, it) }

    ScreenScaffold(title = "Qibla", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (turn == null) "Compass unavailable" else instruction(turn),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            MutedText("Qibla is ${bearing.roundToInt()}° from true north")

            CompassDial(
                needleDegrees = turn?.toFloat(),
                headingDegrees = heading?.toFloat(),
                modifier = Modifier.size(250.dp)
            )

            ContentCard {
                Text("Your position", fontWeight = FontWeight.SemiBold)
                MutedText(
                    when {
                        locating -> "Locating…"
                        coords.isFallback -> "Dubai (default) — allow location for an exact bearing"
                        else -> "%.4f, %.4f".format(coords.latitude, coords.longitude)
                    }
                )
                MutedText("${distanceKm.roundToInt()} km to the Kaaba", modifier = Modifier.padding(top = 4.dp))
                if (coords.isFallback && !locating) {
                    Button(
                        onClick = {
                            permission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    ) { Text("Use my location") }
                }
                if (heading == null) {
                    MutedText(
                        "No usable compass sensor on this device — face ${bearing.roundToInt()}° from north manually.",
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            MutedText(
                "Hold the phone flat, away from metal. Move it in a figure-eight to recalibrate.",
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
    }
}

private fun instruction(turn: Double): String {
    val degrees = abs(turn).roundToInt()
    return when {
        degrees <= 5 -> "Facing the Qibla"
        turn > 0 -> "Turn $degrees° right"
        else -> "Turn $degrees° left"
    }
}

@Composable
private fun CompassDial(needleDegrees: Float?, headingDegrees: Float?, modifier: Modifier = Modifier) {
    // Accumulate rotation so the needle always takes the shortest path across 0°/360°.
    var rotation by remember { mutableFloatStateOf(needleDegrees ?: 0f) }
    LaunchedEffect(needleDegrees) {
        val target = needleDegrees ?: return@LaunchedEffect
        rotation += ((target - rotation + 540f) % 360f) - 180f
    }
    val animated by animateFloatAsState(rotation, animationSpec = tween(220), label = "qiblaNeedle")

    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = onSurface.copy(alpha = 0.06f), radius = radius, center = center)
            drawCircle(
                color = onSurface.copy(alpha = 0.25f),
                radius = radius - 2f,
                center = center,
                style = Stroke(width = 2f)
            )

            for (tick in 0 until 24) {
                val major = tick % 6 == 0
                rotate(degrees = tick * 15f, pivot = center) {
                    drawLine(
                        color = onSurface.copy(alpha = if (major) 0.5f else 0.2f),
                        start = Offset(center.x, center.y - radius + 6f),
                        end = Offset(center.x, center.y - radius + if (major) 26f else 14f),
                        strokeWidth = if (major) 3f else 2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Where north sits on the dial as the phone turns.
            if (headingDegrees != null) {
                rotate(degrees = -headingDegrees, pivot = center) {
                    drawLine(
                        color = onSurface.copy(alpha = 0.45f),
                        start = center,
                        end = Offset(center.x, center.y - radius + 30f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }

            if (needleDegrees != null) {
                rotate(degrees = animated, pivot = center) {
                    val tip = Offset(center.x, center.y - radius + 24f)
                    drawLine(
                        color = primary,
                        start = Offset(center.x, center.y + radius * 0.35f),
                        end = tip,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                    drawPath(
                        Path().apply {
                            moveTo(tip.x, tip.y - 18f)
                            lineTo(tip.x - 20f, tip.y + 18f)
                            lineTo(tip.x + 20f, tip.y + 18f)
                            close()
                        },
                        color = primary
                    )
                }
            }

            drawCircle(color = primary, radius = 9f, center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 100.dp)
        ) {
            Text(
                headingDegrees?.let { "${it.roundToInt()}°" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            MutedText("heading")
        }
    }
}

/**
 * Device heading in whole degrees from true north, from the rotation vector when available
 * and the legacy orientation sensor otherwise. Null when neither sensor exists.
 */
@Composable
private fun rememberTrueHeading(coords: Coordinates): Double? {
    val context = LocalContext.current
    var magnetic by remember { mutableFloatStateOf(Float.NaN) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationVector = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        @Suppress("DEPRECATION")
        val fallback = if (rotationVector == null) manager?.getDefaultSensor(Sensor.TYPE_ORIENTATION) else null
        val sensor = rotationVector ?: fallback

        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val degrees = if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, angles)
                    Math.toDegrees(angles[0].toDouble()).toFloat()
                } else {
                    event.values.firstOrNull() ?: return
                }
                magnetic = ((degrees % 360f) + 360f) % 360f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }

    val declination = remember(coords) {
        GeomagneticField(
            coords.latitude.toFloat(),
            coords.longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
    }

    if (magnetic.isNaN()) return null
    val trueHeading = ((magnetic + declination) % 360f + 360f) % 360f
    return trueHeading.roundToInt().toDouble()
}
