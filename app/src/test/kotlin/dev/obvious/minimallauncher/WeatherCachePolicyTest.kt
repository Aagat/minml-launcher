package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCachePolicyTest {
    private val now = 10 * 60 * 60 * 1000L

    @Test fun `cache state honors hourly refresh and six hour expiry`() {
        assertEquals(WeatherCacheState.MISSING, WeatherCachePolicy.state(null, now))
        assertEquals(WeatherCacheState.FRESH, WeatherCachePolicy.state(now - 59 * 60 * 1000L, now))
        assertEquals(WeatherCacheState.STALE, WeatherCachePolicy.state(now - 60 * 60 * 1000L, now))
        assertEquals(WeatherCacheState.STALE, WeatherCachePolicy.state(now - 359 * 60 * 1000L, now))
        assertEquals(WeatherCacheState.EXPIRED, WeatherCachePolicy.state(now - 6 * 60 * 60 * 1000L, now))
    }

    @Test fun `future timestamps remain fresh after clock correction`() {
        assertEquals(WeatherCacheState.FRESH, WeatherCachePolicy.state(now + 1_000L, now))
    }
}
