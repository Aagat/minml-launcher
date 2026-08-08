package dev.obvious.minimallauncher

import dev.obvious.minimallauncher.appearance.Appearance
import dev.obvious.minimallauncher.appearance.LauncherColorPalette
import dev.obvious.minimallauncher.appearance.LauncherTextTransform
import dev.obvious.minimallauncher.drawer.DrawerSurfaceMode
import dev.obvious.minimallauncher.drawer.DrawerSurfacePolicy
import dev.obvious.minimallauncher.home.HomeElementPosition
import dev.obvious.minimallauncher.preferences.LauncherPreferences
import dev.obvious.minimallauncher.preferences.SharedPreferenceBackend
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LauncherUiTest {
    @get:Rule val testName = TestName()

    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        device.executeShellCommand("cmd role add-role-holder android.app.role.HOME $PACKAGE_NAME")
        device.executeShellCommand("appops set $PACKAGE_NAME GET_USAGE_STATS default")
        context.getSharedPreferences(USER_PREFERENCES, 0).edit().clear().commit()
        context.getSharedPreferences(RUNTIME_PREFERENCES, 0).edit().clear().commit()
        LauncherPreferences(SharedPreferenceBackend(context.getSharedPreferences(USER_PREFERENCES, 0))).apply {
            animationsEnabled = false
            autoShowKeyboard = false
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForPackage()
    }

    @After fun tearDown() {
        runCatching {
            device.executeShellCommand("mkdir -p $ARTIFACTS_DIRECTORY")
            device.executeShellCommand(
                "screencap -p $ARTIFACTS_DIRECTORY/${testName.methodName}.png",
            )
        }
        runCatching { device.setOrientationNatural() }
        runCatching { scenario.close() }
    }

    @Test fun homeRegistrationAndSettingsStructure() {
        scenario.onActivity { activity ->
            val matches = activity.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0,
            )
            assertTrue(matches.any { it.activityInfo.packageName == PACKAGE_NAME })
            val roleManager = activity.getSystemService(RoleManager::class.java)
            assertTrue(roleManager.isRoleAvailable(RoleManager.ROLE_HOME))
            assertTrue(roleManager.isRoleHeld(RoleManager.ROLE_HOME))
        }

        waitForDescription("minml launcher Home")
        openSettings()
        waitForText("Choose how your Home screen and app drawer look and behave.")
        listOf("Home screen", "App drawer", "Appearance", "System", "About").forEach(::waitForText)
        waitForText("About").click()
        waitForText("GPL-3.0")
        waitForText("github.com/Aagat/minml-launcher")
    }

    @Test fun textCapitalizationPersistsAcrossRecreation() {
        openSettingsCategory("Appearance")
        waitForText("Text capitalization").click()
        waitForText("Original capitalization").click()
        waitForDescriptionContains("Original capitalization")
        closeSettingsCategoryAndRoot()

        waitForText(Pattern.compile("[A-Z][a-z]{2},.*"))
        openDrawer()
        waitForText(Pattern.compile("All/\\d+"))
        assertEquals("Search", waitForResource("drawer_search").text)

        device.pressBack()
        openSettingsCategory("Appearance")
        waitForText("Text capitalization").click()
        waitForText("Uppercase").click()
        waitForDescriptionContains("Uppercase")
        closeSettingsCategoryAndRoot()
        openDrawer()
        waitForText(Pattern.compile("ALL/\\d+"))
        assertEquals("SEARCH", waitForResource("drawer_search").text)

        scenario.recreate()
        waitForPackage()
        waitForText(Pattern.compile("ALL/\\d+"))
        scenario.onActivity { activity ->
            assertEquals(
                LauncherTextTransform.UPPERCASE,
                preferences(activity).textTransform,
            )
        }
    }

    @Test fun drawerBackdropModesApplyExactRendering() {
        setPreferences { drawerSurfaceMode = DrawerSurfaceMode.TRANSPARENT }
        assertDrawerSurface(DrawerSurfacePolicy.TRANSPARENT_COLOR, expectTransparentFade = true)

        val customColor = Color.rgb(0x5A, 0x21, 0x48)
        setPreferences {
            drawerSurfaceMode = DrawerSurfaceMode.CUSTOM
            drawerSurfaceColor = customColor
        }
        assertDrawerSurface(customColor, expectTransparentFade = false)

        setPreferences { drawerSurfaceMode = DrawerSurfaceMode.WALLPAPER }
        scenario.onActivity { activity ->
            val wallpaper = WallpaperManager.getInstance(activity)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
                ?: DrawerSurfacePolicy.DARK_COLOR
            assertSurfaceView(activity, wallpaper, expectTransparentFade = false)
        }

        openSettingsCategory("App drawer")
        waitForText("Search backdrop").click()
        waitForText("Custom · use the selected backdrop color").click()
        val colorRow = waitForDescriptionContains("Current custom search backdrop")
        assertTrue(colorRow.isEnabled)
        colorRow.click()
        listOf("color_preset_1", "color_preset_2", "color_preset_3", "color_preset_4", "color_preset_5")
            .forEach(::waitForResource)
        waitForResource("color_preset_5").click()
        waitForDescriptionContains("#F2E8D5")
        scenario.onActivity { activity ->
            assertEquals(0xFFF2E8D5.toInt(), preferences(activity).drawerSurfaceColor)
            assertEquals(LauncherColorPalette.DARK_FONT, preferences(activity).fontColor)
        }

        waitForDescriptionContains("Current custom search backdrop").click()
        waitForText("color picker…").click()
        val picker = waitForResource("visual_color_picker")
        waitForResource("color_hue_slider")
        waitForResource("color_saturation_slider")
        waitForResource("color_brightness_slider")
        val previewBefore = waitForResource("color_picker_preview").text
        picker.visibleBounds.let { bounds ->
            device.click(bounds.left + bounds.width() * 3 / 4, bounds.top + bounds.height() / 3)
        }
        assertTrue(eventually { waitForResource("color_picker_preview").text != previewBefore })
        waitForText(Pattern.compile("(?i)hex code")).click()
        val input = waitForClass("android.widget.EditText")
        input.text = "#5A2148"
        waitForText("SAVE").click()
        waitForDescriptionContains("#5A2148")
        scenario.onActivity { activity ->
            assertEquals(0xFF5A2148.toInt(), preferences(activity).drawerSurfaceColor)
            assertEquals(LauncherColorPalette.LIGHT_FONT, preferences(activity).fontColor)
        }
    }

    @Test fun everyColorSettingUsesPresetFirstFlowAndBackgroundPairing() {
        openSettingsCategory("Appearance")

        waitForText("Font color").click()
        waitForResource("color_preset_1")
        waitForResource("color_preset_5")
        device.pressBack()

        waitForText("Accent color").click()
        waitForResource("color_preset_2").click()
        waitForDescriptionContains("#62D9FF")

        waitForText("Background mode").click()
        waitForText("Solid · selected background color").click()
        waitForDescriptionContains("Current opaque background").click()
        waitForResource("color_preset_5").click()
        waitForDescriptionContains("#F2E8D5")
        scenario.onActivity { activity ->
            assertEquals(Appearance.SOLID, preferences(activity).appearance)
            assertEquals(0xFFF2E8D5.toInt(), preferences(activity).solidBackgroundColor)
            assertEquals(LauncherColorPalette.DARK_FONT, preferences(activity).fontColor)
            assertEquals(0xFF62D9FF.toInt(), preferences(activity).accentColor)
        }
    }

    @Test fun drawerGestureSearchFilterAndLaunchFlow() {
        setPreferences { autoShowKeyboard = true }
        openDrawer()
        val search = waitForResource("drawer_search")
        assertTrue(search.isFocused)
        assertTrue(eventually { isImeVisible() })
        search.text = "clc"
        waitForDescription("Open Clock")
        device.pressEnter()
        assertTrue(eventually { device.currentPackageName != PACKAGE_NAME })

        device.pressHome()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForPackage()
        waitForDescription("minml launcher Home")
        openDrawer()
        val listY = device.displayHeight / 3
        device.swipe(device.displayWidth - 160, listY, device.displayWidth / 4, listY, 24)
        waitForText(Pattern.compile("daily/\\d+"))
        device.swipe(device.displayWidth - 180, listY + 500, device.displayWidth - 180, listY, 30)
        waitForText(Pattern.compile("daily/\\d+"))

        device.swipe(device.displayWidth / 2, 180, device.displayWidth / 2, 900, 20)
        assertTrue(eventually { !isImeVisible() })
        assertNotNull(device.findObject(By.res(PACKAGE_NAME, "drawer_search")))
        device.swipe(device.displayWidth / 2, 180, device.displayWidth / 2, 1_100, 6)
        waitForDescription("minml launcher Home")
    }

    @Test fun rotationKeepsDrawerUsable() {
        openDrawer()
        device.setOrientationLeft()
        assertTrue(eventually { device.displayWidth > device.displayHeight })
        waitForResource("drawer_search")
        waitForResource("drawer_app_list")
        device.setOrientationNatural()
        assertTrue(eventually { device.displayHeight > device.displayWidth })
        waitForResource("drawer_search")
    }

    @Test fun builtInClockCanBeArrangedAndRestored() {
        val clock = waitForResource("home_clock")
        val originalBounds = clock.visibleBounds
        clock.visibleCenter.let { center -> device.swipe(center.x, center.y, center.x, center.y, 160) }
        waitForResource("widget_editor_done")
        assertTrue(
            device.findObject(By.text(Pattern.compile("\\d+,\\d+ · \\d+×\\d+ dp"))) == null,
        )

        waitForResource("widget_editor_resize").visibleCenter.let { handle ->
            device.swipe(handle.x, handle.y, handle.x - 180, handle.y + 40, 24)
        }
        waitForResource("widget_editor_move").visibleCenter.let { surface ->
            device.swipe(surface.x, surface.y, surface.x + 100, surface.y + 100, 24)
        }
        val arrangedBounds = waitForResource("home_clock").visibleBounds
        assertTrue(arrangedBounds.width() < originalBounds.width() - 100)
        assertTrue(arrangedBounds.left > originalBounds.left + 50)
        assertTrue(arrangedBounds.top > originalBounds.top + 50)
        waitForResource("widget_editor_done").click()

        scenario.recreate()
        waitForPackage()
        val restoredBounds = waitForResource("home_clock").visibleBounds
        assertTrue(abs(restoredBounds.left - arrangedBounds.left) <= 4)
        assertTrue(abs(restoredBounds.top - arrangedBounds.top) <= 4)
        assertTrue(abs(restoredBounds.width() - arrangedBounds.width()) <= 4)
        assertTrue(abs(restoredBounds.height() - arrangedBounds.height()) <= 4)
    }

    @Test fun favoritesBlockCanBeMovedRestoredAndReset() {
        assertTrue(eventually { viewBounds(R.id.home_favorites)?.height()?.let { it > 0 } == true })
        val originalBounds = requireNotNull(viewBounds(R.id.home_favorites))

        openSettingsCategory("Home screen")
        waitForText("Favorite position").click()
        val moveSurface = waitForResource("widget_editor_move")
        assertTrue(device.findObject(By.res(PACKAGE_NAME, "widget_editor_resize")) == null)
        waitForText("reset")
        moveSurface.visibleCenter.let { center ->
            device.swipe(center.x, center.y, center.x - 180, center.y - 240, 24)
        }
        val arrangedBounds = requireNotNull(viewBounds(R.id.home_favorites))
        assertTrue(arrangedBounds.left < originalBounds.left - 100)
        assertTrue(arrangedBounds.top < originalBounds.top - 100)
        waitForResource("widget_editor_done").click()

        scenario.recreate()
        waitForPackage()
        assertTrue(eventually {
            viewBounds(R.id.home_favorites)?.let { restored ->
                abs(restored.left - arrangedBounds.left) <= 4 && abs(restored.top - arrangedBounds.top) <= 4
            } == true
        })

        device.setOrientationLeft()
        assertTrue(eventually { device.displayWidth > device.displayHeight })
        val landscapeBounds = requireNotNull(viewBounds(R.id.home_favorites))
        assertTrue(landscapeBounds.left >= 0)
        assertTrue(landscapeBounds.top >= 0)
        assertTrue(landscapeBounds.right <= device.displayWidth)
        assertTrue(landscapeBounds.bottom <= device.displayHeight)
        device.setOrientationNatural()
        assertTrue(eventually { device.displayHeight > device.displayWidth })
        assertTrue(eventually {
            viewBounds(R.id.home_favorites)?.let { restored ->
                abs(restored.left - arrangedBounds.left) <= 4 && abs(restored.top - arrangedBounds.top) <= 4
            } == true
        })

        openSettingsCategory("Home screen")
        waitForText("Favorite position").click()
        waitForText("reset").click()
        waitForResource("widget_editor_done").click()
        assertTrue(eventually {
            viewBounds(R.id.home_favorites)?.let { reset ->
                abs(reset.left - originalBounds.left) <= 4 && abs(reset.top - originalBounds.top) <= 4
            } == true
        })
        scenario.onActivity { activity ->
            assertEquals(HomeElementPosition.DEFAULT, preferences(activity).favoritesPosition)
        }
    }

    @Test fun screenTimeWidgetCanBeEnabledAndHidden() {
        openSettingsCategory("Home screen")
        waitForText("Show screen time").click()
        waitForText("1. Allow restricted settings")
        waitForText("2. Permit usage access")
        assertFalse(scrollToDescriptionContains("Detailed usage").isEnabled)
        closeSettingsCategoryAndRoot()

        waitForResource("home_screen_time").click()
        waitForText("1. Allow restricted settings")
        waitForText("2. Permit usage access")
        closeSettingsCategoryAndRoot()

        device.executeShellCommand("appops set $PACKAGE_NAME GET_USAGE_STATS allow")
        scenario.recreate()
        waitForPackage()
        openSettingsCategory("Home screen")
        val detailedUsage = scrollToDescriptionContains("Detailed usage")
        assertTrue(detailedUsage.isEnabled)
        detailedUsage.click()
        closeSettingsCategoryAndRoot()
        val widget = waitForResource("home_screen_time")
        assertTrue(eventually { widget.text?.toString()?.startsWith("screen on ·") == true })
        assertTrue(widget.contentDescription.toString().startsWith("Screen on today,"))
        assertTrue(waitForResource("home_detailed_usage").text?.isNotBlank() == true)
        val center = widget.visibleCenter
        device.swipe(center.x, center.y, center.x, center.y, 160)
        waitForResource("widget_editor_done")
        val originalBounds = waitForResource("home_screen_time").visibleBounds
        waitForResource("widget_editor_resize").visibleCenter.let { handle ->
            device.swipe(handle.x, handle.y, handle.x - 180, handle.y + 40, 24)
        }
        val resizedBounds = waitForResource("home_screen_time").visibleBounds
        assertTrue(resizedBounds.width() < originalBounds.width() - 100)
        waitForResource("widget_editor_move").visibleCenter.let { surface ->
            device.swipe(surface.x, surface.y, surface.x + 100, surface.y + 100, 24)
        }
        val arrangedBounds = waitForResource("home_screen_time").visibleBounds
        assertTrue(arrangedBounds.left > originalBounds.left + 50)
        assertTrue(arrangedBounds.top > originalBounds.top + 50)
        waitForResource("widget_editor_done").click()
        assertTrue(eventually { device.findObject(By.res(PACKAGE_NAME, "widget_editor_done")) == null })

        scenario.recreate()
        waitForPackage()
        val restoredBounds = waitForResource("home_screen_time").visibleBounds
        assertTrue(abs(restoredBounds.left - arrangedBounds.left) <= 4)
        assertTrue(abs(restoredBounds.top - arrangedBounds.top) <= 4)
        assertTrue(abs(restoredBounds.width() - arrangedBounds.width()) <= 4)

        val restoredCenter = waitForResource("home_screen_time").visibleCenter
        device.swipe(restoredCenter.x, restoredCenter.y, restoredCenter.x, restoredCenter.y, 160)
        waitForResource("widget_editor_done")
        waitForResource("widget_editor_remove").click()
        waitForText("remove built-in screen time?")
        waitForAndroidResource("button1").click()
        assertTrue(eventually { device.findObject(By.res(PACKAGE_NAME, "home_screen_time")) == null })
        waitForDescription("minml launcher Home")
        scenario.onActivity { activity -> assertFalse(preferences(activity).showScreenTime) }
    }

    private fun setPreferences(block: LauncherPreferences.() -> Unit) {
        scenario.onActivity { activity -> preferences(activity).apply(block) }
        scenario.recreate()
        waitForPackage()
    }

    private fun assertDrawerSurface(expectedColor: Int, expectTransparentFade: Boolean) {
        scenario.onActivity { activity -> assertSurfaceView(activity, expectedColor, expectTransparentFade) }
    }

    private fun assertSurfaceView(activity: MainActivity, expectedColor: Int, expectTransparentFade: Boolean) {
        val surface = activity.findViewById<View>(R.id.drawer_bottom_surface)
        assertEquals(expectedColor, (surface.background as ColorDrawable).color)
        val fadeColors = (activity.findViewById<View>(R.id.drawer_fade).background as GradientDrawable).colors
        assertNotNull(fadeColors)
        if (expectTransparentFade) assertTrue(fadeColors!!.all { Color.alpha(it) == 0 })
        else assertEquals(expectedColor, fadeColors!!.last())
    }

    private fun openSettings() {
        waitForDescription("minml launcher Home")
        val x = (device.displayWidth / 10).coerceAtLeast(80)
        val y = device.displayHeight / 2
        device.swipe(x, y, x, y, 160)
        waitForText("launcher settings")
    }

    private fun openSettingsCategory(name: String) {
        openSettings()
        waitForText(name).click()
        waitForDescription("Back to launcher settings")
    }

    private fun closeSettingsCategoryAndRoot() {
        waitForDescription("Back to launcher settings").click()
        waitForDescription("Close settings").click()
        waitForDescription("minml launcher Home")
    }

    private fun openDrawer() {
        waitForDescription("minml launcher Home")
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight - 300,
            device.displayWidth / 2,
            device.displayHeight / 2,
            24,
        )
        waitForResource("drawer_search")
        waitForText(Pattern.compile("(?:all|All|ALL)/\\d+"))
    }

    private fun isImeVisible(): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        return visible
    }

    private fun preferences(activity: MainActivity) = LauncherPreferences(
        SharedPreferenceBackend(activity.getSharedPreferences(USER_PREFERENCES, 0)),
    )

    private fun viewBounds(id: Int): Rect? {
        var bounds: Rect? = null
        scenario.onActivity { activity ->
            val view = activity.findViewById<View>(id) ?: return@onActivity
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        return bounds
    }

    private fun waitForPackage() {
        assertTrue(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), TIMEOUT_MS))
        device.waitForIdle()
    }

    private fun waitForText(text: String): UiObject2 = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)
        ?: throw AssertionError("Timed out waiting for text: $text")

    private fun waitForText(pattern: Pattern): UiObject2 = device.wait(Until.findObject(By.text(pattern)), TIMEOUT_MS)
        ?: throw AssertionError("Timed out waiting for text matching: $pattern")

    private fun waitForDescription(description: String): UiObject2 =
        device.wait(Until.findObject(By.desc(description)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for description: $description")

    private fun waitForDescriptionContains(description: String): UiObject2 =
        device.wait(Until.findObject(By.descContains(description)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for description containing: $description")

    private fun scrollToDescriptionContains(description: String): UiObject2 {
        repeat(5) {
            device.findObject(By.descContains(description))?.let { return it }
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight - 240,
                device.displayWidth / 2,
                device.displayHeight / 3,
                24,
            )
            device.waitForIdle()
        }
        return waitForDescriptionContains(description)
    }

    private fun waitForResource(name: String): UiObject2 =
        device.wait(Until.findObject(By.res(PACKAGE_NAME, name)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for resource: $name")

    private fun waitForAndroidResource(name: String): UiObject2 =
        device.wait(Until.findObject(By.res("android", name)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for Android resource: $name")

    private fun waitForClass(name: String): UiObject2 =
        device.wait(Until.findObject(By.clazz(name)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for class: $name")

    private fun eventually(timeoutMillis: Long = TIMEOUT_MS, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private companion object {
        const val PACKAGE_NAME = "dev.obvious.minimallauncher"
        const val ARTIFACTS_DIRECTORY = "/sdcard/Download/minml-launcher-ui-test-artifacts"
        const val USER_PREFERENCES = "launcher_prefs"
        const val RUNTIME_PREFERENCES = "launcher_runtime"
        const val TIMEOUT_MS = 10_000L
    }
}
