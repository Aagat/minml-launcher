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

    @Test fun `weather cache is reused only near its source coordinates`() {
        assertEquals(true, WeatherCachePolicy.matchesLocation(41.38, 2.17, 41.40, 2.20))
        assertEquals(false, WeatherCachePolicy.matchesLocation(41.38, 2.17, 40.42, -3.70))
        assertEquals(false, WeatherCachePolicy.matchesLocation(null, null, 41.40, 2.20))
    }
}
