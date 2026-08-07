package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class AppPresentationPolicyTest {
    private val personal = AppEntry("personal:camera", "Camera", "camera", "Main", 0, false, false)
    private val work = AppEntry("work:camera", "Camera", "camera", "Main", 10, true, false)

    @Test fun `hidden stable IDs are removed without affecting another profile`() {
        val visible = AppPresentationPolicy.visibleCatalog(
            listOf(personal, work),
            hiddenIds = setOf("personal:camera"),
            aliases = emptyMap(),
        )
        assertEquals(listOf(work), visible)
    }

    @Test fun `aliases change presentation without mutating catalog identity`() {
        val presented = AppPresentationPolicy.presented(personal, mapOf(personal.stableId to "Pocket camera"))
        assertEquals("Pocket camera", presented.label)
        assertEquals(personal.stableId, presented.stableId)
        assertNotSame(personal, presented)
        assertSame(personal, AppPresentationPolicy.presented(personal, emptyMap()))
    }
}
