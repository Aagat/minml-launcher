package dev.obvious.minimallauncher.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerListOrderPolicyTest {
    private val ranked = listOf("most relevant", "second", "least relevant")

    @Test fun `enabled preference reverses ranked apps while keyboard is visible`() {
        assertEquals(
            listOf("least relevant", "second", "most relevant"),
            DrawerListOrderPolicy.order(ranked, reverseWithKeyboard = true, keyboardVisible = true),
        )
    }

    @Test fun `list remains ranked normally when preference is disabled or keyboard is hidden`() {
        assertEquals(ranked, DrawerListOrderPolicy.order(ranked, reverseWithKeyboard = false, keyboardVisible = true))
        assertEquals(ranked, DrawerListOrderPolicy.order(ranked, reverseWithKeyboard = true, keyboardVisible = false))
    }

    @Test fun `most relevant app is retained after reversing the presentation`() {
        val reversed = DrawerListOrderPolicy.order(ranked, reverseWithKeyboard = true, keyboardVisible = true)
        assertEquals(
            "most relevant",
            DrawerListOrderPolicy.mostRelevant(reversed, reverseWithKeyboard = true, keyboardVisible = true),
        )
    }
}
