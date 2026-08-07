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
            weatherLocationMode = WeatherLocationMode.APPROXIMATE
            drawerDismissDistanceSensitivity = 78
            drawerDismissSpeedSensitivity = 84
            customFilters = listOf(CustomFilter("custom:focus", "Focus"), CustomFilter("custom:focus", "Duplicate"))
            setCustomMembership("custom:focus", listOf("b", "a"))
            fontScalePercent = 118
            fontColor = 0xFF112233.toInt()
            accentColor = 0xFFABCDEF.toInt()
            autoShowKeyboard = false
            showFilterBar = false
            showDrawerGradient = false
            showSearchUnderline = false
            appListTopMarginDp = 36
            appListRightMarginDp = 28
            hideStatusBar = true
            setMembership(DrawerFilter.DAILY, listOf("b", "a"))
        }

        val restored = LauncherPreferences(backend)
        assertEquals(listOf("profile:pkg/A", "unicode:应用"), restored.favorites)
        assertEquals(Appearance.GRADIENT, restored.appearance)
        assertTrue(restored.weatherEnabled)
        assertEquals("41.3874", restored.weatherLatitude)
        assertEquals(WeatherLocationMode.APPROXIMATE, restored.weatherLocationMode)
        assertEquals(78, restored.drawerDismissDistanceSensitivity)
        assertEquals(84, restored.drawerDismissSpeedSensitivity)
        assertEquals(listOf(CustomFilter("custom:focus", "Focus")), restored.customFilters)
        assertEquals(setOf("a", "b"), restored.customMembership("custom:focus"))
        assertEquals(118, restored.fontScalePercent)
        assertEquals(0xFF112233.toInt(), restored.fontColor)
        assertEquals(0xFFABCDEF.toInt(), restored.accentColor)
        assertFalse(restored.autoShowKeyboard)
        assertFalse(restored.showFilterBar)
        assertFalse(restored.showDrawerGradient)
        assertFalse(restored.showSearchUnderline)
        assertEquals(36, restored.appListTopMarginDp)
        assertEquals(28, restored.appListRightMarginDp)
        assertTrue(restored.hideStatusBar)
        assertEquals(setOf("a", "b"), restored.membership(DrawerFilter.DAILY))
        assertTrue(restored.isMembershipInitialized(DrawerFilter.DAILY))
        assertFalse(restored.isMembershipInitialized(DrawerFilter.WORK))
    }

    @Test fun `corrupt encoded values fail closed`() {
        assertEquals(emptyList<String>(), PreferenceCodec.decode("4:abc"))
        assertEquals(emptyList<String>(), PreferenceCodec.decode("x:value"))
        assertEquals(listOf("", "a:b"), PreferenceCodec.decode(PreferenceCodec.encode(listOf("", "a:b"))))
    }

    @Test fun `drawer sensitivity defaults and clamps to supported range`() {
        val backend = MapBackend()
        val preferences = LauncherPreferences(backend)
        assertEquals(65, preferences.drawerDismissDistanceSensitivity)
        assertEquals(65, preferences.drawerDismissSpeedSensitivity)
        preferences.drawerDismissDistanceSensitivity = 140
        preferences.drawerDismissSpeedSensitivity = -20
        assertEquals(100, preferences.drawerDismissDistanceSensitivity)
        assertEquals(0, preferences.drawerDismissSpeedSensitivity)
    }

    @Test fun `display customization defaults and clamps font scale`() {
        val preferences = LauncherPreferences(MapBackend())
        assertEquals(100, preferences.fontScalePercent)
        assertEquals(0xFFF4F4F2.toInt(), preferences.fontColor)
        assertEquals(0xFFB7F36B.toInt(), preferences.accentColor)
        assertTrue(preferences.autoShowKeyboard)
        assertTrue(preferences.showFilterBar)
        assertTrue(preferences.showDrawerGradient)
        assertTrue(preferences.showSearchUnderline)
        assertEquals(24, preferences.appListTopMarginDp)
        assertEquals(20, preferences.appListRightMarginDp)
        assertFalse(preferences.hideStatusBar)
        preferences.fontScalePercent = 300
        preferences.appListTopMarginDp = 300
        preferences.appListRightMarginDp = -3
        assertEquals(150, preferences.fontScalePercent)
        assertEquals(96, preferences.appListTopMarginDp)
        assertEquals(0, preferences.appListRightMarginDp)
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
