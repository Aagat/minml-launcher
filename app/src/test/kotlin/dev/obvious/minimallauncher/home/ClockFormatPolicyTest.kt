package dev.obvious.minimallauncher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatPolicyTest {
    @Test fun `explicit clock formats override system format`() {
        assertEquals("hh:mm", ClockFormatPolicy.pattern(ClockFormat.TWELVE_HOUR, true))
        assertEquals("HH:mm", ClockFormatPolicy.pattern(ClockFormat.TWENTY_FOUR_HOUR, false))
    }

    @Test fun `system clock format follows Android preference`() {
        assertEquals("hh:mm", ClockFormatPolicy.pattern(ClockFormat.SYSTEM, false))
        assertEquals("HH:mm", ClockFormatPolicy.pattern(ClockFormat.SYSTEM, true))
    }
}
