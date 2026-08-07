package dev.obvious.minimallauncher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class ScrimTone { NONE, DARK, LIGHT }
enum class ScrimStrength { LIGHT, STRONG }

data class AutoContrastDecision(val tone: ScrimTone, val strength: ScrimStrength)

object ContrastPolicy {
    fun decide(wallpaperColor: Int?, textColor: Int): AutoContrastDecision {
        if (wallpaperColor == null) return AutoContrastDecision(ScrimTone.DARK, ScrimStrength.STRONG)
        val wallpaperLuminance = luminance(wallpaperColor)
        val textLuminance = luminance(textColor)
        if (contrastRatio(wallpaperLuminance, textLuminance) >= MIN_TEXT_CONTRAST) {
            return AutoContrastDecision(ScrimTone.NONE, ScrimStrength.LIGHT)
        }
        return if (textLuminance >= LIGHT_TEXT_LUMINANCE) {
            AutoContrastDecision(ScrimTone.DARK, ScrimStrength.STRONG)
        } else {
            AutoContrastDecision(ScrimTone.LIGHT, ScrimStrength.STRONG)
        }
    }

    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        val red = channel(color shr 16 and 0xFF)
        val green = channel(color shr 8 and 0xFF)
        val blue = channel(color and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    fun localizedFallback(decision: AutoContrastDecision, textColor: Int): AutoContrastDecision =
        if (decision.tone != ScrimTone.NONE) decision
        else AutoContrastDecision(
            tone = if (luminance(textColor) >= LIGHT_TEXT_LUMINANCE) ScrimTone.DARK else ScrimTone.LIGHT,
            strength = ScrimStrength.STRONG,
        )

    private fun contrastRatio(first: Double, second: Double): Double =
        (max(first, second) + 0.05) / (min(first, second) + 0.05)

    private const val MIN_TEXT_CONTRAST = 4.5
    private const val LIGHT_TEXT_LUMINANCE = 0.45
}
