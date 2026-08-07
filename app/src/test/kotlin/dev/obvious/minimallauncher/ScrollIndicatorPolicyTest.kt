package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollIndicatorPolicyTest {
    @Test fun `sub-row scrolling moves thumb continuously`() {
        val top = geometry(first = 0, childTop = 0)
        val quarterRow = geometry(first = 0, childTop = -12)
        val fullRow = geometry(first = 1, childTop = 0)

        assertEquals(0f, top.offset, 0.001f)
        assertTrue(quarterRow.offset > top.offset)
        assertTrue(quarterRow.offset < fullRow.offset)
    }

    @Test fun `thumb reaches track end and hides for unscrollable content`() {
        val bottom = geometry(first = 15, childTop = 0)
        assertEquals((240 - bottom.height).toFloat(), bottom.offset, 0.001f)
        assertNull(
            ScrollIndicatorPolicy.calculate(5, 0, 0, 48, 480, 240, 30),
        )
    }

    private fun geometry(first: Int, childTop: Int): ScrollThumbGeometry {
        val geometry = ScrollIndicatorPolicy.calculate(
            totalItems = 25,
            firstVisibleItem = first,
            firstChildTop = childTop,
            rowHeight = 48,
            viewportHeight = 480,
            trackHeight = 240,
            minimumThumbHeight = 30,
        )
        assertNotNull(geometry)
        return geometry!!
    }
}
