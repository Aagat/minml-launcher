package dev.obvious.minimallauncher.catalog

import dev.obvious.minimallauncher.preferences.PreferenceCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCacheCodecTest {
    @Test fun `catalog cache round trips profile and category identity`() {
        val apps = listOf(
            testApp("personal:pkg/Main", "Cámara"),
            testApp("work:pkg/Main", "Work: Mail", work = true, media = true),
        )

        assertEquals(apps, CatalogCacheCodec.decode(CatalogCacheCodec.encode(apps)))
    }

    @Test fun `malformed records are ignored`() {
        val valid = CatalogCacheCodec.encode(listOf(testApp("ok", "Okay")))
        val mixed = PreferenceCodec.encode(PreferenceCodec.decode(valid) + "3:bad")
        assertEquals(listOf("ok"), CatalogCacheCodec.decode(mixed).map { it.stableId })
    }
}
