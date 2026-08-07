package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerSurfacePolicyTest {
    @Test fun `automatic surface preserves previous wallpaper and solid behavior`() {
        assertEquals(
            DrawerSurfacePolicy.DARK_COLOR,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.AUTOMATIC, Appearance.AUTO, SOLID, CUSTOM, WALLPAPER),
        )
        assertEquals(
            SOLID,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.AUTOMATIC, Appearance.SOLID, SOLID, CUSTOM, WALLPAPER),
        )
    }

    @Test fun `explicit surface modes resolve independently of Home appearance`() {
        assertEquals(
            DrawerSurfacePolicy.DARK_COLOR,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.DARK, Appearance.SOLID, SOLID, CUSTOM, WALLPAPER),
        )
        assertEquals(
            DrawerSurfacePolicy.TRANSPARENT_COLOR,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.TRANSPARENT, Appearance.AUTO, SOLID, CUSTOM, WALLPAPER),
        )
        assertEquals(
            WALLPAPER,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.WALLPAPER, Appearance.GRADIENT, SOLID, CUSTOM, WALLPAPER),
        )
        assertEquals(
            CUSTOM,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.CUSTOM, Appearance.TRANSPARENT, SOLID, CUSTOM, WALLPAPER),
        )
    }

    @Test fun `wallpaper mode has a safe dark fallback`() {
        assertEquals(
            DrawerSurfacePolicy.DARK_COLOR,
            DrawerSurfacePolicy.color(DrawerSurfaceMode.WALLPAPER, Appearance.AUTO, SOLID, CUSTOM, null),
        )
    }

    private companion object {
        const val SOLID = 0xFF112233.toInt()
        const val CUSTOM = 0xFF445566.toInt()
        const val WALLPAPER = 0xFF778899.toInt()
    }
}
