package dev.obvious.minimallauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    @Test fun `ranking follows exact prefix word substring then fuzzy priority`() {
        val apps = listOf(
            testApp("fuzzy", "Mail App"),
            testApp("substring", "Roadmap"),
            testApp("word", "City Map"),
            testApp("prefix", "Mapping Tool"),
            testApp("exact", "map"),
        )

        assertEquals(
            listOf("exact", "prefix", "word", "substring", "fuzzy"),
            AppSearch.rank(apps, "map").map { it.stableId },
        )
    }

    @Test fun `matching is case insensitive and accent tolerant`() {
        assertTrue(AppSearch.score("Cámara", "CAMARA") > 0)
        assertEquals("cafe", AppSearch.normalize(" Café "))
    }

    @Test fun `equal scores have deterministic alphabetical and stable id order`() {
        val apps = listOf(testApp("b", "Notes"), testApp("a", "notes"), testApp("z", "Alpha"))
        assertEquals(listOf("z", "a", "b"), AppSearch.rank(apps, "").map { it.stableId })
    }
}
