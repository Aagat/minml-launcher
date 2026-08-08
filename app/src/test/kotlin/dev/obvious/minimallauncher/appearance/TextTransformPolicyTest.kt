package dev.obvious.minimallauncher.appearance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TextTransformPolicyTest {
    private val locale = Locale.US

    @Test fun `launcher text can be lower original or upper case`() {
        val value = "My Camera"
        assertEquals("my camera", TextTransformPolicy.apply(value, LauncherTextTransform.LOWERCASE, locale))
        assertEquals(value, TextTransformPolicy.apply(value, LauncherTextTransform.ORIGINAL, locale))
        assertEquals("MY CAMERA", TextTransformPolicy.apply(value, LauncherTextTransform.UPPERCASE, locale))
    }

    @Test fun `transform uses the supplied display locale`() {
        val turkish = Locale.forLanguageTag("tr")
        assertEquals("indigo", TextTransformPolicy.apply("İNDİGO", LauncherTextTransform.LOWERCASE, turkish))
        assertEquals("İNDİGO", TextTransformPolicy.apply("indigo", LauncherTextTransform.UPPERCASE, turkish))
    }
}
