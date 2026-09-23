package com.codefixr.beummati.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

/**
 * Prayer times from the AlAdhan API (method 4 — Umm al-Qura), cached for the day.
 * Port of the iOS `PrayerService`: falls back to Dubai when location is unavailable.
 */
object PrayerService {
    const val DUBAI_LAT = 25.2048
    const val DUBAI_LON = 55.2708

    private const val TAG = "PrayerService"
    private const val PREFS = "beummati.prayer"
    private const val KEY_DAY = "day.v1"
    private const val KEY_COORDS = "coords.v1"
    private const val FRESH_LOCATION_MILLIS = 30 * 60_000L
    private const val LOCATE_TIMEOUT_MILLIS = 12_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var prefs: SharedPreferences
    private var refreshJob: Job? = null

    private val _day = MutableStateFlow<PrayerDay?>(null)
    val day: StateFlow<PrayerDay?> = _day.asStateFlow()

    private val _status = MutableStateFlow("Prayer times")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _coordinates = MutableStateFlow(Coordinates(DUBAI_LAT, DUBAI_LON))
    val coordinates: StateFlow<Coordinates> = _coordinates.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        decode<PrayerDay>(KEY_DAY)?.let { cached ->
            _day.value = cached
            _status.value = if (cached.date == todayKey()) "Today’s times" else "Cached times"
        }
        decode<Coordinates>(KEY_COORDS)?.let { _coordinates.value = it }
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Loads today's times. Does nothing when they are already cached, unless [force]. */
    fun refresh(context: Context, force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        if (!force && _day.value?.date == todayKey()) return
        val app = context.applicationContext
        refreshJob = scope.launch {
            val coords = locate(app)
            _status.value = if (coords.isFallback) "Using Dubai times" else "Updating prayer times…"
            load(coords)
        }
    }

    /** Resolves the device position, falling back to Dubai. Also used by the Qibla compass. */
    suspend fun locate(context: Context): Coordinates {
        val app = context.applicationContext
        val location = if (hasLocationPermission(app)) currentLocation(app) else null
        val coords = if (location != null) {
            Coordinates(location.latitude, location.longitude, isFallback = false)
        } else {
            Coordinates(DUBAI_LAT, DUBAI_LON, isFallback = true)
        }
        _coordinates.value = coords
        persist(KEY_COORDS, coords)
        return coords
    }

    /** The next prayer still to come today, or tomorrow's Fajr once Isha has passed. */
    fun nextPrayer(from: PrayerDay? = _day.value): NextPrayer? {
        val today = from ?: return null
        val now = LocalTime.now()
        for ((name, raw) in today.obligatory) {
            val at = parseTime(raw) ?: continue
            if (at.isAfter(now)) {
                return NextPrayer(name, raw, Duration.between(now, at).toMinutes().toInt())
            }
        }
        val fajr = parseTime(today.fajr) ?: return null
        val untilMidnight = Duration.between(now, LocalTime.MAX).toMinutes().toInt() + 1
        return NextPrayer("Fajr", today.fajr, untilMidnight + fajr.toSecondOfDay() / 60, isTomorrow = true)
    }

    data class NextPrayer(
        val name: String,
        val time: String,
        val minutesAway: Int,
        val isTomorrow: Boolean = false
    ) {
        val countdown: String
            get() {
                val hours = minutesAway / 60
                val minutes = minutesAway % 60
                return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }
    }

    private suspend fun load(coords: Coordinates) {
        val today = todayKey()
        val url = "https://api.aladhan.com/v1/timings" +
            "?latitude=${coords.latitude}&longitude=${coords.longitude}&method=4"
        val cacheKey = "prayer_${"%.2f".format(coords.latitude)}_${"%.2f".format(coords.longitude)}_$today.json"
        val raw = try {
            Http.getCached(url, cacheKey)
        } catch (e: Exception) {
            Log.w(TAG, "Prayer times request failed", e)
            _status.value = if (_day.value != null) "Offline — showing cached times" else "Couldn’t load prayer times"
            return
        }
        val parsed = parse(raw, coords, today)
        if (parsed == null) {
            _status.value = "Couldn’t read prayer times"
            return
        }
        _day.value = parsed
        persist(KEY_DAY, parsed)
        _status.value = if (coords.isFallback) "Dubai · default location" else "Updated for your location"
    }

    private fun parse(raw: String, coords: Coordinates, today: String): PrayerDay? = runCatching {
        val data = Catalogs.json.parseToJsonElement(raw).jsonObject["data"]!!.jsonObject
        val timings = data["timings"]!!.jsonObject
        val date = data["date"]?.jsonObject
        val hijri = date?.get("hijri")?.jsonObject

        fun time(name: String): String =
            timings[name]?.jsonPrimitive?.content?.substringBefore(' ')?.trim().orEmpty().ifEmpty { "—" }

        fun text(key: String): String = hijri?.get(key)?.jsonPrimitive?.content.orEmpty()

        val month = hijri?.get("month")?.jsonObject?.get("en")?.jsonPrimitive?.content.orEmpty()
        PrayerDay(
            fajr = time("Fajr"),
            sunrise = time("Sunrise"),
            dhuhr = time("Dhuhr"),
            asr = time("Asr"),
            maghrib = time("Maghrib"),
            isha = time("Isha"),
            hijriDate = listOf(text("day"), month, text("year")).filter { it.isNotBlank() }.joinToString(" "),
            hijriWeekday = hijri?.get("weekday")?.jsonObject?.get("en")?.jsonPrimitive?.content.orEmpty(),
            gregorian = date?.get("readable")?.jsonPrimitive?.content.orEmpty(),
            date = today,
            latitude = coords.latitude,
            longitude = coords.longitude
        )
    }.onFailure { Log.w(TAG, "Unexpected AlAdhan payload", it) }.getOrNull()

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw.trim(), timeFormat) }.getOrNull()

    private fun todayKey(): String = LocalDate.now().toString()

    // region Location

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val known = lastKnown(manager)
        if (known != null && System.currentTimeMillis() - known.time < FRESH_LOCATION_MILLIS) return known

        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return known

        val fresh = withTimeoutOrNull(LOCATE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fresh ?: known
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? = runCatching {
        manager.allProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    // endregion

    private inline fun <reified T> decode(key: String): T? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { Catalogs.json.decodeFromString<T>(raw) }.getOrNull()
    }

    private inline fun <reified T> persist(key: String, value: T) {
        prefs.edit().putString(key, Catalogs.json.encodeToString(value)).apply()
    }
}
