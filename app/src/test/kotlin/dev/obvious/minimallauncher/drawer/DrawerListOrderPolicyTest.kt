package dev.obvious.minimallauncher.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerListOrderPolicyTest {
    private val ranked = listOf("most relevant", "second", "least relevant")

    @Test fun `enabled preference reverses ranked apps while keyboard and search are active`() {
        assertEquals(
            listOf("least relevant", "second", "most relevant"),
            DrawerListOrderPolicy.order(
                ranked,
                reverseWithKeyboard = true,
                keyboardVisible = true,
                searchActive = true,
            ),
        )
    }

    @Test fun `list remains normal when preference keyboard or search is inactive`() {
        assertEquals(ranked, DrawerListOrderPolicy.order(ranked, false, keyboardVisible = true, searchActive = true))
        assertEquals(ranked, DrawerListOrderPolicy.order(ranked, true, keyboardVisible = false, searchActive = true))
        assertEquals(ranked, DrawerListOrderPolicy.order(ranked, true, keyboardVisible = true, searchActive = false))
    }

    @Test fun `most relevant app is retained after reversing the presentation`() {
        val reversed = DrawerListOrderPolicy.order(
            ranked,
            reverseWithKeyboard = true,
            keyboardVisible = true,
            searchActive = true,
        )
        assertEquals(
            "most relevant",
            DrawerListOrderPolicy.mostRelevant(
                reversed,
                reverseWithKeyboard = true,
                keyboardVisible = true,
                searchActive = true,
            ),
        )
    }
}
