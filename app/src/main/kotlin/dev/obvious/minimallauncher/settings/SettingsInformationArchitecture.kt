package dev.obvious.minimallauncher.settings

enum class SettingsPage {
    ROOT,
    HOME,
    DRAWER,
    APPEARANCE,
    SYSTEM,
    ABOUT,
}

data class SettingsCategory(
    val page: SettingsPage,
    val title: String,
    val summary: String,
)

object SettingsInformationArchitecture {
    val categories = listOf(
        SettingsCategory(SettingsPage.HOME, "Home screen", "Favorites, widgets, clock, screen time, and weather"),
        SettingsCategory(SettingsPage.DRAWER, "App drawer", "Filters, search, layout, and gestures"),
        SettingsCategory(SettingsPage.APPEARANCE, "Appearance", "Typography, colors, background, and status bar"),
        SettingsCategory(SettingsPage.SYSTEM, "System", "Default Home, permissions, and app details"),
        SettingsCategory(SettingsPage.ABOUT, "About", "Description, version, and application identity"),
    )

    fun parent(page: SettingsPage): SettingsPage? = if (page == SettingsPage.ROOT) null else SettingsPage.ROOT
}
