package dev.obvious.minimallauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsInformationArchitectureTest {
    @Test fun `settings exposes approved categories in stable order`() {
        assertEquals(
            listOf(
                SettingsPage.HOME,
                SettingsPage.DRAWER,
                SettingsPage.APPEARANCE,
                SettingsPage.SYSTEM,
                SettingsPage.ABOUT,
            ),
            SettingsInformationArchitecture.categories.map { it.page },
        )
    }

    @Test fun `category back navigation returns to root`() {
        SettingsInformationArchitecture.categories.forEach {
            assertEquals(SettingsPage.ROOT, SettingsInformationArchitecture.parent(it.page))
        }
        assertNull(SettingsInformationArchitecture.parent(SettingsPage.ROOT))
    }
}
