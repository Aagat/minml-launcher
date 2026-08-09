package dev.obvious.minimallauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var contrastOverlay: View
    private lateinit var home: HomeGestureLayout
    private lateinit var drawer: FilterGestureLayout
    private lateinit var clockPanel: LinearLayout
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var weatherView: TextView
    private var screenTimeView: TextView? = null
    private var screenUsageView: TextView? = null
    private lateinit var homeRolePrompt: Button
    private lateinit var favoritesContainer: FrameLayout
    private lateinit var favoritesEditor: EditableWidgetFrame
    private lateinit var favoritesView: LinearLayout
    private lateinit var widgetContainer: FrameLayout
    private var clockWidgetEditor: EditableWidgetFrame? = null
    private var activeWidgetEditor: EditableWidgetFrame? = null
    private val widgetEditors = mutableListOf<EditableWidgetFrame>()
    private val widgetEditorGeometries = mutableMapOf<EditableWidgetFrame, WidgetGeometry>()
    private val widgetEditorAutomaticTops = mutableMapOf<EditableWidgetFrame, Int>()
    private lateinit var drawerHeader: TextView
    private lateinit var appList: ListView
    private lateinit var emptyState: TextView
    private lateinit var drawerFade: View
    private lateinit var drawerBottomSurface: View
    private lateinit var filtersView: LinearLayout
    private lateinit var filtersScroller: HorizontalScrollView
    private lateinit var searchFrame: FrameLayout
    private lateinit var searchUnderline: View
    private lateinit var searchInput: EditText
    private lateinit var scrollTrack: View
    private lateinit var scrollThumb: View
    private lateinit var settingsLayer: FrameLayout
    private lateinit var settingsScroll: ScrollView

    private lateinit var regularTypeface: Typeface
    private lateinit var mediumTypeface: Typeface
    private lateinit var preferences: LauncherPreferences
    private lateinit var runtimePreferences: android.content.SharedPreferences
    private lateinit var catalog: AppCatalog
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var screenTimeRepository: ScreenTimeRepository
    private lateinit var coarseLocationResolver: CoarseLocationResolver
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var wallpaperManager: WallpaperManager

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = AppListAdapter()
    private var allApps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var currentFilter = FilterSpec.builtIn(DrawerFilter.ALL)
    private var drawerOpen = false
    private var imeVisible = false
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var weatherRequestedAt = 0L
    private var locationRequestInFlight = false
    private var locationDeniedThisSession = false
    private var screenTimeRequestedAt = 0L
    private var screenTimeRequestGeneration = 0
    private var screenTimeRequestInFlight = false
    private var settingsPage: SettingsPage? = null
    private val settingsScrollPositions = mutableMapOf<SettingsPage, Int>()
    private var filterTransitionGeneration = 0

    private val wallpaperColorsChangedListener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, _: Int ->
        if (
            ::preferences.isInitialized &&
            (preferences.appearance == Appearance.AUTO || preferences.drawerSurfaceMode == DrawerSurfaceMode.WALLPAPER) &&
            ::contrastOverlay.isInitialized
        ) {
            applyAppearance()
        }
    }

    private val primaryColor: Int get() = preferences.fontColor
    private val secondaryColor: Int get() = withAlpha(preferences.fontColor, 0x88)
    private val wallpaperSecondaryColor: Int get() = withAlpha(preferences.fontColor, 0xDD)
    private val accentColor: Int get() = preferences.accentColor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        preferences = LauncherPreferences(
            SharedPreferenceBackend(getSharedPreferences(USER_PREFERENCES, MODE_PRIVATE)),
        )
        loadLauncherTypefaces()
        runtimePreferences = getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE)
        weatherRepository = WeatherRepository(runtimePreferences)
        screenTimeRepository = ScreenTimeRepository(this)
        coarseLocationResolver = CoarseLocationResolver(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APP_WIDGET_HOST_ID)
        wallpaperManager = WallpaperManager.getInstance(this)

        buildUi()
        setContentView(root)
        root.post(::applyStatusBarPreference)
        applyAppearance()
        wallpaperManager.addOnColorsChangedListener(wallpaperColorsChangedListener, handler)
        restoreCachedCatalog()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::handleBack)
        }

        val restoredFilter = savedInstanceState?.getString(STATE_FILTER).orEmpty()
        currentFilter = availableFilters().firstOrNull { it.id == restoredFilter }
            ?: runCatching { DrawerFilter.valueOf(restoredFilter) }.getOrNull()?.let(FilterSpec::builtIn)
            ?: FilterSpec.builtIn(DrawerFilter.ALL)
        val restoredDrawer = savedInstanceState?.getBoolean(STATE_DRAWER_OPEN, false) == true
        val restoredQuery = savedInstanceState?.getString(STATE_QUERY).orEmpty()
        val restoredSettingsPage = savedInstanceState?.getString(STATE_SETTINGS_PAGE)
            ?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
        if (restoredSettingsPage != null) showSettings(restoredSettingsPage)
        else if (restoredDrawer) openDrawer(seed = restoredQuery)

        catalog = AppCatalog(this, ::onCatalogChanged)
        catalog.start()
        if (intent.action == Intent.ACTION_SEARCH) {
            openDrawer(seed = intent.getStringExtra(SearchManager.QUERY).orEmpty())
        }
        updateClock()
        handler.post(clockTicker)
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
        renderWidgets()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarPreference()
        if (::homeRolePrompt.isInitialized) updateHomeRolePrompt()
        updateScreenTime(force = true)
        if (settingsPage != null) renderSettingsPage()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEARCH) {
            openDrawer(seed = intent.getStringExtra(SearchManager.QUERY).orEmpty())
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (settingsPage != null) closeSettings()
            if (drawerOpen) closeDrawer()
        }
    }

    override fun onStop() {
        runCatching { appWidgetHost.stopListening() }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        catalog.stop()
        weatherRepository.close()
        screenTimeRepository.close()
        coarseLocationResolver.cancel()
        runCatching { wallpaperManager.removeOnColorsChangedListener(wallpaperColorsChangedListener) }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DRAWER_OPEN, drawerOpen)
        outState.putString(STATE_FILTER, currentFilter.id)
        outState.putString(STATE_QUERY, searchInput.text?.toString().orEmpty())
        outState.putString(STATE_SETTINGS_PAGE, settingsPage?.name)
        super.onSaveInstanceState(outState)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            id = R.id.launcher_root
            setBackgroundColor(Color.TRANSPARENT)
            isFocusableInTouchMode = true
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) adaptHomeForWindow()
            }
        }
        contrastOverlay = View(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(contrastOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

        buildHome()
        buildDrawer()
        root.addView(home, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))
        drawer.visibility = View.GONE
        buildSettingsLayer()
        root.addView(settingsLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        settingsLayer.visibility = View.GONE

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) {
                imeVisible = insets.isVisible(WindowInsets.Type.ime())
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            } else {
                @Suppress("DEPRECATION")
                run { imeVisible = insets.systemWindowInsetBottom > dp(100) }
                android.graphics.Insets.of(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            home.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            settingsLayer.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            val drawerBottom = if (android.os.Build.VERSION.SDK_INT >= 30 && imeVisible) {
                max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom)
            } else {
                bars.bottom
            }
            drawer.setPadding(bars.left, bars.top, bars.right, drawerBottom)
            drawer.post(::positionDrawerChildren)
            insets
        }
    }

    private fun buildSettingsLayer() {
        settingsLayer = FrameLayout(this).apply {
            id = R.id.launcher_settings
            setBackgroundColor(SETTINGS_BACKGROUND_COLOR)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Launcher settings"
        }
    }

    private fun showSettings(page: SettingsPage = SettingsPage.ROOT) {
        settingsPage?.let { current ->
            if (::settingsScroll.isInitialized) settingsScrollPositions[current] = settingsScroll.scrollY
        }
        if (drawerOpen) closeDrawer()
        settingsPage = page
        settingsLayer.visibility = View.VISIBLE
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        drawer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        renderSettingsPage(preserveCurrentScroll = false)
        settingsLayer.requestFocus()
    }

    private fun closeSettings() {
        settingsPage?.let { current ->
            if (::settingsScroll.isInitialized) settingsScrollPositions[current] = settingsScroll.scrollY
        }
        settingsPage = null
        settingsLayer.visibility = View.GONE
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        drawer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        home.requestFocus()
    }

    private fun renderSettingsPage(preserveCurrentScroll: Boolean = true) {
        val page = settingsPage ?: return
        if (preserveCurrentScroll && ::settingsScroll.isInitialized) {
            settingsScrollPositions[page] = settingsScroll.scrollY
        }
        settingsLayer.removeAllViews()

        val pageColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SETTINGS_BACKGROUND_COLOR)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(20), 0)
        }
        header.addView(settingsText(if (page == SettingsPage.ROOT) "×" else "‹", 28f, SETTINGS_PRIMARY_COLOR, mediumTypeface).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minWidth = dp(56)
            contentDescription = if (page == SettingsPage.ROOT) "Close settings" else "Back to launcher settings"
            setOnClickListener { handleSettingsBack() }
        }, LinearLayout.LayoutParams(dp(56), dp(64)))
        header.addView(settingsText(settingsPageTitle(page), 18f, SETTINGS_PRIMARY_COLOR, mediumTypeface).apply {
            gravity = Gravity.CENTER_VERTICAL
            isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, dp(64), 1f))
        pageColumn.addView(header, LinearLayout.LayoutParams(MATCH, dp(64)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        settingsScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        pageColumn.addView(settingsScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        settingsLayer.addView(pageColumn, FrameLayout.LayoutParams(MATCH, MATCH))

        when (page) {
            SettingsPage.ROOT -> renderSettingsRoot(body)
            SettingsPage.HOME -> renderHomeSettings(body)
            SettingsPage.DRAWER -> renderDrawerSettings(body)
            SettingsPage.APPEARANCE -> renderAppearanceSettings(body)
            SettingsPage.SYSTEM -> renderSystemSettings(body)
            SettingsPage.ABOUT -> renderAboutSettings(body)
        }
        settingsScroll.post { settingsScroll.scrollTo(0, settingsScrollPositions[page] ?: 0) }
    }

    private fun handleSettingsBack() {
        val page = settingsPage ?: return
        val parent = SettingsInformationArchitecture.parent(page)
        if (parent == null) closeSettings() else showSettings(parent)
    }

    private fun settingsPageTitle(page: SettingsPage): String = when (page) {
        SettingsPage.ROOT -> "launcher settings"
        SettingsPage.HOME -> "home screen"
        SettingsPage.DRAWER -> "app drawer"
        SettingsPage.APPEARANCE -> "appearance"
        SettingsPage.SYSTEM -> "system"
        SettingsPage.ABOUT -> "about"
    }

    private fun renderSettingsRoot(body: LinearLayout) {
        body.addView(settingsText(
            "Choose how your Home screen and app drawer look and behave.",
            11f,
            SETTINGS_SECONDARY_COLOR,
            regularTypeface,
        ).apply { setPadding(dp(12), 0, dp(12), dp(16)) })
        SettingsInformationArchitecture.categories.forEach { category ->
            addSettingsRow(body, category.title, category.summary, "›") { showSettings(category.page) }
        }
    }

    private fun renderHomeSettings(body: LinearLayout) {
        addSettingsSection(body, "favorites and widgets")
        addSettingsRow(body, "Favorite apps", "Choose the right-aligned Home shortcuts", "${preferences.favorites.size}") {
            showFavoriteEditor()
        }
        if (preferences.favorites.isNotEmpty()) {
            addSettingsRow(
                body,
                "Favorite position",
                "Move the entire favorites block directly on Home",
                "arrange",
            ) {
                closeSettings()
                favoritesEditor.takeIf { it.visibility == View.VISIBLE }?.let(::enterWidgetEditMode)
            }
        }
        addSettingsRow(body, "Add widget", "Open Android's widget picker", "system") { pickWidget() }
        if (preferences.showBuiltInClock || preferences.showScreenTime || loadWidgetPlacements().isNotEmpty()) {
            addSettingsRow(
                body,
                "Arrange widgets",
                "Move and resize widgets directly on your Home screen",
                "open",
            ) {
                closeSettings()
                widgetEditors.firstOrNull()?.let(::enterWidgetEditMode)
            }
        }

        addSettingsSection(body, "clock and date")
        addSettingsRow(body, "Show clock/date", "Built-in launcher clock treatment", onOff(preferences.showBuiltInClock)) {
            preferences.showBuiltInClock = !preferences.showBuiltInClock
            renderWidgets()
            applyAppearance()
            renderSettingsPage()
        }
        addSettingsRow(body, "Clock format", "Follow Android or override the format", clockFormatLabel()) {
            showClockFormatEditor()
        }

        addSettingsSection(body, "screen time")
        addSettingsRow(
            body,
            "Show screen time",
            "Today's screen-on duration as a built-in Home widget",
            onOff(preferences.showScreenTime),
        ) {
            preferences.showScreenTime = !preferences.showScreenTime
            renderWidgets()
            renderSettingsPage()
        }
        if (preferences.showScreenTime) {
            val usageAccessGranted = screenTimeRepository.hasUsageAccess()
            if (usageAccessGranted) {
                addSettingsRow(
                    body,
                    "Usage access",
                    "Android usage events are read only to total today's screen-on time",
                    "allowed",
                ) { openUsageAccessSettings() }
            } else {
                body.addView(settingsText(
                    "Android requires special Usage Access. If Android blocks the switch because this APK was installed from a file, complete both steps below.",
                    10f,
                    SETTINGS_SECONDARY_COLOR,
                    regularTypeface,
                ).apply {
                    setPadding(dp(12), dp(4), dp(12), dp(8))
                    setLineSpacing(0f, 1.15f)
                })
                addSettingsRow(
                    body,
                    "1. Allow restricted settings",
                    "Open App info, tap the top-right menu, then Allow restricted settings",
                    "app info",
                ) { openAppDetails() }
                addSettingsRow(
                    body,
                    "2. Permit usage access",
                    "Open Android Usage Access, select minml launcher, and enable the switch",
                    "usage access",
                ) { openUsageAccessSettings() }
            }
            addSettingsRow(
                body,
                "Detailed usage",
                if (usageAccessGranted) {
                    "Show up to four most-used non-Home apps below today's screen-on total"
                } else {
                    "Available after Usage Access is allowed"
                },
                if (usageAccessGranted) onOff(preferences.showDetailedUsage) else "requires access",
                enabled = usageAccessGranted,
            ) {
                preferences.showDetailedUsage = !preferences.showDetailedUsage
                renderWidgets()
                renderSettingsPage()
            }
        }

        addSettingsSection(body, "weather")
        addSettingsRow(
            body,
            "Show weather",
            "Optional current conditions from Open-Meteo",
            onOff(preferences.weatherEnabled),
        ) {
            preferences.weatherEnabled = !preferences.weatherEnabled
            applyWeatherSettings(requestApproximatePermission = preferences.weatherEnabled)
        }

        addSettingsSection(body, "temperature unit")
        val systemUnitSymbol = WeatherUnitPolicy.symbol(
            WeatherUnitPolicy.resolve(WeatherTemperatureUnit.SYSTEM, Locale.getDefault().country),
        )
        addWeatherUnitRow(body, WeatherTemperatureUnit.SYSTEM, getString(R.string.weather_system_unit, systemUnitSymbol))
        addWeatherUnitRow(body, WeatherTemperatureUnit.CELSIUS, getString(R.string.weather_celsius_unit))
        addWeatherUnitRow(body, WeatherTemperatureUnit.FAHRENHEIT, getString(R.string.weather_fahrenheit_unit))

        addSettingsSection(body, "location data")
        addSettingsRow(
            body,
            getString(R.string.weather_manual_location),
            getString(R.string.weather_manual_disclosure),
            selected(preferences.weatherLocationMode == WeatherLocationMode.MANUAL),
        ) {
            preferences.weatherLocationMode = WeatherLocationMode.MANUAL
            applyWeatherSettings()
        }
        addSettingsRow(
            body,
            getString(R.string.weather_approximate_location),
            getString(R.string.weather_location_disclosure),
            selected(preferences.weatherLocationMode == WeatherLocationMode.APPROXIMATE),
        ) {
            preferences.weatherLocationMode = WeatherLocationMode.APPROXIMATE
            applyWeatherSettings(requestApproximatePermission = preferences.weatherEnabled)
        }
        val coordinates = manualWeatherCoordinates()
        addSettingsRow(
            body,
            if (preferences.weatherLocationMode == WeatherLocationMode.MANUAL) "Coordinates" else "Fallback coordinates",
            if (preferences.weatherLocationMode == WeatherLocationMode.MANUAL) {
                "Latitude and longitude sent to Open-Meteo"
            } else {
                "Optional fallback when device location is unavailable"
            },
            coordinates?.let { "${preferences.weatherLatitude.trim()}, ${preferences.weatherLongitude.trim()}" } ?: "not set",
        ) { showManualWeatherCoordinatesEditor() }

        if (preferences.weatherLocationMode == WeatherLocationMode.APPROXIMATE) {
            val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            addSettingsRow(
                body,
                "Approximate-location permission",
                if (locationGranted) "Tap to review or revoke in Android" else "Required to use device location",
                if (locationGranted) "allowed" else "request",
            ) {
                if (locationGranted) openAppDetails()
                else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
            }
        }
    }

    private fun addWeatherUnitRow(body: LinearLayout, unit: WeatherTemperatureUnit, label: String) {
        addSettingsRow(body, label, "", selected(preferences.weatherTemperatureUnit == unit)) {
            preferences.weatherTemperatureUnit = unit
            applyWeatherSettings()
        }
    }

    private fun selected(value: Boolean): String = if (value) "selected" else ""

    private fun renderDrawerSettings(body: LinearLayout) {
        addSettingsSection(body, "apps")
        val hiddenAppCount = allApps.count { it.stableId in preferences.hiddenApps }
        addSettingsRow(
            body,
            "Manage apps",
            "Rename, hide, restore, or open app details",
            if (hiddenAppCount == 0) "${allApps.size}" else "$hiddenAppCount hidden",
            enabled = allApps.isNotEmpty(),
        ) { showAppManagementEditor() }

        addSettingsSection(body, "filters")
        addSettingsRow(
            body,
            "Manage filters",
            "Daily, media, and ${preferences.customFilters.size} custom categories",
            "edit",
        ) { showFilterEditor() }
        addSettingsRow(body, "Show filter labels", "Swipe navigation remains available when hidden", onOff(preferences.showFilterBar)) {
            preferences.showFilterBar = !preferences.showFilterBar
            applyDrawerPresentation()
            renderSettingsPage()
        }
        addSettingsRow(
            body,
            "Separate stock apps",
            "Show preinstalled apps in their own Stock section",
            onOff(preferences.separateStockApps),
        ) {
            preferences.separateStockApps = !preferences.separateStockApps
            rebuildFilterButtons()
            renderDrawer()
            renderSettingsPage()
        }

        addSettingsSection(body, "search")
        addSettingsRow(
            body,
            "Search backdrop",
            "Surface behind search and filter controls",
            preferences.drawerSurfaceMode.displayName,
        ) { showDrawerSurfaceModeEditor() }
        addSettingsRow(
            body,
            "Backdrop color",
            if (preferences.drawerSurfaceMode == DrawerSurfaceMode.CUSTOM) {
                "Current custom search backdrop"
            } else {
                "Available when Search backdrop is Custom"
            },
            formatColor(preferences.drawerSurfaceColor),
            enabled = preferences.drawerSurfaceMode == DrawerSurfaceMode.CUSTOM,
        ) { showColorChooser(ColorSettingTarget.DRAWER_BACKGROUND) }
        addSettingsRow(body, "Open keyboard automatically", "Search remains focused for physical keyboards", onOff(preferences.autoShowKeyboard)) {
            preferences.autoShowKeyboard = !preferences.autoShowKeyboard
            renderSettingsPage()
        }
        addSettingsRow(body, "Accent underline", "Show the line beneath search", onOff(preferences.showSearchUnderline)) {
            preferences.showSearchUnderline = !preferences.showSearchUnderline
            applyDrawerPresentation()
            renderSettingsPage()
        }
        addSettingsRow(body, "Search left margin", "Position the left-aligned search field", "${preferences.searchLeftMarginDp} dp") {
            showSearchLeftMarginEditor()
        }

        addSettingsSection(body, "layout")
        addSettingsRow(body, "App-list margins", "Top and right spacing", "${preferences.appListTopMarginDp} / ${preferences.appListRightMarginDp} dp") {
            showAppListMarginsEditor()
        }
        addSettingsRow(body, "Bottom fade", "Fade app rows into the selected search backdrop", onOff(preferences.showDrawerGradient)) {
            preferences.showDrawerGradient = !preferences.showDrawerGradient
            applyDrawerPresentation()
            renderSettingsPage()
        }

        addSettingsSection(body, "gesture")
        addSettingsRow(
            body,
            "Drawer dismissal",
            "Distance and speed sensitivity",
            "${preferences.drawerDismissDistanceSensitivity}% / ${preferences.drawerDismissSpeedSensitivity}%",
        ) { showDismissSensitivityEditor() }
    }

    private fun renderAppearanceSettings(body: LinearLayout) {
        addSettingsSection(body, "typography")
        addSettingsRow(body, "Font family", "Choose the launcher typeface", preferences.launcherFont.displayName) {
            showFontFamilyEditor()
        }
        addSettingsRow(
            body,
            "Text capitalization",
            "App names, filters, date, weather, and launcher labels",
            preferences.textTransform.displayName,
        ) { showTextTransformEditor() }
        addSettingsRow(body, "Font size", "Launcher text scale", "${preferences.fontScalePercent}%") { showFontSizeEditor() }
        addSettingsRow(body, "Font color", "Primary launcher text", formatColor(preferences.fontColor)) {
            showColorChooser(ColorSettingTarget.FONT)
        }
        addSettingsRow(body, "Accent color", "Filters, controls, and highlights", formatColor(preferences.accentColor)) {
            showColorChooser(ColorSettingTarget.ACCENT)
        }

        addSettingsSection(body, "background")
        addSettingsRow(body, "Background mode", "Wallpaper treatment or solid color", preferences.appearance.name.lowercase()) {
            showAppearanceEditor()
        }
        addSettingsRow(
            body,
            "Solid color",
            if (preferences.appearance == Appearance.SOLID) "Current opaque background" else "Available when Background mode is Solid",
            formatColor(preferences.solidBackgroundColor),
            enabled = preferences.appearance == Appearance.SOLID,
        ) { showColorChooser(ColorSettingTarget.SOLID_BACKGROUND) }

        addSettingsSection(body, "motion")
        addSettingsRow(body, "Animations", "Drawer and filter transitions", onOff(preferences.animationsEnabled)) {
            preferences.animationsEnabled = !preferences.animationsEnabled
            if (!preferences.animationsEnabled) resetMotionState()
            renderSettingsPage()
        }

        addSettingsSection(body, "system interface")
        addSettingsRow(body, "Hide status bar", "Use the top system-bar area", onOff(preferences.hideStatusBar)) {
            preferences.hideStatusBar = !preferences.hideStatusBar
            applyStatusBarPreference()
            renderSettingsPage()
        }
    }

    private fun renderSystemSettings(body: LinearLayout) {
        val roleManager = getSystemService(RoleManager::class.java)
        val isHome = roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        addSettingsSection(body, "home application")
        addSettingsRow(
            body,
            "Default Home",
            if (isHome) "minml launcher is the current Home app" else "Choose the device's Home app",
            if (isHome) "selected" else "choose",
        ) { requestHomeRole() }

        addSettingsSection(body, "permissions")
        val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        addSettingsRow(
            body,
            "Approximate location",
            "Used only when device-location weather is enabled",
            if (locationGranted) "allowed" else "not allowed",
        ) {
            if (locationGranted) openAppDetails()
            else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
        }
        val usageAccessGranted = screenTimeRepository.hasUsageAccess()
        addSettingsRow(
            body,
            "Usage access",
            "Used only by the optional screen-time Home widget",
            if (usageAccessGranted) "allowed" else "not allowed",
        ) { openUsageAccessSettings() }
        addSettingsRow(body, "App details", "Open Android's app information screen", "system") { openAppDetails() }
    }

    private fun renderAboutSettings(body: LinearLayout) {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        addSettingsSection(body, "minml launcher")
        body.addView(settingsText(
            "A native, text-first Android Home application focused on fast app access, negative space, and system-integrated customization.",
            13f,
            SETTINGS_PRIMARY_COLOR,
            regularTypeface,
        ).apply {
            setPadding(dp(12), dp(8), dp(12), dp(24))
            setLineSpacing(0f, 1.2f)
        })
        addSettingsRow(body, "Version", "Installed launcher build", "${packageInfo.versionName} (${packageInfo.longVersionCode})")
        addSettingsRow(body, "Application ID", "Android package identity", packageName)
        addSettingsRow(body, "Platform", "Native Android Views · no WebView", "SDK 29+")
        addSettingsRow(body, "License", "Free and open-source software", "GPL-3.0")
        addSettingsRow(body, "Source code", "github.com/Aagat/minml-launcher", "GitHub") {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Aagat/minml-launcher")))
            }
        }
    }

    private fun addSettingsSection(parent: LinearLayout, title: String) {
        parent.addView(settingsText(title.uppercase(Locale.getDefault()), 9f, SETTINGS_ACCENT_COLOR, mediumTypeface).apply {
            letterSpacing = 0.1f
            setPadding(dp(12), dp(22), dp(12), dp(8))
            isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun addSettingsRow(
        parent: LinearLayout,
        title: String,
        summary: String,
        value: String = "",
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minimumHeight = dp(68)
            alpha = if (enabled) 1f else 0.42f
            isEnabled = enabled
            isClickable = enabled && onClick != null
            isFocusable = enabled && onClick != null
            contentDescription = listOf(title, summary, value).filter { it.isNotBlank() }.joinToString(", ")
            if (enabled && onClick != null) setOnClickListener { onClick() }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(settingsText(title, 13f, SETTINGS_PRIMARY_COLOR, mediumTypeface), LinearLayout.LayoutParams(MATCH, WRAP))
        if (summary.isNotBlank()) labels.addView(
            settingsText(summary, 9.5f, SETTINGS_SECONDARY_COLOR, regularTypeface).apply { setPadding(0, dp(3), dp(8), 0) },
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
        row.addView(labels, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (value.isNotBlank()) row.addView(
            settingsText(value, 10f, if (enabled) SETTINGS_ACCENT_COLOR else SETTINGS_SECONDARY_COLOR, mediumTypeface).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxWidth = dp(150)
            },
            LinearLayout.LayoutParams(WRAP, MATCH),
        )
        parent.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun settingsText(textValue: String, sizeSp: Float, color: Int, face: Typeface): TextView = TextView(this).apply {
        text = textValue
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(sizeSp))
        setTextColor(color)
        typeface = face
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun loadLauncherTypefaces() {
        when (preferences.launcherFont) {
            LauncherFont.GEIST_MONO -> {
                regularTypeface = resources.getFont(R.font.geist_mono_nerd_regular)
                mediumTypeface = resources.getFont(R.font.geist_mono_nerd_medium)
            }
            LauncherFont.GEIST -> loadBundledTypefaces(R.font.geist_variable)
            LauncherFont.INTER -> loadBundledTypefaces(R.font.inter_variable)
            LauncherFont.IBM_PLEX_SANS -> loadBundledTypefaces(R.font.ibm_plex_sans_variable)
            LauncherFont.MANROPE -> loadBundledTypefaces(R.font.manrope_variable)
            LauncherFont.SPACE_GROTESK -> loadBundledTypefaces(R.font.space_grotesk_variable)
            LauncherFont.B612_SANS -> loadBundledTypefaces(R.font.b612_regular)
            LauncherFont.B612_MONO -> loadBundledTypefaces(R.font.b612_mono_regular)
            LauncherFont.SYSTEM_MONO -> {
                regularTypeface = Typeface.create("monospace", Typeface.NORMAL)
                mediumTypeface = Typeface.create("monospace", Typeface.BOLD)
            }
            LauncherFont.SYSTEM_SANS -> {
                regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
                mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
    }

    private fun loadBundledTypefaces(fontResource: Int) {
        val family = resources.getFont(fontResource)
        regularTypeface = Typeface.create(family, 400, false)
        mediumTypeface = Typeface.create(family, 500, false)
    }

    private fun applyDrawerPresentation() {
        searchUnderline.visibility = if (preferences.showSearchUnderline) View.VISIBLE else View.GONE
        drawerFade.visibility = if (preferences.showDrawerGradient) View.VISIBLE else View.GONE
        positionDrawerChildren()
        updateDrawerFocusTraversal()
    }

    private fun buildHome() {
        home = HomeGestureLayout(this).apply {
            id = R.id.launcher_home
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "minml launcher Home"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            onSwipeUp = { openDrawer() }
            onSwipeDown = { expandNotificationPanel() }
            onEmptyLongPress = { showLauncherSettings() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_OPEN_APPS, getString(R.string.open_apps)))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == ACTION_OPEN_APPS) {
                        openDrawer()
                        return true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        }

        clockPanel = LinearLayout(this).apply {
            id = R.id.home_clock
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(16), dp(10), dp(16), dp(12))
            contentDescription = "Built-in clock and date. Long press to move or resize."
            isLongClickable = true
            setOnLongClickListener {
                clockWidgetEditor?.let(::enterWidgetEditMode)
                true
            }
        }
        timeView = styledText(64f, primaryColor, mediumTypeface).apply {
            includeFontPadding = false
            letterSpacing = -0.06f
            isSingleLine = true
        }
        dateView = styledText(10f, secondaryColor, mediumTypeface).apply {
            letterSpacing = 0.08f
        }
        weatherView = styledText(10f, wallpaperSecondaryColor, regularTypeface).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(8), 0, 0)
        }
        clockPanel.addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP))
        homeRolePrompt = Button(this).apply {
            text = launcherText(getString(R.string.not_default_home_switch))
            contentDescription = "minml launcher is not the default Home app. Switch Home app."
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isAllCaps = false
            typeface = regularTypeface
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(9f))
            setTextColor(accentColor)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(6), 0, dp(12), 0)
            setOnClickListener { requestHomeRole() }
        }
        home.addView(homeRolePrompt, FrameLayout.LayoutParams(dp(270), dp(48)).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dp(150), dp(12), 0)
        })
        updateHomeRolePrompt()

        widgetContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        home.addView(widgetContainer, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            gravity = Gravity.TOP
            setMargins(dp(12), dp(12), dp(12), dp(80))
        })

        favoritesContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        home.addView(favoritesContainer, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            gravity = Gravity.TOP
            setMargins(dp(12), dp(12), dp(12), dp(92))
        })

        favoritesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        favoritesEditor = createWidgetEditorFrame().apply {
            id = R.id.home_favorites
            configureEditorBehavior(
                itemName = "favorites",
                allowResize = false,
                secondaryActionLabel = getString(R.string.widget_editor_reset_label),
                secondaryActionDescription = "Reset favorites to the bottom-right position",
            )
            onGeometryCommitted = { geometry ->
                preferences.favoritesPosition = HomeElementPosition(geometry.xPermille, geometry.yPermille)
            }
            onRemoveRequested = {
                preferences.favoritesPosition = HomeElementPosition.DEFAULT
                applyFavoritePosition()
                Toast.makeText(this@MainActivity, "Favorites returned to the bottom right", Toast.LENGTH_SHORT).show()
            }
        }
        favoritesEditor.addView(favoritesView, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        favoritesContainer.addView(favoritesEditor, FrameLayout.LayoutParams(dp(270), dp(48)))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildDrawer() {
        drawer = FilterGestureLayout(this).apply {
            id = R.id.launcher_drawer
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "App drawer"
            onFilterSwipe = { cycleFilter(it) }
            onSwipeDown = { handleDrawerSwipeDown() }
            canSwipeDown = { !appList.canScrollVertically(-1) }
            useImeDismissThreshold = { imeVisible }
            dismissDistanceSensitivity = preferences.drawerDismissDistanceSensitivity
            dismissSpeedSensitivity = preferences.drawerDismissSpeedSensitivity
        }

        appList = ListView(this).apply {
            id = R.id.drawer_app_list
            adapter = this@MainActivity.adapter
            contentDescription = getString(R.string.apps_list)
            divider = null
            setSelector(android.R.color.transparent)
            isVerticalScrollBarEnabled = false
            choiceMode = ListView.CHOICE_MODE_NONE
            isFocusable = true
            clipToPadding = false
            setPadding(0, 0, dp(14), 0)
            setOnItemClickListener { _, _, position, _ -> visibleApps.getOrNull(position)?.let(::launchApp) }
            setOnItemLongClickListener { _, _, position, _ ->
                visibleApps.getOrNull(position)?.stableId
                    ?.let { stableId -> allApps.firstOrNull { it.stableId == stableId } }
                    ?.let(::showAppCustomizationActions)
                true
            }
            setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: android.widget.AbsListView?, scrollState: Int) = Unit
                override fun onScroll(view: android.widget.AbsListView?, first: Int, visible: Int, total: Int) {
                    updateScrollThumb(total)
                }
            })
        }
        drawer.addView(appList, FrameLayout.LayoutParams(dp(300), dp(300)).apply { gravity = Gravity.END })

        emptyState = styledText(12f, secondaryColor, regularTypeface).apply {
            text = launcherText(getString(R.string.no_matching_apps))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            gravity = Gravity.END or Gravity.TOP
            setPadding(0, dp(32), dp(22), 0)
        }
        drawer.addView(emptyState, FrameLayout.LayoutParams(dp(300), dp(120)).apply { gravity = Gravity.END })
        appList.emptyView = emptyState

        drawerBottomSurface = View(this).apply {
            id = R.id.drawer_bottom_surface
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(Color.BLACK)
        }
        drawer.addView(drawerBottomSurface, FrameLayout.LayoutParams(MATCH, dp(102)).apply { gravity = Gravity.BOTTOM })

        drawerFade = View(this).apply {
            id = R.id.drawer_fade
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0x33000000, 0xB3000000.toInt(), Color.BLACK),
            )
        }
        drawer.addView(drawerFade, FrameLayout.LayoutParams(MATCH, dp(56)).apply { gravity = Gravity.BOTTOM })

        drawerHeader = styledText(8f, primaryColor, mediumTypeface).apply {
            id = R.id.drawer_header
            gravity = Gravity.END
            letterSpacing = 0.08f
            isAccessibilityHeading = true
        }
        drawer.addView(drawerHeader, FrameLayout.LayoutParams(dp(300), dp(24)).apply { gravity = Gravity.END })

        scrollTrack = View(this).apply { setBackgroundColor(0x6620201F) }
        scrollThumb = View(this).apply { setBackgroundColor(accentColor) }
        drawer.addView(scrollTrack, FrameLayout.LayoutParams(dp(1), dp(200)).apply { gravity = Gravity.END })
        drawer.addView(scrollThumb, FrameLayout.LayoutParams(dp(3), dp(36)).apply { gravity = Gravity.END })

        filtersView = LinearLayout(this).apply {
            id = R.id.drawer_filters
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        filtersScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                false
            }
            addView(filtersView, FrameLayout.LayoutParams(WRAP, MATCH).apply { gravity = Gravity.END })
        }
        rebuildFilterButtons()
        drawer.addView(filtersScroller, FrameLayout.LayoutParams(dp(300), dp(48)).apply { gravity = Gravity.END or Gravity.BOTTOM })

        searchFrame = FrameLayout(this)
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(styledText(12f, accentColor, mediumTypeface).apply {
            text = ">"
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(36), MATCH))
        searchInput = EditText(this).apply {
            id = R.id.drawer_search
            hint = launcherText(getString(R.string.search_hint))
            contentDescription = getString(R.string.search_apps)
            setHintTextColor(secondaryColor)
            setTextColor(primaryColor)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(14f))
            typeface = mediumTypeface
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderDrawer()
                    appList.setSelection(0)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    visibleApps.firstOrNull()?.let(::launchApp)
                    true
                } else false
            }
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, MATCH, 1f))
        searchFrame.addView(searchRow, FrameLayout.LayoutParams(MATCH, MATCH))
        searchUnderline = View(this).apply {
            setBackgroundColor(accentColor)
            visibility = if (preferences.showSearchUnderline) View.VISIBLE else View.GONE
        }
        searchFrame.addView(searchUnderline, FrameLayout.LayoutParams(MATCH, dp(1)).apply { gravity = Gravity.BOTTOM })
        drawer.addView(searchFrame, FrameLayout.LayoutParams(dp(300), dp(48)).apply { gravity = Gravity.END or Gravity.BOTTOM })
        updateDrawerFocusTraversal()
    }

    private fun rebuildFilterButtons() {
        if (!::filtersView.isInitialized) return
        val filters = availableFilters()
        if (filters.none { it.id == currentFilter.id }) currentFilter = FilterSpec.builtIn(DrawerFilter.ALL)
        filtersView.removeAllViews()
        var previousFocusId = appList.id
        filters.forEach { filter ->
            filtersView.addView(Button(this).apply {
                id = View.generateViewId()
                tag = filter
                text = launcherText(filter.displayName)
                contentDescription = "${filter.displayName} apps filter"
                isAllCaps = false
                typeface = regularTypeface
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(9f))
                minHeight = dp(48)
                minWidth = dp(48)
                minimumHeight = dp(48)
                minimumWidth = dp(48)
                setPadding(dp(4), 0, dp(4), 0)
                setBackgroundColor(Color.TRANSPARENT)
                accessibilityTraversalAfter = previousFocusId
                nextFocusUpId = previousFocusId
                setOnClickListener { setFilter(filter, filterDirectionTo(filter)) }
            }.also { previousFocusId = it.id }, LinearLayout.LayoutParams(WRAP, dp(48)))
        }
        updateDrawerFocusTraversal()
        filtersScroller.post { filtersScroller.scrollTo(0, 0) }
    }

    private fun updateDrawerFocusTraversal() {
        if (!::searchInput.isInitialized) return
        val previousId = if (preferences.showFilterBar && filtersView.childCount > 0) {
            filtersView.getChildAt(filtersView.childCount - 1).id
        } else {
            appList.id
        }
        searchInput.accessibilityTraversalAfter = previousId
        searchInput.nextFocusUpId = previousId
    }

    private fun positionDrawerChildren() {
        if (drawer.width == 0 || drawer.height == 0) return
        val availableHeight = drawer.height - drawer.paddingTop - drawer.paddingBottom
        val availableWidth = drawer.width - drawer.paddingLeft - drawer.paddingRight
        val columnWidth = min(dp(300), max(dp(220), availableWidth - dp(32)))
        val controlsHeight = dp(if (preferences.showFilterBar) 102 else 54)
        val listTop = min(dp(preferences.appListTopMarginDp), max(0, availableHeight - controlsHeight))
        val listHeight = max(0, availableHeight - listTop - controlsHeight)
        val right = dp(preferences.appListRightMarginDp)

        appList.layoutParams = (appList.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = listTop
            rightMargin = right
        }
        emptyState.layoutParams = (emptyState.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = listTop
            rightMargin = right
        }
        drawerHeader.layoutParams = (drawerHeader.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = dp(24)
            gravity = Gravity.END or Gravity.TOP
            topMargin = max(0, listTop - dp(26))
            rightMargin = right + dp(14)
        }
        drawerBottomSurface.layoutParams = (drawerBottomSurface.layoutParams as FrameLayout.LayoutParams).apply {
            height = controlsHeight + drawer.paddingBottom
            gravity = Gravity.BOTTOM
            bottomMargin = -drawer.paddingBottom
        }
        drawerFade.layoutParams = (drawerFade.layoutParams as FrameLayout.LayoutParams).apply {
            height = dp(56)
            gravity = Gravity.BOTTOM
            bottomMargin = controlsHeight
        }
        drawerFade.visibility = if (preferences.showDrawerGradient) View.VISIBLE else View.GONE
        filtersScroller.layoutParams = (filtersScroller.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            gravity = Gravity.END or Gravity.BOTTOM
            rightMargin = right
            bottomMargin = dp(54)
        }
        filtersScroller.visibility = if (preferences.showFilterBar) View.VISIBLE else View.GONE
        searchFrame.layoutParams = (searchFrame.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            gravity = Gravity.START or Gravity.BOTTOM
            leftMargin = dp(preferences.searchLeftMarginDp)
            rightMargin = 0
            bottomMargin = dp(6)
        }
        scrollTrack.layoutParams = (scrollTrack.layoutParams as FrameLayout.LayoutParams).apply {
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = listTop
            rightMargin = right + dp(2)
        }
        updateScrollThumb(adapter.count)
    }

    private fun updateScrollThumb(total: Int) {
        // ListView dispatches an initial scroll callback synchronously when the
        // listener is attached, before the custom indicator views are added.
        if (!::scrollTrack.isInitialized || !::scrollThumb.isInitialized) return
        val trackParams = scrollTrack.layoutParams as? FrameLayout.LayoutParams ?: return
        val trackHeight = trackParams.height
        val firstChild = appList.getChildAt(0)
        val geometry = if (firstChild == null) null else ScrollIndicatorPolicy.calculate(
            totalItems = total,
            firstVisibleItem = appList.firstVisiblePosition,
            firstChildTop = firstChild.top - appList.paddingTop,
            rowHeight = firstChild.height,
            viewportHeight = trackHeight,
            trackHeight = trackHeight,
            minimumThumbHeight = dp(30),
        )
        if (geometry == null) {
            scrollTrack.visibility = View.GONE
            scrollThumb.visibility = View.GONE
            scrollThumb.translationY = 0f
            return
        }
        scrollTrack.visibility = View.VISIBLE
        scrollThumb.visibility = View.VISIBLE
        val thumbParams = scrollThumb.layoutParams as FrameLayout.LayoutParams
        if (thumbParams.height != geometry.height ||
            thumbParams.topMargin != trackParams.topMargin ||
            thumbParams.rightMargin != trackParams.rightMargin - dp(1)
        ) {
            scrollThumb.layoutParams = thumbParams.apply {
                height = geometry.height
                gravity = Gravity.END or Gravity.TOP
                topMargin = trackParams.topMargin
                rightMargin = trackParams.rightMargin - dp(1)
            }
        }
        scrollThumb.translationY = geometry.offset
    }

    private fun onCatalogChanged(apps: List<AppEntry>) {
        if (apps.isEmpty() && allApps.isNotEmpty()) return
        allApps = apps
        runtimePreferences.edit().putString(CATALOG_CACHE_KEY, CatalogCacheCodec.encode(apps)).apply()
        initializeClassifications()
        reconcileFavorites()
        renderFavorites()
        renderDrawer()
        if (preferences.showDetailedUsage) updateScreenTime(force = true)
    }

    private fun restoreCachedCatalog() {
        val cached = CatalogCacheCodec.decode(runtimePreferences.getString(CATALOG_CACHE_KEY, "").orEmpty())
        if (cached.isEmpty()) return
        allApps = AppSearch.rank(cached, "")
        initializeClassifications()
        renderFavorites()
        renderDrawer()
    }

    private fun initializeClassifications() {
        if (preferences.favorites.isEmpty() && allApps.isNotEmpty()) {
            val preferredNames = listOf("messages", "camera", "maps", "notes")
            val seeded = preferredNames.mapNotNull { target ->
                allApps.firstOrNull { AppSearch.normalize(it.label).contains(target) }?.stableId
            }.distinct().toMutableList()
            allApps.map { it.stableId }.firstOrNull { it !in seeded }?.let { if (seeded.size < 4) seeded += it }
            preferences.favorites = seeded.take(4)
        }
        if (!preferences.isMembershipInitialized(DrawerFilter.DAILY)) {
            preferences.setMembership(DrawerFilter.DAILY, preferences.favorites)
        }
        if (!preferences.isMembershipInitialized(DrawerFilter.WORK)) {
            preferences.setMembership(DrawerFilter.WORK, allApps.filter { it.isWorkProfile }.map { it.stableId })
        }
        if (!preferences.isMembershipInitialized(DrawerFilter.MEDIA)) {
            preferences.setMembership(DrawerFilter.MEDIA, allApps.filter { it.isMedia }.map { it.stableId })
        }
    }

    private fun reconcileFavorites() {
        // Keep unavailable IDs in durable preferences for backup/reinstall reconciliation,
        // but never render an inert row.
        renderFavorites()
    }

    private fun availableFilters(): List<FilterSpec> =
        FilterCatalog.available(preferences.customFilters, preferences.separateStockApps)

    private fun membership(filter: FilterSpec): Set<String> = when (val builtIn = filter.builtIn) {
        DrawerFilter.ALL, DrawerFilter.WORK, DrawerFilter.STOCK -> emptySet()
        DrawerFilter.DAILY, DrawerFilter.MEDIA -> preferences.membership(builtIn)
        null -> preferences.customMembership(filter.id)
    }

    private fun renderFavorites() {
        favoritesView.removeAllViews()
        val byId = allApps.associateBy { it.stableId }
        val aliases = preferences.appAliases
        val visibleFavorites = preferences.favorites
            .mapNotNull(byId::get)
            .map { AppPresentationPolicy.presented(it, aliases) }
            .take(6)
        visibleFavorites.forEachIndexed { index, app ->
            favoritesView.addView(Button(this).apply {
                text = launcherText(app.label)
                contentDescription = "Open ${app.label}"
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                isAllCaps = false
                typeface = mediumTypeface
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(16f))
                setTextColor(primaryColor)
                setBackgroundColor(Color.TRANSPARENT)
                minHeight = dp(48)
                minimumHeight = dp(48)
                setPadding(dp(8), 0, dp(12), 0)
                setOnClickListener { launchApp(app) }
                setOnLongClickListener {
                    showFavoriteActions(index, app)
                    true
                }
            }, LinearLayout.LayoutParams(MATCH, dp(48)))
        }
        favoritesEditor.exitEditMode(commit = false)
        favoritesEditor.visibility = if (visibleFavorites.isEmpty()) View.GONE else View.VISIBLE
        if (visibleFavorites.isNotEmpty()) {
            favoritesEditor.layoutParams = (favoritesEditor.layoutParams as FrameLayout.LayoutParams).apply {
                width = dp(270)
                height = dp(visibleFavorites.size * 48)
            }
            favoritesContainer.post(::applyFavoritePosition)
        }
    }

    private fun renderDrawer() {
        if (!::searchInput.isInitialized) return
        val catalog = AppPresentationPolicy.visibleCatalog(allApps, preferences.hiddenApps, preferences.appAliases)
        val scoped = FilterEngine.apply(
            catalog,
            currentFilter,
            membership(currentFilter),
            preferences.separateStockApps,
        )
        visibleApps = AppSearch.rank(scoped, searchInput.text?.toString().orEmpty())
        val header = DrawerHeaderPolicy.content(
            launcherText(currentFilter.displayName),
            scoped.size,
        )
        drawerHeader.text = SpannableString(header.text).apply {
            setSpan(ForegroundColorSpan(accentColor), 0, header.accentEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(primaryColor), header.accentEnd, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        drawerHeader.setTextColor(primaryColor)
        adapter.notifyDataSetChanged()
        filtersView.children().forEach { button ->
            val spec = button.tag as FilterSpec
            val active = spec.id == currentFilter.id
            (button as TextView).setTextColor(if (active) accentColor else secondaryColor)
            button.isSelected = active
            if (android.os.Build.VERSION.SDK_INT >= 30) button.stateDescription = if (active) {
                getString(R.string.filter_selected_state)
            } else null
            button.contentDescription = "${spec.displayName} apps filter${if (active) ", selected" else ""}"
        }
        appList.post { updateScrollThumb(adapter.count) }
    }

    private fun setFilter(filter: FilterSpec, transitionDirection: Int = 0) {
        if (currentFilter.id == filter.id) return
        if (preferences.animationsEnabled && drawerOpen && transitionDirection != 0) {
            animateFilterChange(filter, transitionDirection)
            return
        }
        cancelFilterTransition()
        applyFilter(filter)
    }

    private fun applyFilter(filter: FilterSpec) {
        currentFilter = filter
        appList.setSelection(0)
        renderDrawer()
        filtersView.announceForAccessibility("${filter.displayName} filter")
    }

    private fun cycleFilter(step: Int) = setFilter(
        FilterCatalog.cycle(availableFilters(), currentFilter.id, step),
        transitionDirection = step,
    )

    private fun filterDirectionTo(filter: FilterSpec): Int {
        val filters = availableFilters()
        val currentIndex = filters.indexOfFirst { it.id == currentFilter.id }
        val targetIndex = filters.indexOfFirst { it.id == filter.id }
        return if (targetIndex >= currentIndex) 1 else -1
    }

    private fun animateFilterChange(filter: FilterSpec, direction: Int) {
        cancelFilterTransition()
        val generation = ++filterTransitionGeneration
        val distance = dp(FILTER_TRANSITION_DISTANCE_DP).toFloat()
        val outgoingTranslation = -direction.coerceIn(-1, 1) * distance
        val secondaryViews = listOf(appList, emptyState, scrollTrack, scrollThumb)
        appList.isEnabled = false
        secondaryViews.forEach { view ->
            view.animate()
                .alpha(FILTER_TRANSITION_DIM_ALPHA)
                .translationX(outgoingTranslation)
                .setDuration(FILTER_TRANSITION_OUT_MS)
                .start()
        }
        drawerHeader.animate()
            .alpha(FILTER_TRANSITION_DIM_ALPHA)
            .translationX(outgoingTranslation)
            .setDuration(FILTER_TRANSITION_OUT_MS)
            .withEndAction {
                if (generation != filterTransitionGeneration) return@withEndAction
                applyFilter(filter)
                val incomingTranslation = direction.coerceIn(-1, 1) * distance
                secondaryViews.forEach { view ->
                    view.animate().cancel()
                    view.translationX = incomingTranslation
                    view.alpha = FILTER_TRANSITION_DIM_ALPHA
                    view.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(FILTER_TRANSITION_IN_MS)
                        .start()
                }
                drawerHeader.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(FILTER_TRANSITION_IN_MS)
                    .withEndAction {
                        if (generation == filterTransitionGeneration) appList.isEnabled = true
                    }
                    .start()
            }
            .start()
    }

    private fun cancelFilterTransition() {
        filterTransitionGeneration++
        if (!::appList.isInitialized) return
        listOf(appList, emptyState, drawerHeader, scrollTrack, scrollThumb).forEach { view ->
            view.animate().cancel()
            view.alpha = 1f
            view.translationX = 0f
        }
        appList.isEnabled = true
    }

    private fun resetMotionState() {
        cancelFilterTransition()
        if (!::drawer.isInitialized || !::home.isInitialized) return
        drawer.animate().cancel()
        home.animate().cancel()
        drawer.alpha = 1f
        drawer.translationY = 0f
        home.alpha = 1f
        drawer.visibility = if (drawerOpen) View.VISIBLE else View.GONE
        home.visibility = if (drawerOpen) View.GONE else View.VISIBLE
    }

    private fun openDrawer(seed: String = "") {
        val animate = preferences.animationsEnabled && !drawerOpen
        drawerOpen = true
        drawer.animate().cancel()
        home.animate().cancel()
        drawer.visibility = View.VISIBLE
        if (animate) {
            drawer.alpha = 0f
            drawer.translationY = dp(DRAWER_TRANSITION_DISTANCE_DP).toFloat()
            home.visibility = View.VISIBLE
            home.alpha = 1f
            home.animate()
                .alpha(0f)
                .setDuration(DRAWER_TRANSITION_OUT_MS)
                .withEndAction {
                    if (drawerOpen) {
                        home.visibility = View.GONE
                        home.alpha = 1f
                    }
                }
                .start()
            drawer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(DRAWER_TRANSITION_IN_MS)
                .start()
        } else {
            home.visibility = View.GONE
            home.alpha = 1f
            drawer.alpha = 1f
            drawer.translationY = 0f
        }
        if (seed.isNotEmpty()) searchInput.setText(seed)
        renderDrawer()
        appList.setSelection(0)
        drawer.post {
            positionDrawerChildren()
            searchInput.requestFocus()
            searchInput.setSelection(searchInput.length())
            if (!preferences.autoShowKeyboard) return@post
            searchInput.postDelayed({
                if (!drawerOpen) return@postDelayed
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    searchInput.windowInsetsController?.show(WindowInsets.Type.ime())
                }
                getSystemService(InputMethodManager::class.java)
                    .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }, IME_SHOW_DELAY_MS)
        }
    }

    private fun expandNotificationPanel() {
        val statusBar = getSystemService("statusbar") ?: return
        runCatching {
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        }.onFailure {
            Toast.makeText(this, "Notification shade is unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeDrawer() {
        if (!drawerOpen && drawer.visibility != View.VISIBLE) return
        val animate = preferences.animationsEnabled && drawer.visibility == View.VISIBLE
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        searchInput.setText("")
        currentFilter = FilterSpec.builtIn(DrawerFilter.ALL)
        drawerOpen = false
        cancelFilterTransition()
        drawer.animate().cancel()
        home.animate().cancel()
        if (animate) {
            home.visibility = View.VISIBLE
            home.alpha = 0f
            home.animate().alpha(1f).setDuration(DRAWER_TRANSITION_IN_MS).start()
            drawer.animate()
                .alpha(0f)
                .translationY(dp(DRAWER_TRANSITION_DISTANCE_DP).toFloat())
                .setDuration(DRAWER_TRANSITION_OUT_MS)
                .withEndAction {
                    if (!drawerOpen) {
                        drawer.visibility = View.GONE
                        drawer.alpha = 1f
                        drawer.translationY = 0f
                    }
                }
                .start()
        } else {
            drawer.visibility = View.GONE
            drawer.alpha = 1f
            drawer.translationY = 0f
            home.visibility = View.VISIBLE
            home.alpha = 1f
        }
        home.requestFocus()
    }

    private fun dismissDrawerIme() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        drawer.requestFocus()
    }

    private fun handleDrawerSwipeDown() {
        if (imeVisible) dismissDrawerIme() else closeDrawer()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Handled by OnBackInvokedDispatcher on Android 13+")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        activeWidgetEditor?.takeIf { it.isEditing() }?.let {
            it.exitEditMode(commit = true)
            return
        }
        if (settingsPage != null) {
            handleSettingsBack()
            return
        }
        if (drawerOpen && imeVisible) {
            dismissDrawerIme()
            return
        }
        if (drawerOpen) {
            closeDrawer()
            return
        }
        // A launcher is the navigation root; Back on Home intentionally has no effect.
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && settingsPage == null && !drawerOpen && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
            val unicode = event.unicodeChar
            if (unicode != 0 && !Character.isISOControl(unicode)) {
                openDrawer(seed = String(Character.toChars(unicode)))
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && drawerOpen && event.keyCode == KeyEvent.KEYCODE_ENTER && appList.hasFocus()) {
            visibleApps.firstOrNull()?.let(::launchApp)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun launchApp(app: AppEntry) {
        catalog.launch(app)
            .onSuccess { closeDrawer() }
            .onFailure {
                Toast.makeText(this, "${app.label} is unavailable", Toast.LENGTH_SHORT).show()
                catalog.refresh()
            }
    }

    private fun openAppDetails(app: AppEntry) {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        }.onFailure {
            Toast.makeText(this, "App details are unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateClock() {
        val now = Date()
        val timePattern = ClockFormatPolicy.pattern(
            preferences.clockFormat,
            DateFormat.is24HourFormat(this),
        )
        timeView.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEddMMM")
        dateView.text = launcherText(SimpleDateFormat(datePattern, Locale.getDefault()).format(now))
        updateWeather()
        updateScreenTime()
    }

    private fun adaptHomeForWindow() {
        if (!::timeView.isInitialized || root.width == 0 || root.height == 0) return
        val landscape = root.width > root.height
        (favoritesContainer.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = dp(if (landscape) 12 else 92)
            favoritesContainer.layoutParams = params
            favoritesContainer.post(::applyFavoritePosition)
        }
        (widgetContainer.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.topMargin = dp(12)
            params.bottomMargin = dp(if (landscape) 64 else 80)
            widgetContainer.layoutParams = params
            widgetContainer.post(::applyWidgetGeometries)
        }
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 30_000L)
        }
    }

    private fun updateWeather() {
        if (!preferences.weatherEnabled) {
            weatherView.visibility = View.GONE
            coarseLocationResolver.cancel()
            locationRequestInFlight = false
            return
        }
        weatherView.visibility = View.VISIBLE
        val manual = manualWeatherCoordinates()
        if (preferences.weatherLocationMode == WeatherLocationMode.MANUAL) {
            renderWeatherCoordinateDecision(
                WeatherLocationPolicy.decide(WeatherLocationMode.MANUAL, false, null, manual),
            )
            return
        }

        val permissionGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            val decision = WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, false, null, manual)
            if (decision == WeatherCoordinateDecision.PermissionRequired && locationDeniedThisSession) {
                setWeatherText(getString(R.string.weather_location_denied))
            } else renderWeatherCoordinateDecision(decision)
            return
        }
        if (System.currentTimeMillis() - weatherRequestedAt < WEATHER_REFRESH_INTERVAL_MS || locationRequestInFlight) return
        locationRequestInFlight = true
        setWeatherText(getString(R.string.weather_loading))
        coarseLocationResolver.resolve { approximate ->
            locationRequestInFlight = false
            if (!preferences.weatherEnabled || preferences.weatherLocationMode != WeatherLocationMode.APPROXIMATE) return@resolve
            renderWeatherCoordinateDecision(
                WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, true, approximate, manualWeatherCoordinates()),
            )
        }
    }

    private fun updateScreenTime(force: Boolean = false) {
        val view = screenTimeView ?: return
        if (!preferences.showScreenTime) return
        if (!screenTimeRepository.hasUsageAccess()) {
            renderScreenTimeResult(view, ScreenTimeResult.PermissionRequired)
            return
        }
        val now = System.currentTimeMillis()
        if (screenTimeRequestInFlight || (!force && now - screenTimeRequestedAt < SCREEN_TIME_REFRESH_INTERVAL_MS)) return
        screenTimeRequestedAt = now
        screenTimeRequestInFlight = true
        val generation = ++screenTimeRequestGeneration
        if (view.text.isNullOrBlank()) view.text = launcherText(getString(R.string.screen_time_loading))
        val detailedPackages = if (preferences.showDetailedUsage) {
            allApps.asSequence().filterNot { it.isWorkProfile }.map { it.packageName }.toSet()
        } else {
            emptySet()
        }
        screenTimeRepository.load(now, detailedPackages) { result ->
            handler.post {
                screenTimeRequestInFlight = false
                if (
                    !isDestroyed &&
                    generation == screenTimeRequestGeneration &&
                    preferences.showScreenTime &&
                    screenTimeView === view
                ) {
                    renderScreenTimeResult(view, result)
                }
            }
        }
    }

    private fun renderScreenTimeResult(view: TextView, result: ScreenTimeResult) {
        when (result) {
            is ScreenTimeResult.Available -> {
                val compact = ScreenTimeFormatter.compact(result.durationMillis)
                view.text = launcherText(getString(R.string.screen_time_summary, compact))
                view.contentDescription =
                    "Screen on today, ${ScreenTimeFormatter.spoken(result.durationMillis)}. Long press to move or resize."
                view.isClickable = false
                view.isFocusable = false
                view.setOnClickListener(null)
                renderDetailedUsage(result.topApps)
            }
            ScreenTimeResult.PermissionRequired -> {
                view.text = launcherText(getString(R.string.screen_time_permission_required))
                view.contentDescription = "Open setup instructions to allow screen-on time access. Long press to move or resize."
                view.isClickable = true
                view.isFocusable = true
                view.setOnClickListener { showSettings(SettingsPage.HOME) }
                screenUsageView?.visibility = View.GONE
            }
            ScreenTimeResult.Unavailable -> {
                view.text = launcherText(getString(R.string.screen_time_unavailable))
                view.contentDescription = "Screen-on time is unavailable. Long press to move or resize."
                view.isClickable = false
                view.isFocusable = false
                view.setOnClickListener(null)
                screenUsageView?.visibility = View.GONE
            }
        }
    }

    private fun renderDetailedUsage(topApps: List<AppUsageDuration>) {
        val detailView = screenUsageView ?: return
        if (!preferences.showDetailedUsage) {
            detailView.visibility = View.GONE
            return
        }
        val aliases = preferences.appAliases
        val labelsByPackage = allApps.asSequence()
            .filterNot { it.isWorkProfile }
            .map { AppPresentationPolicy.presented(it, aliases) }
            .distinctBy { it.packageName }
            .associate { it.packageName to it.label }
        val rows = topApps.mapNotNull { usage ->
            labelsByPackage[usage.packageName]?.let { label ->
                Triple(label, ScreenTimeFormatter.compact(usage.durationMillis), usage.durationMillis)
            }
        }.take(DETAILED_USAGE_APP_LIMIT)
        detailView.visibility = View.VISIBLE
        if (rows.isEmpty()) {
            detailView.text = launcherText("no app usage yet")
            detailView.contentDescription = "No app usage recorded yet today."
        } else {
            detailView.text = rows.joinToString("\n") { (label, compact) -> launcherText("$label · $compact") }
            detailView.contentDescription = "Most used apps today. " + rows.joinToString(". ") { (label, _, duration) ->
                "$label, ${ScreenTimeFormatter.spoken(duration)}"
            }
        }
    }

    private fun manualWeatherCoordinates(): WeatherCoordinates? {
        val latitude = preferences.weatherLatitude.toDoubleOrNull()
        val longitude = preferences.weatherLongitude.toDoubleOrNull()
        return if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            WeatherCoordinates(latitude, longitude)
        } else null
    }

    private fun renderWeatherCoordinateDecision(decision: WeatherCoordinateDecision) {
        when (decision) {
            is WeatherCoordinateDecision.Use -> loadWeather(decision.coordinates)
            WeatherCoordinateDecision.PermissionRequired -> setWeatherText(getString(R.string.weather_location_permission_required))
            WeatherCoordinateDecision.LocationUnavailable -> setWeatherText(getString(R.string.weather_location_unavailable))
            WeatherCoordinateDecision.ManualLocationRequired -> setWeatherText(getString(R.string.weather_set_manual_location))
        }
    }

    private fun loadWeather(coordinates: WeatherCoordinates) {
        if (System.currentTimeMillis() - weatherRequestedAt < WEATHER_REFRESH_INTERVAL_MS) return
        weatherRequestedAt = System.currentTimeMillis()
        setWeatherText(getString(R.string.weather_loading))
        weatherRepository.load(
            coordinates.latitude,
            coordinates.longitude,
            preferences.weatherTemperatureUnit,
        ) { result ->
            handler.post {
                if (!preferences.weatherEnabled) return@post
                setWeatherText(when (result) {
                    is WeatherResult.Available -> with(result.snapshot) {
                        getString(
                            R.string.weather_summary,
                            temperature,
                            unit,
                            condition,
                            high,
                            low,
                            if (result.stale) getString(R.string.weather_stale_suffix) else "",
                        )
                    }
                    is WeatherResult.Unavailable -> result.message
                })
            }
        }
    }

    private fun setWeatherText(value: String) {
        weatherView.text = launcherText(value)
    }

    private fun applyAppearance() {
        val appearance = preferences.appearance
        val wallpaperPrimaryColor = systemWallpaperPrimaryColor()
        root.setBackgroundColor(
            if (appearance == Appearance.SOLID) preferences.solidBackgroundColor else Color.TRANSPARENT,
        )
        val autoDecision = if (appearance == Appearance.AUTO) {
            ContrastPolicy.decide(wallpaperPrimaryColor, primaryColor)
        } else null
        val decision = when (appearance) {
            Appearance.TRANSPARENT -> AutoContrastDecision(ScrimTone.NONE, ScrimStrength.LIGHT)
            Appearance.AUTO -> autoDecision!!
            Appearance.GRADIENT -> AutoContrastDecision(ScrimTone.DARK, ScrimStrength.STRONG)
            Appearance.SOLID -> AutoContrastDecision(ScrimTone.NONE, ScrimStrength.LIGHT)
        }
        contrastOverlay.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            fullScreenScrimColors(decision),
        )
        val drawerSurfaceColor = DrawerSurfacePolicy.color(
            preferences.drawerSurfaceMode,
            appearance,
            preferences.solidBackgroundColor,
            preferences.drawerSurfaceColor,
            wallpaperPrimaryColor,
        )
        drawerBottomSurface.setBackgroundColor(drawerSurfaceColor)
        drawerFade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            drawerSurfaceGradient(drawerSurfaceColor),
        )
        val localizedDecision = if (appearance == Appearance.AUTO) {
            ContrastPolicy.localizedFallback(decision, primaryColor)
        } else decision
        clockPanel.background = localizedClockScrimColors(localizedDecision)?.let { colors ->
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = dp(8).toFloat() }
        }
        dateView.setTextColor(if (localizedDecision.tone == ScrimTone.NONE) secondaryColor else wallpaperSecondaryColor)
        clockPanel.visibility = if (preferences.showBuiltInClock) View.VISIBLE else View.GONE
        screenTimeView?.setTextColor(wallpaperSecondaryColor)
        screenUsageView?.setTextColor(wallpaperSecondaryColor)
        widgetEditors.forEach { it.configureEditor(accentColor, primaryColor, mediumTypeface) }
        favoritesEditor.configureEditor(accentColor, primaryColor, mediumTypeface)
        root.post(::adaptHomeForWindow)
    }

    private fun drawerSurfaceGradient(surfaceColor: Int): IntArray = if (Color.alpha(surfaceColor) == 0) {
        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
    } else {
        intArrayOf(
            Color.TRANSPARENT,
            withAlpha(surfaceColor, 0x33),
            withAlpha(surfaceColor, 0xB3),
            surfaceColor,
        )
    }

    private fun openUsageAccessSettings() {
        val packageUri = Uri.parse("package:$packageName")
        val detailIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri)
        val intent = if (detailIntent.resolveActivity(packageManager) != null) {
            detailIntent
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Usage access settings are unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun systemWallpaperPrimaryColor(): Int? = runCatching {
        wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull()

    private fun fullScreenScrimColors(decision: AutoContrastDecision): IntArray = when (decision.tone) {
        ScrimTone.NONE -> intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
        ScrimTone.DARK -> if (decision.strength == ScrimStrength.STRONG) {
            intArrayOf(0x22000000, 0x44000000, 0xAA000000.toInt())
        } else {
            intArrayOf(Color.TRANSPARENT, 0x22000000, 0x66000000)
        }
        ScrimTone.LIGHT -> if (decision.strength == ScrimStrength.STRONG) {
            intArrayOf(0x22FFFFFF, 0x55FFFFFF, 0xAAFFFFFF.toInt())
        } else {
            intArrayOf(Color.TRANSPARENT, 0x22FFFFFF, 0x66FFFFFF)
        }
    }

    private fun localizedClockScrimColors(decision: AutoContrastDecision): IntArray? = when (decision.tone) {
        ScrimTone.NONE -> null
        ScrimTone.DARK -> intArrayOf(0xD9000000.toInt(), 0x99000000.toInt(), 0x33000000)
        ScrimTone.LIGHT -> intArrayOf(0xD9FFFFFF.toInt(), 0x99FFFFFF.toInt(), 0x33FFFFFF)
    }

    private fun applyStatusBarPreference() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (preferences.hideStatusBar) hide(WindowInsets.Type.statusBars()) else show(WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    (if (preferences.hideStatusBar) View.SYSTEM_UI_FLAG_FULLSCREEN else 0)
                )
        }
    }

    private fun showLauncherSettings() {
        showSettings()
    }

    private fun updateHomeRolePrompt() {
        val roleManager = getSystemService(RoleManager::class.java)
        homeRolePrompt.visibility = if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        val settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS)
        runCatching { startActivity(settingsIntent) }.recoverCatching {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else throw it
        }.onFailure { Toast.makeText(this, "Default Home settings are unavailable", Toast.LENGTH_SHORT).show() }
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun showAppearanceEditor() {
        val choices = arrayOf(
            "Auto · wallpaper with adaptive contrast",
            "Transparent · wallpaper without overlay",
            "Gradient · wallpaper with dark overlay",
            "Solid · selected background color",
        )
        AlertDialog.Builder(this)
            .setTitle("appearance")
            .setSingleChoiceItems(choices, preferences.appearance.ordinal) { dialog, which ->
                preferences.appearance = Appearance.entries[which]
                applyAppearance()
                renderSettingsPage()
                dialog.dismiss()
            }
            .show()
    }

    private fun showDrawerSurfaceModeEditor() {
        val choices = arrayOf(
            "Automatic · dark over wallpaper, matching Solid backgrounds",
            "Dark · black search and filter surface",
            "Transparent · show the background underneath",
            "Wallpaper color · derive from the system wallpaper",
            "Custom · use the selected backdrop color",
        )
        AlertDialog.Builder(this)
            .setTitle("search backdrop")
            .setSingleChoiceItems(choices, preferences.drawerSurfaceMode.ordinal) { dialog, which ->
                preferences.drawerSurfaceMode = DrawerSurfaceMode.entries[which]
                applyAppearance()
                renderSettingsPage()
                dialog.dismiss()
            }
            .show()
    }

    private fun showColorChooser(target: ColorSettingTarget) {
        val current = selectedColor(target)
        val backgroundTarget = target == ColorSettingTarget.SOLID_BACKGROUND ||
            target == ColorSettingTarget.DRAWER_BACKGROUND
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        content.addView(settingsText(
            if (backgroundTarget) {
                "Choose a suggested color pair. The launcher will also set readable text automatically."
            } else {
                "Choose a suggested color, or create an exact custom color."
            },
            10f,
            SETTINGS_SECONDARY_COLOR,
            regularTypeface,
        ).apply { setPadding(dp(12), 0, dp(12), dp(8)) })

        lateinit var dialog: AlertDialog
        val presetIds = intArrayOf(
            R.id.color_preset_1,
            R.id.color_preset_2,
            R.id.color_preset_3,
            R.id.color_preset_4,
            R.id.color_preset_5,
        )
        LauncherColorPalette.presets(target).forEachIndexed { index, preset ->
            val row = LinearLayout(this).apply {
                id = presetIds[index]
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                minimumHeight = dp(56)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                contentDescription = buildString {
                    append(preset.name).append(", ").append(formatColor(preset.color))
                    preset.pairedFontColor?.let { append(", paired text ").append(formatColor(it)) }
                    if (preset.color == current) append(", selected")
                }
                setOnClickListener {
                    applyColorSelection(target, preset.color)
                    dialog.dismiss()
                }
            }
            val sampleTextColor = preset.pairedFontColor ?: LauncherColorPalette.pairedFontColor(preset.color)
            val swatch = TextView(this).apply {
                gravity = Gravity.CENTER
                text = if (backgroundTarget) "Aa" else "●"
                typeface = mediumTypeface
                setTextColor(sampleTextColor)
                background = GradientDrawable().apply {
                    setColor(preset.color)
                    cornerRadius = dp(7).toFloat()
                    if (preset.color == current) setStroke(dp(2), accentColor)
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(swatch, LinearLayout.LayoutParams(dp(56), dp(44)).apply { marginEnd = dp(12) })
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(settingsText(preset.name, 12f, SETTINGS_PRIMARY_COLOR, mediumTypeface))
            labels.addView(settingsText(
                if (preset.pairedFontColor == null) {
                    formatColor(preset.color)
                } else {
                    "${formatColor(preset.color)} · text ${formatColor(preset.pairedFontColor)}"
                },
                9f,
                SETTINGS_SECONDARY_COLOR,
                regularTypeface,
            ))
            row.addView(labels, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(settingsText(
                if (preset.color == current) "✓" else "",
                15f,
                SETTINGS_ACCENT_COLOR,
                mediumTypeface,
            ).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), dp(44)))
            content.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        content.addView(colorDialogButton("color picker…", "Open visual color picker") {
            dialog.dismiss()
            showVisualColorPicker(target, current)
        }, LinearLayout.LayoutParams(MATCH, dp(48)))
        content.addView(colorDialogButton("hex code…", "Enter an exact hexadecimal color") {
            dialog.dismiss()
            showHexColorEditor(target, current)
        }, LinearLayout.LayoutParams(MATCH, dp(48)))

        dialog = AlertDialog.Builder(this)
            .setTitle(target.title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("cancel", null)
            .create()
        dialog.show()
    }

    private fun showVisualColorPicker(target: ColorSettingTarget, initialColor: Int) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val preview = TextView(this).apply {
            id = R.id.color_picker_preview
            gravity = Gravity.CENTER
            typeface = mediumTypeface
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            minHeight = dp(56)
        }
        val picker = HsvColorPickerView(this).apply {
            id = R.id.visual_color_picker
            setPadding(0, dp(8), 0, dp(8))
        }
        val hueLabel = settingsText("", 10f, SETTINGS_SECONDARY_COLOR, regularTypeface)
        val saturationLabel = settingsText("", 10f, SETTINGS_SECONDARY_COLOR, regularTypeface)
        val brightnessLabel = settingsText("", 10f, SETTINGS_SECONDARY_COLOR, regularTypeface)
        val hueSlider = SeekBar(this).apply {
            id = R.id.color_hue_slider
            max = 360
            contentDescription = "Hue"
        }
        val saturationSlider = SeekBar(this).apply {
            id = R.id.color_saturation_slider
            max = 100
            contentDescription = "Saturation"
        }
        val brightnessSlider = SeekBar(this).apply {
            id = R.id.color_brightness_slider
            max = 100
            contentDescription = "Brightness"
        }
        var selected = initialColor or 0xFF000000.toInt()
        var synchronizing = false

        fun renderColor(color: Int, updatePicker: Boolean) {
            selected = color or 0xFF000000.toInt()
            val hsv = FloatArray(3)
            Color.colorToHSV(selected, hsv)
            synchronizing = true
            hueSlider.progress = hsv[0].toInt()
            saturationSlider.progress = (hsv[1] * 100).toInt()
            brightnessSlider.progress = (hsv[2] * 100).toInt()
            synchronizing = false
            hueLabel.text = getString(R.string.color_hue_value, hueSlider.progress)
            saturationLabel.text = getString(R.string.color_saturation_value, saturationSlider.progress)
            brightnessLabel.text = getString(R.string.color_brightness_value, brightnessSlider.progress)
            val backgroundTarget = target == ColorSettingTarget.SOLID_BACKGROUND ||
                target == ColorSettingTarget.DRAWER_BACKGROUND
            val paired = LauncherColorPalette.pairedFontColor(selected)
            if (backgroundTarget) {
                preview.setBackgroundColor(selected)
                preview.setTextColor(paired)
                preview.text = getString(R.string.color_preview_pair, formatColor(selected), formatColor(paired))
            } else {
                preview.setBackgroundColor(paired)
                preview.setTextColor(selected)
                preview.text = getString(R.string.color_preview_single, formatColor(selected))
            }
            preview.contentDescription = preview.text
            if (updatePicker) picker.color = selected
        }

        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (synchronizing) return
                renderColor(
                    Color.HSVToColor(floatArrayOf(
                        hueSlider.progress.toFloat(),
                        saturationSlider.progress / 100f,
                        brightnessSlider.progress / 100f,
                    )),
                    updatePicker = true,
                )
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        hueSlider.setOnSeekBarChangeListener(sliderListener)
        saturationSlider.setOnSeekBarChangeListener(sliderListener)
        brightnessSlider.setOnSeekBarChangeListener(sliderListener)
        picker.onColorChanged = { renderColor(it, updatePicker = false) }
        renderColor(selected, updatePicker = true)

        content.addView(preview, LinearLayout.LayoutParams(MATCH, dp(56)))
        content.addView(picker, LinearLayout.LayoutParams(MATCH, dp(250)))
        listOf(
            hueLabel to hueSlider,
            saturationLabel to saturationSlider,
            brightnessLabel to brightnessSlider,
        ).forEach { (label, slider) ->
            content.addView(label, LinearLayout.LayoutParams(MATCH, WRAP))
            content.addView(slider, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        AlertDialog.Builder(this)
            .setTitle("custom ${target.title}")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("save") { _, _ -> applyColorSelection(target, selected) }
            .setNeutralButton("hex code") { _, _ -> showHexColorEditor(target, selected) }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showHexColorEditor(target: ColorSettingTarget, initialColor: Int) {
        val input = EditText(this).apply {
            id = R.id.color_hex_input
            hint = "#RRGGBB"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(formatColor(initialColor))
            setSelection(length())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("exact ${target.title}")
            .setMessage("Enter #RGB, #RRGGBB, or #AARRGGBB. Saved colors are always opaque.")
            .setView(input)
            .setPositiveButton("save", null)
            .setNeutralButton("color picker") { _, _ -> showVisualColorPicker(target, initialColor) }
            .setNegativeButton("cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = LauncherColorPalette.parseHex(input.text.toString())
                if (parsed == null) {
                    input.error = "Use a color such as #B7F36B"
                } else {
                    dialog.dismiss()
                    applyColorSelection(target, parsed)
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun selectedColor(target: ColorSettingTarget): Int = when (target) {
        ColorSettingTarget.FONT -> preferences.fontColor
        ColorSettingTarget.ACCENT -> preferences.accentColor
        ColorSettingTarget.SOLID_BACKGROUND -> preferences.solidBackgroundColor
        ColorSettingTarget.DRAWER_BACKGROUND -> preferences.drawerSurfaceColor
    }

    private fun applyColorSelection(target: ColorSettingTarget, color: Int) {
        val opaque = color or 0xFF000000.toInt()
        val oldFont = preferences.fontColor
        when (target) {
            ColorSettingTarget.FONT -> preferences.fontColor = opaque
            ColorSettingTarget.ACCENT -> preferences.accentColor = opaque
            ColorSettingTarget.SOLID_BACKGROUND -> {
                preferences.solidBackgroundColor = opaque
                preferences.fontColor = LauncherColorPalette.pairedFontColor(opaque)
            }
            ColorSettingTarget.DRAWER_BACKGROUND -> {
                preferences.drawerSurfaceColor = opaque
                preferences.fontColor = LauncherColorPalette.pairedFontColor(opaque)
            }
        }
        if (
            (target == ColorSettingTarget.SOLID_BACKGROUND || target == ColorSettingTarget.DRAWER_BACKGROUND) &&
            oldFont != preferences.fontColor
        ) {
            Toast.makeText(
                this,
                "Text changed to ${formatColor(preferences.fontColor)} for contrast",
                Toast.LENGTH_LONG,
            ).show()
        }
        recreate()
    }

    private fun colorDialogButton(label: String, description: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        isAllCaps = false
        typeface = mediumTypeface
        setTextColor(SETTINGS_ACCENT_COLOR)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { action() }
    }

    private fun showClockFormatEditor() {
        val choices = arrayOf("System default", "12-hour", "24-hour")
        AlertDialog.Builder(this)
            .setTitle("clock format")
            .setSingleChoiceItems(choices, preferences.clockFormat.ordinal) { dialog, which ->
                preferences.clockFormat = ClockFormat.entries[which]
                updateClock()
                renderSettingsPage()
                dialog.dismiss()
            }
            .show()
    }

    private fun clockFormatLabel(): String = when (preferences.clockFormat) {
        ClockFormat.SYSTEM -> "system"
        ClockFormat.TWELVE_HOUR -> "12-hour"
        ClockFormat.TWENTY_FOUR_HOUR -> "24-hour"
    }

    private fun showSearchLeftMarginEditor() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val value = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.search_left_margin_value, preferences.searchLeftMarginDp)
        }
        val slider = SeekBar(this).apply {
            max = 64
            progress = preferences.searchLeftMarginDp
            contentDescription = "Search left margin"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = getString(R.string.search_left_margin_value, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(value, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(slider, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("search left margin")
            .setView(content)
            .setPositiveButton("save") { _, _ ->
                preferences.searchLeftMarginDp = slider.progress
                positionDrawerChildren()
                renderSettingsPage()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showAppListMarginsEditor() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val topValue = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.app_list_top_margin_value, preferences.appListTopMarginDp)
        }
        val topSlider = SeekBar(this).apply {
            max = 72
            progress = preferences.appListTopMarginDp - 24
            contentDescription = "App list top margin"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    topValue.text = getString(R.string.app_list_top_margin_value, progress + 24)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val rightValue = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.app_list_right_margin_value, preferences.appListRightMarginDp)
        }
        val rightSlider = SeekBar(this).apply {
            max = 64
            progress = preferences.appListRightMarginDp
            contentDescription = "App list right margin"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    rightValue.text = getString(R.string.app_list_right_margin_value, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(topValue, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(topSlider, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(rightValue, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(rightSlider, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("app list margins")
            .setView(content)
            .setPositiveButton("save") { _, _ ->
                preferences.appListTopMarginDp = topSlider.progress + 24
                preferences.appListRightMarginDp = rightSlider.progress
                positionDrawerChildren()
                renderSettingsPage()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showFontFamilyEditor() {
        val choices = LauncherFont.entries
        var selectedIndex = choices.indexOf(preferences.launcherFont)
        AlertDialog.Builder(this)
            .setTitle("font family")
            .setSingleChoiceItems(choices.map { it.displayName }.toTypedArray(), selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("save") { _, _ ->
                val selectedFont = choices[selectedIndex]
                if (selectedFont != preferences.launcherFont) {
                    preferences.launcherFont = selectedFont
                    recreate()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showTextTransformEditor() {
        val choices = LauncherTextTransform.entries
        AlertDialog.Builder(this)
            .setTitle("text capitalization")
            .setSingleChoiceItems(
                choices.map { it.displayName }.toTypedArray(),
                choices.indexOf(preferences.textTransform),
            ) { dialog, which ->
                val selectedTransform = choices[which]
                dialog.dismiss()
                if (selectedTransform != preferences.textTransform) {
                    preferences.textTransform = selectedTransform
                    recreate()
                }
            }
            .show()
    }

    private fun showFontSizeEditor() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val value = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.font_size_value, preferences.fontScalePercent)
        }
        val slider = SeekBar(this).apply {
            max = 75
            progress = preferences.fontScalePercent - 75
            contentDescription = "Launcher font size"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = getString(R.string.font_size_value, progress + 75)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(value, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(slider, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("font size")
            .setView(content)
            .setPositiveButton("save") { _, _ ->
                preferences.fontScalePercent = slider.progress + 75
                recreate()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showDismissSensitivityEditor() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val distanceValue = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.drawer_dismiss_distance_value, preferences.drawerDismissDistanceSensitivity)
        }
        val distanceSlider = SeekBar(this).apply {
            max = 100
            progress = preferences.drawerDismissDistanceSensitivity
            contentDescription = getString(R.string.drawer_dismiss_distance)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    distanceValue.text = getString(R.string.drawer_dismiss_distance_value, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val speedValue = styledText(12f, primaryColor, mediumTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.drawer_dismiss_speed_value, preferences.drawerDismissSpeedSensitivity)
        }
        val speedSlider = SeekBar(this).apply {
            max = 100
            progress = preferences.drawerDismissSpeedSensitivity
            contentDescription = getString(R.string.drawer_dismiss_speed)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speedValue.text = getString(R.string.drawer_dismiss_speed_value, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val guidance = styledText(10f, secondaryColor, regularTypeface).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = getString(R.string.drawer_dismiss_sensitivity_guidance)
        }
        content.addView(distanceValue, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(distanceSlider, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(speedValue, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(speedSlider, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(guidance, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.drawer_dismiss_sensitivity))
            .setView(content)
            .setPositiveButton("save") { _, _ ->
                preferences.drawerDismissDistanceSensitivity = distanceSlider.progress
                preferences.drawerDismissSpeedSensitivity = speedSlider.progress
                drawer.dismissDistanceSensitivity = distanceSlider.progress
                drawer.dismissSpeedSensitivity = speedSlider.progress
                renderSettingsPage()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showFavoriteEditor() {
        if (allApps.isEmpty()) return
        val selected = preferences.favorites.toMutableSet()
        val labels = allApps.map(::settingsAppLabel).toTypedArray()
        val checked = BooleanArray(allApps.size) { allApps[it].stableId in selected }
        AlertDialog.Builder(this)
            .setTitle("favorite apps")
            .setMultiChoiceItems(labels, checked) { _, which, enabled ->
                if (enabled) selected += allApps[which].stableId else selected -= allApps[which].stableId
            }
            .setPositiveButton("save") { _, _ ->
                val existing = preferences.favorites.filter { it in selected }
                val added = allApps.map { it.stableId }.filter { it in selected && it !in existing }
                preferences.favorites = existing + added
                if (!preferences.isMembershipInitialized(DrawerFilter.DAILY)) {
                    preferences.setMembership(DrawerFilter.DAILY, preferences.favorites)
                }
                renderFavorites()
                renderDrawer()
                renderSettingsPage()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showFavoriteActions(index: Int, app: AppEntry) {
        val actions = arrayOf("move up", "move down", "move favorites block", "remove")
        AlertDialog.Builder(this)
            .setTitle(settingsAppLabel(app))
            .setItems(actions) { _, which ->
                val favorites = preferences.favorites.toMutableList()
                val actualIndex = favorites.indexOf(app.stableId).takeIf { it >= 0 } ?: index
                when (which) {
                    0 -> if (actualIndex > 0) favorites[actualIndex] = favorites[actualIndex - 1].also { favorites[actualIndex - 1] = favorites[actualIndex] }
                    1 -> if (actualIndex < favorites.lastIndex) favorites[actualIndex] = favorites[actualIndex + 1].also { favorites[actualIndex + 1] = favorites[actualIndex] }
                    2 -> {
                        enterWidgetEditMode(favoritesEditor)
                        return@setItems
                    }
                    3 -> favorites.remove(app.stableId)
                }
                preferences.favorites = favorites
                renderFavorites()
            }
            .show()
    }

    private fun showAppManagementEditor() {
        if (allApps.isEmpty()) return
        val aliases = preferences.appAliases
        val hidden = preferences.hiddenApps
        var filteredApps = managedAppsForQuery("", aliases)
        val search = EditText(this).apply {
            hint = "search apps"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        lateinit var adapter: BaseAdapter
        val list = ListView(this).apply {
            divider = null
            isVerticalScrollBarEnabled = true
        }
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredApps.size
            override fun getItem(position: Int): AppEntry = filteredApps[position]
            override fun getItemId(position: Int): Long = filteredApps[position].stableId.hashCode().toLong()
            override fun hasStableIds(): Boolean = true

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: styledText(11f, primaryColor, regularTypeface).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(56)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                val app = getItem(position)
                row.text = managementAppLabel(app, aliases, hidden)
                row.contentDescription = row.text
                return row
            }
        }
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filteredApps = managedAppsForQuery(s?.toString().orEmpty(), aliases)
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            addView(search, LinearLayout.LayoutParams(MATCH, dp(52)))
            addView(list, LinearLayout.LayoutParams(MATCH, dp(440)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("manage apps")
            .setView(content)
            .setNegativeButton("close", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            showAppCustomizationActions(app)
        }
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
    }

    private fun managedAppsForQuery(query: String, aliases: Map<String, String>): List<AppEntry> {
        val normalizedQuery = AppSearch.normalize(query)
        return allApps.filter { app ->
            normalizedQuery.isEmpty() || AppSearch.normalize(
                listOf(app.label, aliases[app.stableId].orEmpty(), app.packageName).joinToString(" "),
            ).contains(normalizedQuery)
        }.sortedWith(compareBy(
            { AppSearch.normalize(aliases[it.stableId].orEmpty().ifBlank { it.label }) },
            { it.isWorkProfile },
            { it.stableId },
        ))
    }

    private fun managementAppLabel(app: AppEntry, aliases: Map<String, String>, hidden: Set<String>): String {
        val alias = aliases[app.stableId]?.takeIf { it.isNotBlank() }
        return buildString {
            append(alias ?: app.label)
            if (app.isWorkProfile) append(" (w)")
            if (alias != null && alias != app.label) append(" · originally ").append(app.label)
            if (app.stableId in hidden) append(" · hidden")
        }
    }

    private fun showAppCustomizationActions(app: AppEntry) {
        val hidden = app.stableId in preferences.hiddenApps
        val renamed = app.stableId in preferences.appAliases
        val labels = mutableListOf("rename", if (hidden) "show in app drawer" else "hide from app drawer")
        if (renamed) labels += "reset name"
        labels += "app details"
        AlertDialog.Builder(this)
            .setTitle(settingsAppLabel(app))
            .setItems(labels.toTypedArray()) { _, which ->
                when (labels[which]) {
                    "rename" -> showAppRenameEditor(app)
                    "show in app drawer" -> {
                        preferences.setAppHidden(app.stableId, false)
                        refreshAfterAppCustomization(reopenManager = true)
                    }
                    "hide from app drawer" -> {
                        preferences.setAppHidden(app.stableId, true)
                        refreshAfterAppCustomization(reopenManager = true)
                    }
                    "reset name" -> {
                        preferences.setAppAlias(app.stableId, null)
                        refreshAfterAppCustomization(reopenManager = true)
                    }
                    "app details" -> openAppDetails(app)
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showAppRenameEditor(app: AppEntry) {
        val input = EditText(this).apply {
            hint = app.label
            setSingleLine(true)
            setText(preferences.appAliases[app.stableId] ?: app.label)
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle("rename ${app.label}")
            .setMessage("Leave the name empty to restore the original label.")
            .setView(input)
            .setPositiveButton("save") { _, _ ->
                val alias = input.text.toString().trim().take(40).takeUnless { it == app.label }
                preferences.setAppAlias(app.stableId, alias)
                refreshAfterAppCustomization(reopenManager = true)
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun refreshAfterAppCustomization(reopenManager: Boolean) {
        renderFavorites()
        renderDrawer()
        if (settingsPage != null) renderSettingsPage()
        if (reopenManager && settingsPage == SettingsPage.DRAWER) handler.post(::showAppManagementEditor)
    }

    private fun showFilterEditor() {
        val custom = preferences.customFilters
        val labels = listOf("add category", DrawerFilter.DAILY.displayName, DrawerFilter.MEDIA.displayName) +
            custom.map { "${it.name} · custom" }
        AlertDialog.Builder(this)
            .setTitle("edit filter")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showCustomFilterNameEditor(null)
                    1 -> showMembershipEditor(FilterSpec.builtIn(DrawerFilter.DAILY))
                    2 -> showMembershipEditor(FilterSpec.builtIn(DrawerFilter.MEDIA))
                    else -> showCustomFilterActions(custom[which - 3])
                }
            }
            .show()
    }

    private fun showCustomFilterNameEditor(existing: CustomFilter?) {
        val name = EditText(this).apply {
            hint = "category name"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "new filter category" else "rename filter category")
            .setView(name)
            .setPositiveButton("save") { _, _ ->
                val value = name.text.toString().trim().take(18)
                if (value.isEmpty()) {
                    Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val filters = preferences.customFilters.toMutableList()
                val saved = if (existing == null) {
                    CustomFilter("custom:${UUID.randomUUID()}", value).also(filters::add)
                } else {
                    existing.copy(name = value).also { updated ->
                        val index = filters.indexOfFirst { it.id == existing.id }
                        if (index >= 0) filters[index] = updated
                    }
                }
                preferences.customFilters = filters
                rebuildFilterButtons()
                renderDrawer()
                renderSettingsPage()
                if (existing == null) showMembershipEditor(FilterSpec.custom(saved))
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showCustomFilterActions(filter: CustomFilter) {
        AlertDialog.Builder(this)
            .setTitle(filter.name)
            .setItems(arrayOf("edit apps", "rename", "delete")) { _, which ->
                when (which) {
                    0 -> showMembershipEditor(FilterSpec.custom(filter))
                    1 -> showCustomFilterNameEditor(filter)
                    2 -> {
                        preferences.customFilters = preferences.customFilters.filterNot { it.id == filter.id }
                        if (currentFilter.id == filter.id) currentFilter = FilterSpec.builtIn(DrawerFilter.ALL)
                        rebuildFilterButtons()
                        renderDrawer()
                        renderSettingsPage()
                    }
                }
            }
            .show()
    }

    private fun showMembershipEditor(filter: FilterSpec) {
        val eligibleApps = allApps.filterNot { it.isWorkProfile }
        val selected = membership(filter).toMutableSet()
        val checked = BooleanArray(eligibleApps.size) { eligibleApps[it].stableId in selected }
        AlertDialog.Builder(this)
            .setTitle("${filter.displayName} apps")
            .setMultiChoiceItems(eligibleApps.map(::settingsAppLabel).toTypedArray(), checked) { _, which, enabled ->
                if (enabled) selected += eligibleApps[which].stableId else selected -= eligibleApps[which].stableId
            }
            .setPositiveButton("save") { _, _ ->
                val builtIn = filter.builtIn
                if (builtIn == null) preferences.setCustomMembership(filter.id, selected)
                else preferences.setMembership(builtIn, selected)
                renderDrawer()
                renderSettingsPage()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun settingsAppLabel(app: AppEntry): String {
        val presented = AppPresentationPolicy.presented(app, preferences.appAliases)
        return presented.label + if (app.isWorkProfile) " (w)" else ""
    }

    private fun showManualWeatherCoordinatesEditor() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val disclosure = styledText(11f, secondaryColor, regularTypeface).apply {
            text = getString(R.string.weather_manual_disclosure)
            setPadding(0, dp(8), 0, dp(8))
        }
        val latitude = EditText(this).apply {
            hint = "latitude"
            setText(preferences.weatherLatitude)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val longitude = EditText(this).apply {
            hint = "longitude"
            setText(preferences.weatherLongitude)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        form.addView(disclosure)
        form.addView(latitude)
        form.addView(longitude)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (preferences.weatherLocationMode == WeatherLocationMode.MANUAL) "weather coordinates" else "fallback coordinates")
            .setView(form)
            .setPositiveButton("save") { _, _ ->
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                val manualProvided = latitude.text.isNotBlank() || longitude.text.isNotBlank()
                val manualValid = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                if ((preferences.weatherEnabled && preferences.weatherLocationMode == WeatherLocationMode.MANUAL || manualProvided) && !manualValid) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                } else {
                    preferences.weatherLatitude = latitude.text.toString().trim()
                    preferences.weatherLongitude = longitude.text.toString().trim()
                    applyWeatherSettings()
                }
            }
            .setNegativeButton("cancel", null)
            .create()
        dialog.show()
    }

    private fun applyWeatherSettings(requestApproximatePermission: Boolean = false) {
        weatherRequestedAt = 0L
        locationDeniedThisSession = false
        coarseLocationResolver.cancel()
        locationRequestInFlight = false
        updateWeather()
        renderSettingsPage()
        if (requestApproximatePermission &&
            preferences.weatherLocationMode == WeatherLocationMode.APPROXIMATE &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_COARSE_LOCATION) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        locationDeniedThisSession = !granted
        weatherRequestedAt = 0L
        updateWeather()
        if (settingsPage != null) renderSettingsPage()
    }

    private fun pickWidget() {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, arrayListOf())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, arrayListOf())
        }
        runCatching { startActivityForResult(intent, REQUEST_PICK_WIDGET) }.onFailure {
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            Toast.makeText(this, "Widget picker is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Platform widget flows still use activity results on API 29")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        when (requestCode) {
            REQUEST_PICK_WIDGET -> {
                if (resultCode != RESULT_OK || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    return
                }
                pendingWidgetId = id
                val info = appWidgetManager.getAppWidgetInfo(id)
                if (info?.configure != null) {
                    runCatching {
                        appWidgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
                    }.onFailure { commitWidget(id) }
                } else commitWidget(id)
            }
            REQUEST_CONFIGURE_WIDGET -> {
                if (resultCode == RESULT_OK) commitWidget(id) else {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }
            }
        }
    }

    private fun commitWidget(id: Int) {
        val placements = loadWidgetPlacements().filterNot { it.appWidgetId == id }.toMutableList()
        placements += WidgetPlacement(id, 160)
        saveWidgetPlacements(placements)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        renderWidgets()
    }

    private fun renderWidgets() {
        if (!::widgetContainer.isInitialized) return
        screenTimeRequestGeneration += 1
        screenTimeRequestInFlight = false
        activeWidgetEditor = null
        clockWidgetEditor = null
        (clockPanel.parent as? ViewGroup)?.removeView(clockPanel)
        widgetContainer.removeAllViews()
        widgetEditors.clear()
        widgetEditorGeometries.clear()
        widgetEditorAutomaticTops.clear()
        screenTimeView = null
        screenUsageView = null
        var automaticTopDp = 0
        if (preferences.showBuiltInClock) {
            val clockGeometry = loadBuiltInWidgetGeometry(
                CLOCK_GEOMETRY_KEY,
                WidgetGeometry(150, yPermille = 0),
            )
            val clockFrame = createWidgetEditorFrame().apply {
                minimumEditorWidthPx = dp(180)
                minimumEditorHeightPx = dp(100)
            }
            clockWidgetEditor = clockFrame
            clockPanel.visibility = View.VISIBLE
            clockFrame.onGeometryCommitted = { geometry ->
                widgetEditorGeometries[clockFrame] = geometry
                saveBuiltInWidgetGeometry(CLOCK_GEOMETRY_KEY, geometry)
                updateClockTypography(geometry.heightDp)
            }
            clockFrame.onRemoveRequested = {
                confirmWidgetRemoval("built-in clock/date") {
                    preferences.showBuiltInClock = false
                    renderWidgets()
                    applyAppearance()
                    Toast.makeText(this, "Clock/date hidden. Restore it in Home screen settings.", Toast.LENGTH_LONG).show()
                }
            }
            clockFrame.setOnLongClickListener {
                enterWidgetEditMode(clockFrame)
                true
            }
            clockFrame.addView(clockPanel, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            addWidgetEditor(clockFrame, clockGeometry, automaticTopDp)
            automaticTopDp += clockGeometry.heightDp + 8
        }
        if (preferences.showScreenTime) {
            val screenTimeBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                isLongClickable = true
            }
            val screenTimeFrame = createWidgetEditorFrame()
            val defaultScreenTimeHeight = if (preferences.showDetailedUsage) 136 else 80
            val storedScreenTimeGeometry = loadBuiltInWidgetGeometry(
                SCREEN_TIME_GEOMETRY_KEY,
                WidgetGeometry(defaultScreenTimeHeight),
            )
            val screenTimeGeometry = storedScreenTimeGeometry.copy(
                heightDp = max(defaultScreenTimeHeight, storedScreenTimeGeometry.heightDp),
            )
            screenTimeFrame.minimumEditorHeightPx = dp(defaultScreenTimeHeight)
            screenTimeFrame.onGeometryCommitted = { geometry ->
                widgetEditorGeometries[screenTimeFrame] = geometry
                saveBuiltInWidgetGeometry(SCREEN_TIME_GEOMETRY_KEY, geometry)
            }
            screenTimeFrame.onRemoveRequested = {
                confirmWidgetRemoval("built-in screen time") {
                    preferences.showScreenTime = false
                    screenTimeRequestGeneration += 1
                    renderWidgets()
                    Toast.makeText(this, "Screen time hidden. Restore it in Home screen settings.", Toast.LENGTH_LONG).show()
                }
            }
            val editScreenTime = View.OnLongClickListener {
                enterWidgetEditMode(screenTimeFrame)
                true
            }
            screenTimeBlock.setOnLongClickListener(editScreenTime)
            screenTimeView = styledText(12f, wallpaperSecondaryColor, mediumTypeface).apply {
                id = R.id.home_screen_time
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                includeFontPadding = false
                letterSpacing = 0.04f
                minHeight = dp(44)
                setPadding(dp(8), 0, dp(8), dp(8))
                contentDescription = "Screen-on time today. Long press to move or resize."
                isLongClickable = true
                setOnLongClickListener(editScreenTime)
            }
            screenUsageView = styledText(10f, wallpaperSecondaryColor, regularTypeface).apply {
                id = R.id.home_detailed_usage
                gravity = Gravity.END
                includeFontPadding = false
                setLineSpacing(0f, 1.15f)
                maxLines = DETAILED_USAGE_APP_LIMIT
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(8), 0, dp(8), dp(8))
                visibility = View.GONE
                isLongClickable = true
                setOnLongClickListener(editScreenTime)
            }
            screenTimeBlock.addView(screenTimeView, LinearLayout.LayoutParams(MATCH, WRAP))
            screenTimeBlock.addView(screenUsageView, LinearLayout.LayoutParams(MATCH, WRAP))
            screenTimeFrame.addView(screenTimeBlock, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            addWidgetEditor(screenTimeFrame, screenTimeGeometry, automaticTopDp)
            automaticTopDp += screenTimeGeometry.heightDp + 8
            updateScreenTime(force = true)
        } else {
            screenTimeRequestGeneration += 1
        }
        val valid = mutableListOf<WidgetPlacement>()
        loadWidgetPlacements().forEach { placement ->
            val id = placement.appWidgetId
            val height = placement.heightDp
            val info = appWidgetManager.getAppWidgetInfo(id) ?: run {
                runCatching { appWidgetHost.deleteAppWidgetId(id) }
                return@forEach
            }
            val frame = createWidgetEditorFrame().apply {
                minimumEditorWidthPx = max(dp(120), info.minWidth)
                minimumEditorHeightPx = max(dp(80), info.minHeight)
            }
            val minimumHeightDp = (frame.minimumEditorHeightPx / resources.displayMetrics.density).toInt()
            val renderedGeometry = placement.geometry.copy(heightDp = max(height, minimumHeightDp)).sanitized()
            val renderedPlacement = placement.withGeometry(renderedGeometry)
            valid += renderedPlacement
            val hostView = appWidgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                setOnLongClickListener {
                    enterWidgetEditMode(frame)
                    true
                }
            }
            frame.setOnLongClickListener {
                enterWidgetEditMode(frame)
                true
            }
            frame.onGeometryCommitted = { geometry ->
                widgetEditorGeometries[frame] = geometry
                val placements = loadWidgetPlacements()
                    .map { current -> if (current.appWidgetId == id) current.withGeometry(geometry) else current }
                saveWidgetPlacements(placements)
                updateAppWidgetSize(id, frame, geometry.heightDp)
            }
            frame.onRemoveRequested = {
                confirmWidgetRemoval(info.loadLabel(packageManager)?.toString().orEmpty().ifBlank { "widget" }) {
                    appWidgetHost.deleteAppWidgetId(id)
                    saveWidgetPlacements(loadWidgetPlacements().filterNot { it.appWidgetId == id })
                    renderWidgets()
                }
            }
            frame.addView(hostView, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            addWidgetEditor(frame, renderedGeometry, automaticTopDp)
            automaticTopDp += renderedGeometry.heightDp + 8
        }
        if (valid != loadWidgetPlacements()) saveWidgetPlacements(valid)
        widgetContainer.post(::applyWidgetGeometries)
    }

    private fun createWidgetEditorFrame(): EditableWidgetFrame = EditableWidgetFrame(this).apply {
        configureEditor(accentColor, primaryColor, mediumTypeface)
        onEditingChanged = { isEditing ->
            if (isEditing) activeWidgetEditor = this
            else if (activeWidgetEditor === this) activeWidgetEditor = null
        }
    }

    private fun addWidgetEditor(frame: EditableWidgetFrame, geometry: WidgetGeometry, automaticTopDp: Int) {
        widgetEditors += frame
        widgetEditorGeometries[frame] = geometry
        widgetEditorAutomaticTops[frame] = dp(automaticTopDp)
        widgetContainer.addView(frame, FrameLayout.LayoutParams(MATCH, dp(geometry.heightDp)))
    }

    private fun applyWidgetGeometries() {
        if (!::widgetContainer.isInitialized || widgetContainer.width <= 0 || widgetContainer.height <= 0) return
        widgetEditors.forEach { frame ->
            val geometry = widgetEditorGeometries[frame] ?: return@forEach
            frame.applyGeometry(geometry, widgetEditorAutomaticTops[frame] ?: 0)
            if (frame === clockWidgetEditor) updateClockTypography(frame.currentGeometry().heightDp)
        }
    }

    private fun updateClockTypography(heightDp: Int) {
        val timeSize = (heightDp * 0.43f).coerceIn(40f, 84f)
        timeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(timeSize))
    }

    private fun enterWidgetEditMode(frame: EditableWidgetFrame) {
        activeWidgetEditor?.takeIf { it !== frame }?.exitEditMode(commit = true)
        frame.enterEditMode()
        val hintKey = if (frame === favoritesEditor) FAVORITES_EDITOR_HINT_SHOWN_KEY else WIDGET_EDITOR_HINT_SHOWN_KEY
        if (!runtimePreferences.getBoolean(hintKey, false)) {
            runtimePreferences.edit().putBoolean(hintKey, true).apply()
            Toast.makeText(this, frame.editingHint(), Toast.LENGTH_LONG).show()
        }
    }

    private fun applyFavoritePosition() {
        if (!::favoritesContainer.isInitialized || favoritesContainer.width <= 0 || favoritesContainer.height <= 0) return
        if (favoritesEditor.visibility != View.VISIBLE) return
        val params = favoritesEditor.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = dp(270).coerceAtMost(favoritesContainer.width)
        params.height = dp((favoritesView.childCount.coerceAtLeast(1)) * 48).coerceAtMost(favoritesContainer.height)
        val position = preferences.favoritesPosition.sanitized()
        params.leftMargin = WidgetGeometryPolicy.pixelsFromPermille(
            position.xPermille,
            (favoritesContainer.width - params.width).coerceAtLeast(0),
        )
        params.topMargin = WidgetGeometryPolicy.pixelsFromPermille(
            position.yPermille,
            (favoritesContainer.height - params.height).coerceAtLeast(0),
        )
        favoritesEditor.layoutParams = params
    }

    private fun confirmWidgetRemoval(label: String, remove: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("remove $label?")
            .setMessage("You can add it again from Home screen settings.")
            .setPositiveButton("remove") { _, _ -> remove() }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun updateAppWidgetSize(id: Int, frame: EditableWidgetFrame, heightDp: Int) {
        val widthDp = (frame.width / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        appWidgetManager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        })
    }

    private fun loadWidgetPlacements(): List<WidgetPlacement> = WidgetPlacementCodec.decode(
        runtimePreferences.getString("widget.placements", "").orEmpty(),
    )

    private fun saveWidgetPlacements(placements: List<WidgetPlacement>) {
        runtimePreferences.edit().putString("widget.placements", WidgetPlacementCodec.encode(placements)).apply()
    }

    private fun loadBuiltInWidgetGeometry(key: String, default: WidgetGeometry): WidgetGeometry =
        WidgetGeometryCodec.decode(runtimePreferences.getString(key, "").orEmpty(), default)

    private fun saveBuiltInWidgetGeometry(key: String, geometry: WidgetGeometry) {
        runtimePreferences.edit().putString(key, WidgetGeometryCodec.encode(geometry)).apply()
    }

    private fun styledText(sizeSp: Float, color: Int, face: Typeface): TextView = TextView(this).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(sizeSp))
        setTextColor(color)
        typeface = face
    }

    private fun scaledSp(base: Float): Float = base * preferences.fontScalePercent / 100f

    private fun launcherText(value: String): String = TextTransformPolicy.apply(
        value,
        preferences.textTransform,
        Locale.getDefault(),
    )

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun formatColor(color: Int): String = LauncherColorPalette.formatHex(color)

    private fun LinearLayout.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) yield(getChildAt(index))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private inner class AppListAdapter : BaseAdapter() {
        override fun getCount(): Int = visibleApps.size
        override fun getItem(position: Int): AppEntry = visibleApps[position]
        override fun getItemId(position: Int): Long = visibleApps[position].stableId.hashCode().toLong()
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(14f))
                setTextColor(primaryColor)
                typeface = mediumTypeface
                minHeight = dp(48)
                setPadding(dp(8), 0, dp(8), 0)
                isFocusable = false
                setBackgroundColor(Color.TRANSPARENT)
            }
            val app = getItem(position)
            val label = AppPresentationPolicy.drawerLabel(
                app,
                showStockProfileMarker = currentFilter.builtIn == DrawerFilter.STOCK,
            )
            view.text = launcherText(label)
            view.contentDescription = "Open $label${if (app.isWorkProfile) ", work profile" else ""}"
            return view
        }
    }

    private companion object {
        const val USER_PREFERENCES = "launcher_prefs"
        const val RUNTIME_PREFERENCES = "launcher_runtime"
        const val APP_WIDGET_HOST_ID = 0x4D4C
        const val REQUEST_PICK_WIDGET = 1001
        const val REQUEST_CONFIGURE_WIDGET = 1002
        const val REQUEST_COARSE_LOCATION = 1003
        const val ACTION_OPEN_APPS = 0x01020001
        const val STATE_DRAWER_OPEN = "drawer.open"
        const val STATE_FILTER = "drawer.filter"
        const val STATE_QUERY = "drawer.query"
        const val STATE_SETTINGS_PAGE = "settings.page"
        const val CATALOG_CACHE_KEY = "catalog.entries"
        const val CLOCK_GEOMETRY_KEY = "widget.builtin.clock.geometry"
        const val SCREEN_TIME_GEOMETRY_KEY = "widget.builtin.screen_time.geometry"
        const val WIDGET_EDITOR_HINT_SHOWN_KEY = "widget.editor.hint_shown"
        const val FAVORITES_EDITOR_HINT_SHOWN_KEY = "favorites.editor.hint_shown"
        const val IME_SHOW_DELAY_MS = 120L
        const val DRAWER_TRANSITION_DISTANCE_DP = 28
        const val DRAWER_TRANSITION_OUT_MS = 130L
        const val DRAWER_TRANSITION_IN_MS = 180L
        const val FILTER_TRANSITION_DISTANCE_DP = 32
        const val FILTER_TRANSITION_OUT_MS = 70L
        const val FILTER_TRANSITION_IN_MS = 110L
        const val FILTER_TRANSITION_DIM_ALPHA = 0.18f
        const val WEATHER_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
        const val SCREEN_TIME_REFRESH_INTERVAL_MS = 30_000L
        const val DETAILED_USAGE_APP_LIMIT = 4
        const val SETTINGS_BACKGROUND_COLOR = 0xFF0B0B0D.toInt()
        const val SETTINGS_PRIMARY_COLOR = 0xFFF4F4F2.toInt()
        const val SETTINGS_SECONDARY_COLOR = 0xFFA0A09A.toInt()
        const val SETTINGS_ACCENT_COLOR = 0xFFB7F36B.toInt()
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
