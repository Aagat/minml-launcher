package dev.obvious.minimallauncher

import java.util.Locale

enum class LauncherTextTransform(val displayName: String) {
    LOWERCASE("Lowercase"),
    ORIGINAL("Original capitalization"),
    UPPERCASE("Uppercase"),
}

object TextTransformPolicy {
    fun apply(
        value: String,
        transform: LauncherTextTransform,
        locale: Locale = Locale.getDefault(),
    ): String = when (transform) {
        LauncherTextTransform.LOWERCASE -> value.lowercase(locale)
        LauncherTextTransform.ORIGINAL -> value
        LauncherTextTransform.UPPERCASE -> value.uppercase(locale)
    }
}
