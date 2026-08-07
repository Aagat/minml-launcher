package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherUnitPolicyTest {
    @Test fun `system unit follows locale country`() {
        assertEquals(
            WeatherTemperatureUnit.FAHRENHEIT,
            WeatherUnitPolicy.resolve(WeatherTemperatureUnit.SYSTEM, "US"),
        )
        assertEquals(
            WeatherTemperatureUnit.CELSIUS,
            WeatherUnitPolicy.resolve(WeatherTemperatureUnit.SYSTEM, "ES"),
        )
    }

    @Test fun `explicit unit overrides locale`() {
        assertEquals(
            WeatherTemperatureUnit.CELSIUS,
            WeatherUnitPolicy.resolve(WeatherTemperatureUnit.CELSIUS, "US"),
        )
        assertEquals(
            WeatherTemperatureUnit.FAHRENHEIT,
            WeatherUnitPolicy.resolve(WeatherTemperatureUnit.FAHRENHEIT, "ES"),
        )
    }
}
