package dev.obvious.minimallauncher.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGesturePolicyTest {
    @Test fun `vertical swipes resolve in both Home directions`() {
        assertEquals(HomeSwipeDirection.UP, HomeGesturePolicy.swipeDirection(8f, -80f, 24f))
        assertEquals(HomeSwipeDirection.DOWN, HomeGesturePolicy.swipeDirection(-8f, 80f, 24f))
    }

    @Test fun `short horizontal and diagonal movement remains undecided`() {
        assertNull(HomeGesturePolicy.swipeDirection(2f, 20f, 24f))
        assertNull(HomeGesturePolicy.swipeDirection(80f, 70f, 24f))
        assertNull(HomeGesturePolicy.swipeDirection(-80f, -70f, 24f))
    }
}
