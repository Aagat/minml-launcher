package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureStateMachineTest {
    @Test fun `horizontal swipe locks and suppresses later vertical drift`() {
        val gesture = GestureStateMachine(threshold = 10f)
        gesture.begin(100f, 100f)
        assertEquals(GestureDecision.UNDECIDED, gesture.update(106f, 103f))
        assertEquals(GestureDecision.HORIZONTAL_LEFT, gesture.update(70f, 104f))
        assertEquals(GestureDecision.HORIZONTAL_LEFT, gesture.update(60f, 180f))
    }

    @Test fun `vertical movement wins before a row can become a filter swipe`() {
        val gesture = GestureStateMachine(threshold = 10f)
        gesture.begin(10f, 10f)
        assertEquals(GestureDecision.VERTICAL_DOWN, gesture.update(14f, 40f))
        assertEquals(GestureDecision.VERTICAL_DOWN, gesture.update(80f, 42f))
    }

    @Test fun `vertical direction distinguishes drawer close from upward scrolling`() {
        val gesture = GestureStateMachine(threshold = 10f)
        gesture.begin(100f, 100f)
        assertEquals(GestureDecision.VERTICAL_UP, gesture.update(98f, 70f))

        gesture.begin(100f, 100f)
        assertEquals(GestureDecision.VERTICAL_DOWN, gesture.update(102f, 130f))
    }

    @Test fun `ambiguous diagonal remains undecided until direction dominates`() {
        val gesture = GestureStateMachine(threshold = 10f)
        gesture.begin(0f, 0f)
        assertEquals(GestureDecision.UNDECIDED, gesture.update(15f, 14f))
        assertEquals(GestureDecision.HORIZONTAL_RIGHT, gesture.update(30f, 14f))
    }

    @Test fun `drawer dismissal requires long fast downward travel`() {
        assertEquals(false, DrawerGesturePolicy.isDismissGesture(10f, 140f, 120L, 150f, 800f))
        assertEquals(false, DrawerGesturePolicy.isDismissGesture(10f, 240f, 600L, 150f, 800f))
        assertEquals(false, DrawerGesturePolicy.isDismissGesture(180f, 200f, 180L, 150f, 800f))
        assertEquals(true, DrawerGesturePolicy.isDismissGesture(20f, 240f, 180L, 150f, 800f))
    }

    @Test fun `filter swipe requires deliberate final horizontal dominance`() {
        assertEquals(null, DrawerGesturePolicy.filterStep(-60f, 4f, 72f))
        assertEquals(null, DrawerGesturePolicy.filterStep(-100f, 90f, 72f))
        assertEquals(1, DrawerGesturePolicy.filterStep(-100f, 20f, 72f))
        assertEquals(-1, DrawerGesturePolicy.filterStep(100f, 20f, 72f))
    }
}
