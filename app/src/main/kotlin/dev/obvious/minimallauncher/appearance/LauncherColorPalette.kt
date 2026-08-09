package dev.obvious.minimallauncher.appearance

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class ColorSettingTarget(val title: String) {
    FONT("font color"),
    ACCENT("accent color"),
    SOLID_BACKGROUND("solid background color"),
    DRAWER_BACKGROUND("search backdrop color"),
}

data class LauncherColorPreset(
    val name: String,
    val color: Int,
    val pairedFontColor: Int? = null,
)

object LauncherColorPalette {
    const val LIGHT_FONT: Int = 0xFFF4F4F2.toInt()
    const val DARK_FONT: Int = 0xFF171816.toInt()

    fun presets(target: ColorSettingTarget): List<LauncherColorPreset> = when (target) {
        ColorSettingTarget.FONT -> listOf(
            LauncherColorPreset("Paper", LIGHT_FONT),
            LauncherColorPreset("White", 0xFFFFFFFF.toInt()),
            LauncherColorPreset("Warm ivory", 0xFFFFF0D8.toInt()),
            LauncherColorPreset("Mist blue", 0xFFDCE8FF.toInt()),
            LauncherColorPreset("Ink", DARK_FONT),
        )
        ColorSettingTarget.ACCENT -> listOf(
            LauncherColorPreset("Lime", 0xFFB7F36B.toInt()),
            LauncherColorPreset("Sky", 0xFF62D9FF.toInt()),
            LauncherColorPreset("Amber", 0xFFFFC857.toInt()),
            LauncherColorPreset("Coral", 0xFFFF7A68.toInt()),
            LauncherColorPreset("Violet", 0xFFB69CFF.toInt()),
        )
        ColorSettingTarget.SOLID_BACKGROUND,
        ColorSettingTarget.DRAWER_BACKGROUND,
        -> listOf(
            pairedPreset("Black", 0xFF000000.toInt()),
            pairedPreset("Charcoal", 0xFF101416.toInt()),
            pairedPreset("Deep navy", 0xFF101827.toInt()),
            pairedPreset("Forest", 0xFF12231A.toInt()),
            pairedPreset("Warm paper", 0xFFF2E8D5.toInt()),
        )
    }

    fun pairedFontColor(background: Int): Int {
        val opaque = background or 0xFF000000.toInt()
        val lightContrast = contrastRatio(opaque, LIGHT_FONT)
        val darkContrast = contrastRatio(opaque, DARK_FONT)
        return if (lightContrast >= darkContrast) LIGHT_FONT else DARK_FONT
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    fun parseHex(value: String): Int? {
        val normalized = value.trim().removePrefix("#")
        val expanded = when (normalized.length) {
            3 -> normalized.map { "$it$it" }.joinToString("")
            6 -> normalized
            8 -> normalized.takeLast(6)
            else -> return null
        }
        if (expanded.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return null
        return (expanded.toLongOrNull(16)?.toInt() ?: return null) or 0xFF000000.toInt()
    }

    fun formatHex(color: Int): String = "#" + (color.toLong() and 0xFFFFFFL)
        .toString(16)
        .uppercase()
        .padStart(6, '0')

    private fun pairedPreset(name: String, color: Int): LauncherColorPreset =
        LauncherColorPreset(name, color, pairedFontColor(color))

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color shr 16 and 0xFF) +
            0.7152 * channel(color shr 8 and 0xFF) +
            0.0722 * channel(color and 0xFF)
    }
}
