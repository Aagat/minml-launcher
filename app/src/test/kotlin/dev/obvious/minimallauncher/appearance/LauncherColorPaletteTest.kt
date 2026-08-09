package dev.obvious.minimallauncher.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherColorPaletteTest {
    @Test fun `every color setting offers exactly five presets`() {
        ColorSettingTarget.entries.forEach { target ->
            assertEquals(target.name, 5, LauncherColorPalette.presets(target).size)
        }
    }

    @Test fun `suggested backgrounds include readable font pairs`() {
        listOf(ColorSettingTarget.SOLID_BACKGROUND, ColorSettingTarget.DRAWER_BACKGROUND).forEach { target ->
            LauncherColorPalette.presets(target).forEach { preset ->
                val font = requireNotNull(preset.pairedFontColor)
                assertTrue(preset.name, LauncherColorPalette.contrastRatio(preset.color, font) >= 4.5)
            }
        }
    }

    @Test fun `custom backgrounds choose the higher contrast font`() {
        assertEquals(LauncherColorPalette.LIGHT_FONT, LauncherColorPalette.pairedFontColor(0xFF152033.toInt()))
        assertEquals(LauncherColorPalette.DARK_FONT, LauncherColorPalette.pairedFontColor(0xFFFFE269.toInt()))
    }

    @Test fun `hex parser accepts shorthand rgb and alpha input but stores opaque rgb`() {
        assertEquals(0xFFAABBCC.toInt(), LauncherColorPalette.parseHex("#abc"))
        assertEquals(0xFF102030.toInt(), LauncherColorPalette.parseHex("102030"))
        assertEquals(0xFF112233.toInt(), LauncherColorPalette.parseHex("80112233"))
        assertEquals("#102030", LauncherColorPalette.formatHex(0xFF102030.toInt()))
        assertNull(LauncherColorPalette.parseHex("#12"))
        assertNull(LauncherColorPalette.parseHex("#GGHHII"))
    }
}
