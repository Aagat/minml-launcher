package dev.obvious.minimallauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherLocationPolicyTest {
    private val manual = WeatherCoordinates(41.3874, 2.1686)
    private val approximate = WeatherCoordinates(41.4, 2.2)

    @Test fun `manual mode requires valid manual coordinates`() {
        assertEquals(
            WeatherCoordinateDecision.Use(manual, manualFallback = false),
            WeatherLocationPolicy.decide(WeatherLocationMode.MANUAL, false, approximate, manual),
        )
        assertEquals(
            WeatherCoordinateDecision.ManualLocationRequired,
            WeatherLocationPolicy.decide(WeatherLocationMode.MANUAL, false, null, null),
        )
    }

    @Test fun `approximate mode prefers granted device location`() {
        assertEquals(
            WeatherCoordinateDecision.Use(approximate, manualFallback = false),
            WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, true, approximate, manual),
        )
    }

    @Test fun `approximate mode falls back to manual coordinates`() {
        assertEquals(
            WeatherCoordinateDecision.Use(manual, manualFallback = true),
            WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, false, null, manual),
        )
        assertEquals(
            WeatherCoordinateDecision.Use(manual, manualFallback = true),
            WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, true, null, manual),
        )
    }

    @Test fun `approximate mode distinguishes denial from unavailable location`() {
        assertEquals(
            WeatherCoordinateDecision.PermissionRequired,
            WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, false, null, null),
        )
        assertEquals(
            WeatherCoordinateDecision.LocationUnavailable,
            WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, true, null, null),
        )
    }
}
