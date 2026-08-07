package dev.obvious.minimallauncher

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper

class CoarseLocationResolver(context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val mainExecutor = context.mainExecutor
    private val handler = Handler(Looper.getMainLooper())
    private var cancellationSignal: CancellationSignal? = null
    private var legacyListener: LocationListener? = null
    private var generation = 0

    @Suppress("MissingPermission")
    fun resolve(callback: (WeatherCoordinates?) -> Unit) {
        cancel()
        val requestGeneration = ++generation
        val providers = availableProviders()
        val recent = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { location ->
            System.currentTimeMillis() - location.time <= MAX_LAST_LOCATION_AGE_MS
        }.maxByOrNull(Location::getTime)
        if (recent != null) {
            callback(recent.toWeatherCoordinates())
            return
        }

        val provider = providers.firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            callback(null)
            return
        }

        var completed = false
        fun complete(location: Location?) {
            if (completed || generation != requestGeneration) return
            completed = true
            cancelPlatformRequest()
            callback(location?.toWeatherCoordinates())
        }

        handler.postDelayed({ complete(null) }, LOCATION_TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= 30) {
            cancellationSignal = CancellationSignal()
            runCatching {
                locationManager.getCurrentLocation(provider, cancellationSignal, mainExecutor, ::complete)
            }.onFailure { complete(null) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = complete(location)
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = complete(null)
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            runCatching { locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
                .onFailure { complete(null) }
        }
    }

    fun cancel() {
        generation += 1
        cancelPlatformRequest()
    }

    private fun cancelPlatformRequest() {
        cancellationSignal?.cancel()
        cancellationSignal = null
        legacyListener?.let { listener -> runCatching { locationManager.removeUpdates(listener) } }
        legacyListener = null
    }

    private fun availableProviders(): List<String> = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).filter { provider -> runCatching { provider in locationManager.allProviders }.getOrDefault(false) }

    private fun Location.toWeatherCoordinates() = WeatherCoordinates(latitude, longitude)

    private companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
        const val MAX_LAST_LOCATION_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
