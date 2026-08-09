package dev.obvious.minimallauncher.appearance

enum class Appearance { AUTO, TRANSPARENT, GRADIENT, SOLID }

fun Appearance.toggleSolid(): Appearance = if (this == Appearance.SOLID) Appearance.AUTO else Appearance.SOLID

enum class LauncherFont(val displayName: String) {
    GEIST_MONO("Geist Mono"),
    GEIST("Geist"),
    INTER("Inter"),
    IBM_PLEX_SANS("IBM Plex Sans"),
    MANROPE("Manrope"),
    SPACE_GROTESK("Space Grotesk"),
    B612_SANS("B612"),
    B612_MONO("B612 Mono"),
    SYSTEM_MONO("Android Monospace"),
    SYSTEM_SANS("Android Sans"),
}
