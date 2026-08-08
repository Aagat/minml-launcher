package dev.obvious.minimallauncher.drawer

import dev.obvious.minimallauncher.catalog.AppSearch
import dev.obvious.minimallauncher.catalog.CatalogCacheCodec
import dev.obvious.minimallauncher.catalog.testApp
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleReliabilityTest {
    @Test fun `five hundred app fuzzy search stays within the interaction budget`() {
        val apps = (0 until 500).map { index ->
            testApp("personal:$index", "Application ${index.toString().padStart(3, '0')}")
        }
        repeat(5) { AppSearch.rank(apps, "aplctn 499") }
        val timings = (0 until 20).map {
            measureNanoTime { AppSearch.rank(apps, "aplctn 499") } / 1_000_000.0
        }.sorted()
        val p95 = timings[18]
        assertEquals("personal:499", AppSearch.rank(apps, "aplctn 499").first().stableId)
        assertTrue("500-app search p95 was ${p95}ms", p95 < 100.0)
    }

    @Test fun `large filter scopes preserve profile isolation and membership`() {
        val personal = (0 until 128).map { testApp("p:$it", "Personal $it") }
        val work = (0 until 40).map { testApp("w:$it", "Work $it", work = true) }
        val apps = personal + work
        val dailyIds = personal.filterIndexed { index, _ -> index % 3 == 0 }.mapTo(mutableSetOf()) { it.stableId }
        assertEquals(128, FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.ALL), emptySet()).size)
        assertEquals(40, FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.WORK), emptySet()).size)
        assertEquals(dailyIds.size, FilterEngine.apply(apps, FilterSpec.builtIn(DrawerFilter.DAILY), dailyIds).size)
    }

    @Test fun `large catalog cache round trip remains complete and deterministic`() {
        val apps = (0 until 500).map { testApp("id:$it", "App $it", work = it % 7 == 0, media = it % 5 == 0) }
        assertEquals(apps, CatalogCacheCodec.decode(CatalogCacheCodec.encode(apps)))
    }
}
