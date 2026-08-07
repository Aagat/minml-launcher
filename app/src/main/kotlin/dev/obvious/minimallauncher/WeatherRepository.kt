package dev.obvious.minimallauncher

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val unit: String,
    val fetchedAt: Long,
)

sealed interface WeatherResult {
    data class Available(val snapshot: WeatherSnapshot, val stale: Boolean) : WeatherResult
    data class Unavailable(val message: String) : WeatherResult
}

enum class WeatherCacheState { MISSING, FRESH, STALE, EXPIRED }

object WeatherCachePolicy {
    fun state(fetchedAt: Long?, now: Long): WeatherCacheState {
        if (fetchedAt == null || fetchedAt <= 0L) return WeatherCacheState.MISSING
        val age = (now - fetchedAt).coerceAtLeast(0L)
        return when {
            age < WeatherRepository.REFRESH_INTERVAL -> WeatherCacheState.FRESH
            age < WeatherRepository.MAX_CACHE_AGE -> WeatherCacheState.STALE
            else -> WeatherCacheState.EXPIRED
        }
    }

    fun matchesLocation(
        cachedLatitude: Double?,
        cachedLongitude: Double?,
        requestedLatitude: Double,
        requestedLongitude: Double,
    ): Boolean = cachedLatitude != null && cachedLongitude != null &&
        abs(cachedLatitude - requestedLatitude) <= LOCATION_TOLERANCE_DEGREES &&
        abs(cachedLongitude - requestedLongitude) <= LOCATION_TOLERANCE_DEGREES

    private const val LOCATION_TOLERANCE_DEGREES = 0.1
}

object WeatherUnitPolicy {
    private val fahrenheitCountries = setOf("US", "BS", "PW")

    fun resolve(preference: WeatherTemperatureUnit, country: String): WeatherTemperatureUnit = when (preference) {
        WeatherTemperatureUnit.SYSTEM -> if (country.uppercase(Locale.ROOT) in fahrenheitCountries) {
            WeatherTemperatureUnit.FAHRENHEIT
        } else {
            WeatherTemperatureUnit.CELSIUS
        }
        else -> preference
    }

    fun symbol(unit: WeatherTemperatureUnit): String = when (unit) {
        WeatherTemperatureUnit.CELSIUS -> "C"
        WeatherTemperatureUnit.FAHRENHEIT -> "F"
        WeatherTemperatureUnit.SYSTEM -> error("System temperature unit must be resolved first")
    }
}

class WeatherRepository(private val runtime: SharedPreferences) {
    private val executor = Executors.newSingleThreadExecutor()

    fun load(
        latitude: Double,
        longitude: Double,
        unitPreference: WeatherTemperatureUnit,
        callback: (WeatherResult) -> Unit,
    ) {
        val unit = WeatherUnitPolicy.resolve(unitPreference, Locale.getDefault().country)
        val cached = readCache(latitude, longitude, WeatherUnitPolicy.symbol(unit))
        val now = System.currentTimeMillis()
        val cacheState = WeatherCachePolicy.state(cached?.fetchedAt, now)
        if (cached != null && cacheState == WeatherCacheState.FRESH) {
            callback(WeatherResult.Available(cached, false))
            return
        }
        if (cached != null && cacheState == WeatherCacheState.STALE) {
            callback(WeatherResult.Available(cached, true))
        }
        executor.execute {
            val fetched = runCatching { fetch(latitude, longitude, unit) }
            fetched.onSuccess { snapshot ->
                writeCache(snapshot, latitude, longitude)
                callback(WeatherResult.Available(snapshot, false))
            }.onFailure {
                if (cached == null || cacheState == WeatherCacheState.EXPIRED) {
                    callback(WeatherResult.Unavailable("weather unavailable"))
                }
            }
        }
    }

    fun close() = executor.shutdownNow()

    private fun fetch(
        latitude: Double,
        longitude: Double,
        unit: WeatherTemperatureUnit,
    ): WeatherSnapshot {
        val fahrenheit = unit == WeatherTemperatureUnit.FAHRENHEIT
        val unitQuery = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val endpoint = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto$unitQuery"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "MinimalLauncher/0.1")
        try {
            if (connection.responseCode !in 200..299) error("Weather provider returned ${connection.responseCode}")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            val code = current.getInt("weather_code")
            return WeatherSnapshot(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                condition = condition(code),
                unit = WeatherUnitPolicy.symbol(unit),
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCache(snapshot: WeatherSnapshot, latitude: Double, longitude: Double) {
        runtime.edit()
            .putInt("weather.temperature", snapshot.temperature)
            .putInt("weather.high", snapshot.high)
            .putInt("weather.low", snapshot.low)
            .putString("weather.condition", snapshot.condition)
            .putString("weather.unit", snapshot.unit)
            .putLong("weather.fetchedAt", snapshot.fetchedAt)
            .putString("weather.latitude", latitude.toString())
            .putString("weather.longitude", longitude.toString())
            .apply()
    }

    private fun readCache(latitude: Double, longitude: Double, expectedUnit: String): WeatherSnapshot? {
        val fetchedAt = runtime.getLong("weather.fetchedAt", 0L)
        if (fetchedAt == 0L) return null
        if (!WeatherCachePolicy.matchesLocation(
                runtime.getString("weather.latitude", null)?.toDoubleOrNull(),
                runtime.getString("weather.longitude", null)?.toDoubleOrNull(),
                latitude,
                longitude,
            )
        ) return null
        val cachedUnit = runtime.getString("weather.unit", "C") ?: "C"
        if (cachedUnit != expectedUnit) return null
        return WeatherSnapshot(
            temperature = runtime.getInt("weather.temperature", 0),
            high = runtime.getInt("weather.high", 0),
            low = runtime.getInt("weather.low", 0),
            condition = runtime.getString("weather.condition", "") ?: "",
            unit = cachedUnit,
            fetchedAt = fetchedAt,
        )
    }

    private fun condition(code: Int): String = when (code) {
        0 -> "CLR"
        1, 2 -> "FAIR"
        3 -> "CLD"
        45, 48 -> "FOG"
        in 51..67, in 80..82 -> "RAIN"
        in 71..77, in 85..86 -> "SNOW"
        in 95..99 -> "STORM"
        else -> "WX"
    }

    companion object {
        const val REFRESH_INTERVAL = 60 * 60 * 1000L
        const val MAX_CACHE_AGE = 6 * 60 * 60 * 1000L
    }
}
