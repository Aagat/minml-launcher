package dev.obvious.minimallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastPolicyTest {
    @Test fun `light text on light wallpaper selects a dark scrim`() {
        assertEquals(
            AutoContrastDecision(ScrimTone.DARK, ScrimStrength.STRONG),
            ContrastPolicy.decide(0xFFF2F2F2.toInt(), 0xFFF4F4F2.toInt()),
        )
    }

    @Test fun `dark text on dark wallpaper selects a light scrim`() {
        assertEquals(
            AutoContrastDecision(ScrimTone.LIGHT, ScrimStrength.STRONG),
            ContrastPolicy.decide(0xFF111111.toInt(), 0xFF202020.toInt()),
        )
    }

    @Test fun `already contrasting colors avoid an unnecessary scrim`() {
        assertEquals(ScrimTone.NONE, ContrastPolicy.decide(0xFF080808.toInt(), 0xFFF4F4F2.toInt()).tone)
        assertEquals(ScrimTone.NONE, ContrastPolicy.decide(0xFFF8F8F8.toInt(), 0xFF101010.toInt()).tone)
    }

    @Test fun `relative luminance has stable black and white endpoints`() {
        assertEquals(0.0, ContrastPolicy.luminance(0xFF000000.toInt()), 0.0001)
        assertEquals(1.0, ContrastPolicy.luminance(0xFFFFFFFF.toInt()), 0.0001)
        assertTrue(ContrastPolicy.luminance(0xFF00FF00.toInt()) > ContrastPolicy.luminance(0xFF0000FF.toInt()))
    }
}
