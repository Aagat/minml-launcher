package dev.obvious.minimallauncher.drawer

import dev.obvious.minimallauncher.catalog.AppEntry
import dev.obvious.minimallauncher.catalog.testApp
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterEngineTest {
    private val apps = listOf(
        testApp("one", "One"),
        testApp("two", "Two", work = true),
        testApp("three", "Three"),
        testApp("four", "Four", work = true),
    )
    @Test fun `all ignores membership while custom scopes use persisted ids`() {
        assertEquals(listOf("one", "three"), FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.ALL), emptySet()).map { it.stableId })
        assertEquals(listOf("one", "three"), FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.DAILY), setOf("one", "three")).map { it.stableId })
        assertEquals(listOf("two", "four"), FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.WORK), emptySet()).map { it.stableId })
        assertEquals(emptyList<AppEntry>(), FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.MEDIA), emptySet()))
        assertEquals(listOf("three"), FilterEngine.apply(apps, FilterSpec("custom:focus", "focus"), setOf("three", "four")).map { it.stableId })
    }

    @Test fun `stock separation leaves personal and work sections with user installed apps`() {
        val catalog = listOf(
            testApp("personal-stock", "Camera", stock = true),
            testApp("personal-installed", "Reader"),
            testApp("work-stock", "Work settings", work = true, stock = true),
            testApp("work-installed", "Work chat", work = true),
        )

        assertEquals(
            listOf("personal-installed"),
            FilterEngine.apply(catalog, FilterSpec.builtIn(DrawerFilter.ALL), emptySet(), separateStockApps = true)
                .map { it.stableId },
        )
        assertEquals(
            listOf("work-installed"),
            FilterEngine.apply(catalog, FilterSpec.builtIn(DrawerFilter.WORK), emptySet(), separateStockApps = true)
                .map { it.stableId },
        )
        assertEquals(
            listOf("personal-stock", "work-stock"),
            FilterEngine.apply(catalog, FilterSpec.builtIn(DrawerFilter.STOCK), emptySet(), separateStockApps = true)
                .map { it.stableId },
        )
    }

    @Test fun `filter navigation is cyclic in both directions`() {
        assertEquals(DrawerFilter.DAILY, DrawerFilter.ALL.cycle(1))
        assertEquals(DrawerFilter.ALL, DrawerFilter.MEDIA.cycle(1))
        assertEquals(DrawerFilter.MEDIA, DrawerFilter.ALL.cycle(-1))
    }

    @Test fun `dynamic filter navigation includes custom categories`() {
        val filters = FilterCatalog.available(listOf(CustomFilter("custom:focus", "focus")))
        assertEquals("custom:focus", FilterCatalog.cycle(filters, "builtin:media", 1).id)
        assertEquals("builtin:all", FilterCatalog.cycle(filters, "custom:focus", 1).id)
        assertEquals("custom:focus", FilterCatalog.cycle(filters, "builtin:all", -1).id)
    }

    @Test fun `drawer header accent range covers only filter name`() {
        val header = DrawerHeaderPolicy.content("daily", 4)
        assertEquals("daily/4", header.text)
        assertEquals("daily", header.text.substring(0, header.accentEnd))
        assertEquals("/4", header.text.substring(header.accentEnd))
    }

    @Test fun `stock filter is only available when enabled`() {
        assertEquals(false, FilterCatalog.available(emptyList()).any { it.builtIn == DrawerFilter.STOCK })
        assertEquals(true, FilterCatalog.available(emptyList(), separateStockApps = true).any { it.builtIn == DrawerFilter.STOCK })
    }
}
