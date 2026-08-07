package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterEngineTest {
    private val apps = listOf(
        testApp("one", "One"),
        testApp("two", "Two", work = true),
        testApp("three", "Three"),
        testApp("four", "Four", work = true),
    )
    private val memberships = mapOf(
        DrawerFilter.DAILY to setOf("one", "three"),
        DrawerFilter.WORK to setOf("two"),
        DrawerFilter.MEDIA to emptySet(),
    )

    @Test fun `all ignores membership while custom scopes use persisted ids`() {
        assertEquals(listOf("one", "three"), FilterEngine.apply(apps, DrawerFilter.ALL, memberships).map { it.stableId })
        assertEquals(listOf("one", "three"), FilterEngine.apply(apps, DrawerFilter.DAILY, memberships).map { it.stableId })
        assertEquals(listOf("two", "four"), FilterEngine.apply(apps, DrawerFilter.WORK, memberships).map { it.stableId })
        assertEquals(emptyList<AppEntry>(), FilterEngine.apply(apps, DrawerFilter.MEDIA, memberships))
    }

    @Test fun `filter navigation is cyclic in both directions`() {
        assertEquals(DrawerFilter.DAILY, DrawerFilter.ALL.cycle(1))
        assertEquals(DrawerFilter.ALL, DrawerFilter.MEDIA.cycle(1))
        assertEquals(DrawerFilter.MEDIA, DrawerFilter.ALL.cycle(-1))
    }
}
