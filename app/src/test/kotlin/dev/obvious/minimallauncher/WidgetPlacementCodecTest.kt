package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetPlacementCodecTest {
    @Test fun `widget placement order and geometry survive round trip`() {
        val placements = listOf(
            WidgetPlacement(9, 240, xPermille = 640, yPermille = 180, widthPermille = 360),
            WidgetPlacement(3, 100, xPermille = 0, yPermille = 900, widthPermille = 800),
        )
        assertEquals(placements, WidgetPlacementCodec.decode(WidgetPlacementCodec.encode(placements)))
    }

    @Test fun `legacy records migrate and corrupt duplicate records fail closed`() {
        assertEquals(
            listOf(WidgetPlacement(7, 640), WidgetPlacement(8, 80)),
            WidgetPlacementCodec.decode("broken;7:900;7:100;8:2;-1:160;9:nope"),
        )
    }

    @Test fun `unsafe geometry is clamped`() {
        assertEquals(
            listOf(WidgetPlacement(7, 640, xPermille = 1000, yPermille = 0, widthPermille = 250)),
            WidgetPlacementCodec.decode("7:900:1400:-40:10"),
        )
    }

    @Test fun `pixel and normalized positions round trip within a pixel`() {
        val available = 713
        val normalized = WidgetGeometryPolicy.permilleFromPixels(417, available)
        assertTrue(abs(417 - WidgetGeometryPolicy.pixelsFromPermille(normalized, available)) <= 1)
    }

    @Test fun `built in geometry falls back and clamps`() {
        val fallback = WidgetGeometry(120, 0, 100, 1000)
        assertEquals(fallback, WidgetGeometryCodec.decode("nope", fallback))
        assertEquals(
            WidgetGeometry(640, 1000, 0, 250),
            WidgetGeometryCodec.decode("900:1200:-40:10", fallback),
        )
    }
}
