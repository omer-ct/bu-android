package com.codefixr.beummati.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle bearing to the Kaaba. Port of the iOS `QiblaService` math. */
object Qibla {
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    private const val EARTH_RADIUS_KM = 6371.0

    /** Initial bearing in degrees clockwise from true north. */
    fun bearing(
        fromLat: Double,
        fromLon: Double,
        toLat: Double = KAABA_LAT,
        toLon: Double = KAABA_LON
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val deltaLon = Math.toRadians(toLon - fromLon)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance in kilometres. */
    fun distanceKm(
        fromLat: Double,
        fromLon: Double,
        toLat: Double = KAABA_LAT,
        toLon: Double = KAABA_LON
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(toLon - fromLon)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    /** Signed shortest turn (−180…180) from [heading] to [bearing]. */
    fun relativeAngle(bearing: Double, heading: Double): Double {
        val delta = (bearing - heading + 540.0) % 360.0 - 180.0
        return delta
    }
}
