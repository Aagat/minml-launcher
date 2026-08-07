package dev.obvious.minimallauncher

data class AppEntry(
    val stableId: String,
    val label: String,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val isWorkProfile: Boolean,
    val isMedia: Boolean,
)
