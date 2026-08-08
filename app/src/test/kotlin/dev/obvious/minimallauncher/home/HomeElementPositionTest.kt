package dev.obvious.minimallauncher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeElementPositionTest {
    @Test fun `position survives round trip`() {
        val position = HomeElementPosition(237, 814)
        assertEquals(position, HomeElementPositionCodec.decode(HomeElementPositionCodec.encode(position)))
    }

    @Test fun `position clamps to available normalized range`() {
        assertEquals(
            HomeElementPosition(HomeElementPosition.END, HomeElementPosition.START),
            HomeElementPositionCodec.decode("1300:-25"),
        )
    }

    @Test fun `invalid position falls back to bottom end`() {
        assertEquals(HomeElementPosition.DEFAULT, HomeElementPositionCodec.decode(""))
        assertEquals(HomeElementPosition.DEFAULT, HomeElementPositionCodec.decode("left:bottom"))
        assertEquals(HomeElementPosition.DEFAULT, HomeElementPositionCodec.decode("1:2:3"))
    }
}
