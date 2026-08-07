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
    private val cancellationSignals = mutableListOf<CancellationSignal>()
    private val legacyListeners = mutableListOf<LocationListener>()
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

        val enabledProviders = providers.filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (enabledProviders.isEmpty()) {
            callback(null)
            return
        }

        var completed = false
        var providersRemaining = enabledProviders.size
        fun finish(location: Location?) {
            if (completed || generation != requestGeneration) return
            completed = true
            cancelPlatformRequest()
            callback(location?.toWeatherCoordinates())
        }
        fun providerComplete(location: Location?) {
            if (completed || generation != requestGeneration) return
            if (location != null) finish(location)
            else if (--providersRemaining == 0) finish(null)
        }

        handler.postDelayed({ finish(null) }, LOCATION_TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= 30) {
            enabledProviders.forEach { provider ->
                val signal = CancellationSignal().also(cancellationSignals::add)
                runCatching {
                    locationManager.getCurrentLocation(provider, signal, mainExecutor, ::providerComplete)
                }.onFailure { providerComplete(null) }
            }
        } else {
            enabledProviders.forEach { provider ->
                @Suppress("DEPRECATION")
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = providerComplete(location)
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                legacyListeners += listener
                @Suppress("DEPRECATION")
                runCatching { locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
                    .onFailure { providerComplete(null) }
            }
        }
    }

    fun cancel() {
        generation += 1
        cancelPlatformRequest()
    }

    private fun cancelPlatformRequest() {
        cancellationSignals.forEach(CancellationSignal::cancel)
        cancellationSignals.clear()
        legacyListeners.forEach { listener -> runCatching { locationManager.removeUpdates(listener) } }
        legacyListeners.clear()
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
