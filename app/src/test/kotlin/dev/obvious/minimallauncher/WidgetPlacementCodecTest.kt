package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPlacementCodecTest {
    @Test fun `widget placement order and height survive round trip`() {
        val placements = listOf(WidgetPlacement(9, 240), WidgetPlacement(3, 100))
        assertEquals(placements, WidgetPlacementCodec.decode(WidgetPlacementCodec.encode(placements)))
    }

    @Test fun `corrupt duplicate and unsafe widget records fail closed`() {
        assertEquals(
            listOf(WidgetPlacement(7, 320), WidgetPlacement(8, 80)),
            WidgetPlacementCodec.decode("broken;7:900;7:100;8:2;-1:160;9:nope"),
        )
    }
}
