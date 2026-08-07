package dev.obvious.minimallauncher

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
    }
}
