package dev.obvious.minimallauncher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimePolicyTest {
    @Test fun `screen transitions accumulate only interactive intervals`() {
        val hour = 60 * 60 * 1000L
        assertEquals(
            3 * hour,
            ScreenTimeCalculator.calculate(
                dayStartMillis = 0L,
                nowMillis = 8 * hour,
                currentlyInteractive = true,
                events = listOf(
                    ScreenStateEvent(2 * hour, true),
                    ScreenStateEvent(4 * hour, false),
                    ScreenStateEvent(7 * hour, true),
                ),
            ),
        )
    }

    @Test fun `first transition establishes the state at midnight`() {
        val hour = 60 * 60 * 1000L
        assertEquals(
            2 * hour,
            ScreenTimeCalculator.calculate(
                dayStartMillis = 0L,
                nowMillis = 6 * hour,
                currentlyInteractive = false,
                events = listOf(ScreenStateEvent(2 * hour, false)),
            ),
        )
        assertEquals(
            4 * hour,
            ScreenTimeCalculator.calculate(
                dayStartMillis = 0L,
                nowMillis = 6 * hour,
                currentlyInteractive = true,
                events = listOf(ScreenStateEvent(2 * hour, true)),
            ),
        )
    }

    @Test fun `no transitions uses current interactive state and bounds invalid data`() {
        val hour = 60 * 60 * 1000L
        assertEquals(5 * hour, ScreenTimeCalculator.calculate(0L, 5 * hour, true, emptyList()))
        assertEquals(0L, ScreenTimeCalculator.calculate(0L, 5 * hour, false, emptyList()))
        assertEquals(
            hour,
            ScreenTimeCalculator.calculate(
                0L,
                2 * hour,
                false,
                listOf(
                    ScreenStateEvent(-hour, true),
                    ScreenStateEvent(hour, true),
                    ScreenStateEvent(3 * hour, false),
                ),
            ),
        )
    }

    @Test fun `compact duration remains readable`() {
        assertEquals("<1m", ScreenTimeFormatter.compact(59_999L))
        assertEquals("17m", ScreenTimeFormatter.compact(17 * 60_000L))
        assertEquals("2h", ScreenTimeFormatter.compact(2 * 60 * 60_000L))
        assertEquals("2h 7m", ScreenTimeFormatter.compact((2 * 60 + 7) * 60_000L))
        assertEquals("less than one minute", ScreenTimeFormatter.spoken(1_000L))
        assertEquals("1 hour 1 minute", ScreenTimeFormatter.spoken(61 * 60_000L))
    }

    @Test fun `detailed usage merges filters and deterministically ranks packages`() {
        assertEquals(
            listOf(
                AppUsageDuration("video.app", 95_000L),
                AppUsageDuration("chat.app", 90_000L),
                AppUsageDuration("maps.app", 90_000L),
            ),
            DetailedUsagePolicy.rank(
                usage = listOf(
                    AppUsageDuration("chat.app", 60_000L),
                    AppUsageDuration("video.app", 95_000L),
                    AppUsageDuration("chat.app", 30_000L),
                    AppUsageDuration("maps.app", 90_000L),
                    AppUsageDuration("system.service", 500_000L),
                    AppUsageDuration("launcher", 800_000L),
                    AppUsageDuration("other.launcher", 700_000L),
                    AppUsageDuration("unused.app", 0L),
                ),
                eligiblePackages = setOf(
                    "chat.app",
                    "video.app",
                    "maps.app",
                    "launcher",
                    "other.launcher",
                    "unused.app",
                ),
                excludedPackages = setOf("launcher", "other.launcher"),
            ),
        )
    }

    @Test fun `detailed usage honors its visible row limit`() {
        val usage = (1..8).map { AppUsageDuration("app.$it", it * 10_000L) }
        assertEquals(
            listOf("app.8", "app.7", "app.6", "app.5"),
            DetailedUsagePolicy.rank(usage, usage.map { it.packageName }.toSet(), setOf("launcher"))
                .map { it.packageName },
        )
    }
}
