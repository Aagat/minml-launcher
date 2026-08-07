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
        assertEquals(GestureDecision.VERTICAL, gesture.update(14f, 40f))
        assertEquals(GestureDecision.VERTICAL, gesture.update(80f, 42f))
    }

    @Test fun `ambiguous diagonal remains undecided until direction dominates`() {
        val gesture = GestureStateMachine(threshold = 10f)
        gesture.begin(0f, 0f)
        assertEquals(GestureDecision.UNDECIDED, gesture.update(15f, 14f))
        assertEquals(GestureDecision.HORIZONTAL_RIGHT, gesture.update(30f, 14f))
    }
}
