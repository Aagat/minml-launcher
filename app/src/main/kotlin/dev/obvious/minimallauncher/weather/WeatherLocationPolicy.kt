package dev.obvious.minimallauncher.weather

enum class WeatherLocationMode { MANUAL, APPROXIMATE }

data class WeatherCoordinates(val latitude: Double, val longitude: Double)

sealed interface WeatherCoordinateDecision {
    data class Use(val coordinates: WeatherCoordinates, val manualFallback: Boolean) : WeatherCoordinateDecision
    data object PermissionRequired : WeatherCoordinateDecision
    data object LocationUnavailable : WeatherCoordinateDecision
    data object ManualLocationRequired : WeatherCoordinateDecision
}

object WeatherLocationPolicy {
    fun decide(
        mode: WeatherLocationMode,
        coarsePermissionGranted: Boolean,
        approximate: WeatherCoordinates?,
        manual: WeatherCoordinates?,
    ): WeatherCoordinateDecision = when (mode) {
        WeatherLocationMode.MANUAL -> manual
            ?.let { WeatherCoordinateDecision.Use(it, manualFallback = false) }
            ?: WeatherCoordinateDecision.ManualLocationRequired

        WeatherLocationMode.APPROXIMATE -> when {
            approximate != null -> WeatherCoordinateDecision.Use(approximate, manualFallback = false)
            manual != null -> WeatherCoordinateDecision.Use(manual, manualFallback = true)
            !coarsePermissionGranted -> WeatherCoordinateDecision.PermissionRequired
            else -> WeatherCoordinateDecision.LocationUnavailable
        }
    }
}
