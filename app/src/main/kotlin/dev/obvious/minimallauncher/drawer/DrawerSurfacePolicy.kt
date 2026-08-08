package dev.obvious.minimallauncher.drawer

import dev.obvious.minimallauncher.appearance.Appearance

enum class DrawerSurfaceMode(val displayName: String) {
    AUTOMATIC("Automatic"),
    DARK("Dark"),
    TRANSPARENT("Transparent"),
    WALLPAPER("Wallpaper color"),
    CUSTOM("Custom"),
}

object DrawerSurfacePolicy {
    fun color(
        mode: DrawerSurfaceMode,
        appearance: Appearance,
        solidBackgroundColor: Int,
        customColor: Int,
        wallpaperColor: Int?,
    ): Int = when (mode) {
        DrawerSurfaceMode.AUTOMATIC -> if (appearance == Appearance.SOLID) solidBackgroundColor else DARK_COLOR
        DrawerSurfaceMode.DARK -> DARK_COLOR
        DrawerSurfaceMode.TRANSPARENT -> TRANSPARENT_COLOR
        DrawerSurfaceMode.WALLPAPER -> wallpaperColor ?: DARK_COLOR
        DrawerSurfaceMode.CUSTOM -> customColor
    }

    const val DARK_COLOR: Int = 0xFF000000.toInt()
    const val TRANSPARENT_COLOR: Int = 0x00000000
}
