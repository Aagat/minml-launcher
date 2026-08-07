package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPreferencesTest {
    @Test fun `durable state survives repository recreation`() {
        val backend = MapBackend()
        LauncherPreferences(backend).apply {
            favorites = listOf("profile:pkg/A", "unicode:应用", "profile:pkg/A")
            appearance = Appearance.GRADIENT
            weatherEnabled = true
            weatherLatitude = "41.3874"
            weatherLongitude = "2.1686"
            setMembership(DrawerFilter.DAILY, listOf("b", "a"))
        }

        val restored = LauncherPreferences(backend)
        assertEquals(listOf("profile:pkg/A", "unicode:应用"), restored.favorites)
        assertEquals(Appearance.GRADIENT, restored.appearance)
        assertTrue(restored.weatherEnabled)
        assertEquals("41.3874", restored.weatherLatitude)
        assertEquals(setOf("a", "b"), restored.membership(DrawerFilter.DAILY))
        assertTrue(restored.isMembershipInitialized(DrawerFilter.DAILY))
        assertFalse(restored.isMembershipInitialized(DrawerFilter.WORK))
    }

    @Test fun `corrupt encoded values fail closed`() {
        assertEquals(emptyList<String>(), PreferenceCodec.decode("4:abc"))
        assertEquals(emptyList<String>(), PreferenceCodec.decode("x:value"))
        assertEquals(listOf("", "a:b"), PreferenceCodec.decode(PreferenceCodec.encode(listOf("", "a:b"))))
    }

    private class MapBackend : PreferenceBackend {
        private val strings = mutableMapOf<String, String>()
        private val booleans = mutableMapOf<String, Boolean>()
        override fun getString(key: String, default: String) = strings[key] ?: default
        override fun putString(key: String, value: String) { strings[key] = value }
        override fun getBoolean(key: String, default: Boolean) = booleans[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
    }
}
