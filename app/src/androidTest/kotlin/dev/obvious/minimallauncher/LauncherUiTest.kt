package dev.obvious.minimallauncher

import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
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
        context.getSharedPreferences(USER_PREFERENCES, 0).edit().clear().commit()
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

        waitForDescription("Minimal Launcher Home")
        openSettings()
        waitForText("Choose how your Home screen and app drawer look and behave.")
        listOf("Home screen", "App drawer", "Appearance", "System", "About").forEach(::waitForText)
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
        val input = waitForClass("android.widget.EditText")
        input.text = "#5A2148"
        waitForText("SAVE").click()
        waitForDescriptionContains("#5A2148")
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
        waitForDescription("Minimal Launcher Home")
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
        waitForDescription("Minimal Launcher Home")
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

    @Test fun screenTimeWidgetCanBeEnabledAndHidden() {
        device.executeShellCommand("appops set $PACKAGE_NAME GET_USAGE_STATS allow")
        openSettingsCategory("Home screen")
        waitForText("Show screen time").click()
        waitForDescriptionContains("Usage access")
        closeSettingsCategoryAndRoot()

        val widget = waitForResource("home_screen_time")
        assertTrue(eventually { widget.text?.toString()?.startsWith("screen on ·") == true })
        assertTrue(widget.contentDescription.toString().startsWith("Screen on today,"))
        val center = widget.visibleCenter
        device.swipe(center.x, center.y, center.x, center.y, 160)
        waitForText("built-in screen time")
        waitForText("hide").click()
        assertTrue(eventually { device.findObject(By.res(PACKAGE_NAME, "home_screen_time")) == null })
        waitForDescription("Minimal Launcher Home")
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
        waitForDescription("Minimal Launcher Home")
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
        waitForDescription("Minimal Launcher Home")
    }

    private fun openDrawer() {
        waitForDescription("Minimal Launcher Home")
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

    private fun waitForResource(name: String): UiObject2 =
        device.wait(Until.findObject(By.res(PACKAGE_NAME, name)), TIMEOUT_MS)
            ?: throw AssertionError("Timed out waiting for resource: $name")

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
        const val ARTIFACTS_DIRECTORY = "/sdcard/Download/minimal-launcher-ui-test-artifacts"
        const val USER_PREFERENCES = "launcher_prefs"
        const val TIMEOUT_MS = 10_000L
    }
}
