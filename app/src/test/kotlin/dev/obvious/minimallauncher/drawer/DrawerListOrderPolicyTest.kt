package dev.obvious.minimallauncher.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerListOrderPolicyTest {
    private val ranked = listOf("most relevant", "second", "least relevant")

    @Test fun `enabled preference reverses ranked apps while keyboard and search are active`() {
        val reversed = DrawerListOrderPolicy.shouldReverse(
            reverseWithKeyboard = true,
            keyboardVisible = true,
            searchActive = true,
            currentlyReversed = false,
        )
        assertEquals(
            listOf("least relevant", "second", "most relevant"),
            DrawerListOrderPolicy.order(ranked, reversed),
        )
    }

    @Test fun `list remains normal when preference keyboard or search is inactive`() {
        assertEquals(false, DrawerListOrderPolicy.shouldReverse(false, true, true, currentlyReversed = false))
        assertEquals(false, DrawerListOrderPolicy.shouldReverse(true, false, true, currentlyReversed = false))
        assertEquals(false, DrawerListOrderPolicy.shouldReverse(true, true, false, currentlyReversed = false))
    }

    @Test fun `reversed search remains reversed after keyboard is dismissed until query is cleared`() {
        assertEquals(true, DrawerListOrderPolicy.shouldReverse(true, false, true, currentlyReversed = true))
        assertEquals(false, DrawerListOrderPolicy.shouldReverse(true, false, false, currentlyReversed = true))
    }

    @Test fun `most relevant app is retained after reversing the presentation`() {
        val reversed = DrawerListOrderPolicy.order(ranked, reversed = true)
        assertEquals(
            "most relevant",
            DrawerListOrderPolicy.mostRelevant(reversed, reversed = true),
        )
    }
}
