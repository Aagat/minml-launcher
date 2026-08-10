package dev.obvious.minimallauncher.preferences

import dev.obvious.minimallauncher.appearance.Appearance
import dev.obvious.minimallauncher.appearance.LauncherFont
import dev.obvious.minimallauncher.appearance.LauncherTextTransform
import dev.obvious.minimallauncher.appearance.toggleSolid
import dev.obvious.minimallauncher.drawer.CustomFilter
import dev.obvious.minimallauncher.drawer.DrawerFilter
import dev.obvious.minimallauncher.drawer.DrawerSurfaceMode
import dev.obvious.minimallauncher.home.HomeElementPosition
import dev.obvious.minimallauncher.home.ClockFormat
import dev.obvious.minimallauncher.weather.WeatherLocationMode
import dev.obvious.minimallauncher.weather.WeatherTemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPreferencesTest {
    @Test fun `solid background toggle has an obvious wallpaper fallback`() {
        assertEquals(Appearance.SOLID, Appearance.AUTO.toggleSolid())
        assertEquals(Appearance.SOLID, Appearance.TRANSPARENT.toggleSolid())
        assertEquals(Appearance.SOLID, Appearance.GRADIENT.toggleSolid())
        assertEquals(Appearance.AUTO, Appearance.SOLID.toggleSolid())
    }

    @Test fun `durable state survives repository recreation`() {
        val backend = MapBackend()
        LauncherPreferences(backend).apply {
            favorites = listOf("profile:pkg/A", "unicode:应用", "profile:pkg/A")
            favoritesPosition = HomeElementPosition(240, 680)
            appearance = Appearance.SOLID
            weatherEnabled = true
            weatherLatitude = "41.3874"
            weatherLongitude = "2.1686"
            weatherLocationMode = WeatherLocationMode.APPROXIMATE
            weatherTemperatureUnit = WeatherTemperatureUnit.FAHRENHEIT
            clockFormat = ClockFormat.TWENTY_FOUR_HOUR
            drawerDismissDistanceSensitivity = 78
            drawerDismissSpeedSensitivity = 84
            customFilters = listOf(CustomFilter("custom:focus", "Focus"), CustomFilter("custom:focus", "Duplicate"))
            setCustomMembership("custom:focus", listOf("b", "a"))
            fontScalePercent = 118
            launcherFont = LauncherFont.SYSTEM_MONO
            textTransform = LauncherTextTransform.UPPERCASE
            animationsEnabled = false
            hiddenApps = setOf("profile:pkg/Hidden")
            appAliases = mapOf("profile:pkg/A" to "  Personal name  ")
            fontColor = 0xFF112233.toInt()
            accentColor = 0xFFABCDEF.toInt()
            solidBackgroundColor = 0xFF102030.toInt()
            drawerSurfaceMode = DrawerSurfaceMode.CUSTOM
            drawerSurfaceColor = 0xFF405060.toInt()
            showBuiltInClock = false
            showScreenTime = true
            showDetailedUsage = true
            autoShowKeyboard = false
            reverseAppListWithKeyboard = true
            showFilterBar = false
            showDrawerGradient = false
            showSearchUnderline = false
            appListTopMarginDp = 36
            appListRightMarginDp = 28
            searchLeftMarginDp = 33
            hideStatusBar = true
            setMembership(DrawerFilter.DAILY, listOf("b", "a"))
        }

        val restored = LauncherPreferences(backend)
        assertEquals(listOf("profile:pkg/A", "unicode:应用"), restored.favorites)
        assertEquals(HomeElementPosition(240, 680), restored.favoritesPosition)
        assertEquals(Appearance.SOLID, restored.appearance)
        assertTrue(restored.weatherEnabled)
        assertEquals("41.3874", restored.weatherLatitude)
        assertEquals(WeatherLocationMode.APPROXIMATE, restored.weatherLocationMode)
        assertEquals(WeatherTemperatureUnit.FAHRENHEIT, restored.weatherTemperatureUnit)
        assertEquals(ClockFormat.TWENTY_FOUR_HOUR, restored.clockFormat)
        assertEquals(78, restored.drawerDismissDistanceSensitivity)
        assertEquals(84, restored.drawerDismissSpeedSensitivity)
        assertEquals(listOf(CustomFilter("custom:focus", "Focus")), restored.customFilters)
        assertEquals(setOf("a", "b"), restored.customMembership("custom:focus"))
        assertEquals(118, restored.fontScalePercent)
        assertEquals(LauncherFont.SYSTEM_MONO, restored.launcherFont)
        assertEquals(LauncherTextTransform.UPPERCASE, restored.textTransform)
        assertFalse(restored.animationsEnabled)
        assertEquals(setOf("profile:pkg/Hidden"), restored.hiddenApps)
        assertEquals(mapOf("profile:pkg/A" to "Personal name"), restored.appAliases)
        assertEquals(0xFF112233.toInt(), restored.fontColor)
        assertEquals(0xFFABCDEF.toInt(), restored.accentColor)
        assertEquals(0xFF102030.toInt(), restored.solidBackgroundColor)
        assertEquals(DrawerSurfaceMode.CUSTOM, restored.drawerSurfaceMode)
        assertEquals(0xFF405060.toInt(), restored.drawerSurfaceColor)
        assertFalse(restored.showBuiltInClock)
        assertTrue(restored.showScreenTime)
        assertTrue(restored.showDetailedUsage)
        assertFalse(restored.autoShowKeyboard)
        assertTrue(restored.reverseAppListWithKeyboard)
        assertFalse(restored.showFilterBar)
        assertFalse(restored.showDrawerGradient)
        assertFalse(restored.showSearchUnderline)
        assertEquals(36, restored.appListTopMarginDp)
        assertEquals(28, restored.appListRightMarginDp)
        assertEquals(33, restored.searchLeftMarginDp)
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
        assertEquals(LauncherFont.GEIST_MONO, preferences.launcherFont)
        assertEquals(LauncherTextTransform.LOWERCASE, preferences.textTransform)
        assertTrue(preferences.animationsEnabled)
        assertTrue(preferences.hiddenApps.isEmpty())
        assertTrue(preferences.appAliases.isEmpty())
        assertEquals(0xFFF4F4F2.toInt(), preferences.fontColor)
        assertEquals(0xFFB7F36B.toInt(), preferences.accentColor)
        assertEquals(0xFF000000.toInt(), preferences.solidBackgroundColor)
        assertEquals(DrawerSurfaceMode.AUTOMATIC, preferences.drawerSurfaceMode)
        assertEquals(0xFF101416.toInt(), preferences.drawerSurfaceColor)
        assertTrue(preferences.showBuiltInClock)
        assertFalse(preferences.showScreenTime)
        assertFalse(preferences.showDetailedUsage)
        assertTrue(preferences.autoShowKeyboard)
        assertFalse(preferences.reverseAppListWithKeyboard)
        assertTrue(preferences.showFilterBar)
        assertTrue(preferences.showDrawerGradient)
        assertTrue(preferences.showSearchUnderline)
        assertEquals(24, preferences.appListTopMarginDp)
        assertEquals(20, preferences.appListRightMarginDp)
        assertEquals(20, preferences.searchLeftMarginDp)
        assertEquals(WeatherTemperatureUnit.SYSTEM, preferences.weatherTemperatureUnit)
        assertEquals(ClockFormat.SYSTEM, preferences.clockFormat)
        assertEquals(HomeElementPosition.DEFAULT, preferences.favoritesPosition)
        assertFalse(preferences.hideStatusBar)
        preferences.fontScalePercent = 300
        preferences.appListTopMarginDp = 300
        preferences.appListRightMarginDp = -3
        preferences.searchLeftMarginDp = 90
        assertEquals(150, preferences.fontScalePercent)
        assertEquals(96, preferences.appListTopMarginDp)
        assertEquals(0, preferences.appListRightMarginDp)
        assertEquals(64, preferences.searchLeftMarginDp)
    }

    @Test fun `unknown font values fall back to Geist Mono`() {
        val backend = MapBackend().apply { putString("customization.launcher_font", "NOT_A_FONT") }
        assertEquals(LauncherFont.GEIST_MONO, LauncherPreferences(backend).launcherFont)
    }

    @Test fun `unknown presentation modes fall back safely`() {
        val backend = MapBackend().apply {
            putString("customization.text_transform", "NOT_A_TRANSFORM")
            putString("customization.drawer_surface_mode", "NOT_A_SURFACE")
        }
        val preferences = LauncherPreferences(backend)
        assertEquals(LauncherTextTransform.LOWERCASE, preferences.textTransform)
        assertEquals(DrawerSurfaceMode.AUTOMATIC, preferences.drawerSurfaceMode)
    }

    @Test fun `every font choice persists by stable enum name`() {
        LauncherFont.entries.forEach { font ->
            val backend = MapBackend()
            LauncherPreferences(backend).launcherFont = font
            assertEquals(font, LauncherPreferences(backend).launcherFont)
        }
    }

    @Test fun `app customization updates and resets individual entries`() {
        val preferences = LauncherPreferences(MapBackend())
        preferences.setAppHidden("personal:camera", true)
        preferences.setAppHidden("work:camera", true)
        preferences.setAppAlias("personal:camera", "  Pocket camera  ")
        assertEquals(setOf("personal:camera", "work:camera"), preferences.hiddenApps)
        assertEquals("Pocket camera", preferences.appAliases["personal:camera"])

        preferences.setAppHidden("personal:camera", false)
        preferences.setAppAlias("personal:camera", "")
        assertEquals(setOf("work:camera"), preferences.hiddenApps)
        assertFalse("personal:camera" in preferences.appAliases)
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
