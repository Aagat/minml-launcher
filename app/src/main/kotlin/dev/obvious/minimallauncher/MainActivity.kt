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
import android.text.TextWatcher
import android.text.format.DateFormat
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
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
    private lateinit var homeRolePrompt: Button
    private lateinit var favoritesView: LinearLayout
    private lateinit var widgetContainer: LinearLayout
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

    private lateinit var regularTypeface: Typeface
    private lateinit var mediumTypeface: Typeface
    private lateinit var preferences: LauncherPreferences
    private lateinit var runtimePreferences: android.content.SharedPreferences
    private lateinit var catalog: AppCatalog
    private lateinit var weatherRepository: WeatherRepository
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

    private val wallpaperColorsChangedListener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, _: Int ->
        if (::preferences.isInitialized && preferences.appearance == Appearance.AUTO && ::contrastOverlay.isInitialized) {
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

        regularTypeface = resources.getFont(R.font.geist_mono_nerd_regular)
        mediumTypeface = resources.getFont(R.font.geist_mono_nerd_medium)
        preferences = LauncherPreferences(
            SharedPreferenceBackend(getSharedPreferences(USER_PREFERENCES, MODE_PRIVATE)),
        )
        runtimePreferences = getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE)
        weatherRepository = WeatherRepository(runtimePreferences)
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
        if (restoredDrawer) openDrawer(seed = restoredQuery)

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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEARCH) {
            openDrawer(seed = intent.getStringExtra(SearchManager.QUERY).orEmpty())
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) && drawerOpen) {
            closeDrawer()
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
        coarseLocationResolver.cancel()
        runCatching { wallpaperManager.removeOnColorsChangedListener(wallpaperColorsChangedListener) }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DRAWER_OPEN, drawerOpen)
        outState.putString(STATE_FILTER, currentFilter.id)
        outState.putString(STATE_QUERY, searchInput.text?.toString().orEmpty())
        super.onSaveInstanceState(outState)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
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

    private fun buildHome() {
        home = HomeGestureLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Minimal Launcher Home"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            onSwipeUp = { openDrawer() }
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
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(16), dp(10), dp(16), dp(12))
            contentDescription = "Built-in clock and date. Long press to hide."
            isLongClickable = true
            setOnLongClickListener {
                showBuiltInClockActions()
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
            isAllCaps = true
        }
        weatherView = styledText(10f, wallpaperSecondaryColor, regularTypeface).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(8), 0, 0)
        }
        clockPanel.addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP))
        home.addView(clockPanel, FrameLayout.LayoutParams(MATCH, WRAP).apply {
            gravity = Gravity.TOP
            setMargins(dp(20), dp(14), dp(12), 0)
        })

        homeRolePrompt = Button(this).apply {
            text = getString(R.string.not_default_home_switch)
            contentDescription = "Minimal Launcher is not the default Home app. Switch Home app."
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

        widgetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            clipChildren = false
        }
        home.addView(widgetContainer, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            gravity = Gravity.TOP
            setMargins(dp(20), dp(205), dp(20), dp(300))
        })

        favoritesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        home.addView(favoritesView, FrameLayout.LayoutParams(dp(270), WRAP).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            setMargins(0, 0, dp(12), dp(92))
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildDrawer() {
        drawer = FilterGestureLayout(this).apply {
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
            id = View.generateViewId()
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
                visibleApps.getOrNull(position)?.let(::openAppDetails)
                true
            }
            setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: android.widget.AbsListView?, scrollState: Int) = Unit
                override fun onScroll(view: android.widget.AbsListView?, first: Int, visible: Int, total: Int) {
                    updateScrollThumb(first, visible, total)
                }
            })
        }
        drawer.addView(appList, FrameLayout.LayoutParams(dp(300), dp(300)).apply { gravity = Gravity.END })

        emptyState = styledText(12f, secondaryColor, regularTypeface).apply {
            text = getString(R.string.no_matching_apps)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            gravity = Gravity.END or Gravity.TOP
            setPadding(0, dp(32), dp(22), 0)
        }
        drawer.addView(emptyState, FrameLayout.LayoutParams(dp(300), dp(120)).apply { gravity = Gravity.END })
        appList.emptyView = emptyState

        drawerBottomSurface = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(Color.BLACK)
        }
        drawer.addView(drawerBottomSurface, FrameLayout.LayoutParams(MATCH, dp(102)).apply { gravity = Gravity.BOTTOM })

        drawerFade = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0x33000000, 0xB3000000.toInt(), Color.BLACK),
            )
        }
        drawer.addView(drawerFade, FrameLayout.LayoutParams(MATCH, dp(56)).apply { gravity = Gravity.BOTTOM })

        drawerHeader = styledText(8f, primaryColor, mediumTypeface).apply {
            id = View.generateViewId()
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
            id = View.generateViewId()
            hint = "search"
            contentDescription = getString(R.string.search_apps)
            setHintTextColor(primaryColor)
            setTextColor(primaryColor)
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
                text = filter.displayName
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
                setOnClickListener { setFilter(filter) }
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
            gravity = Gravity.END or Gravity.BOTTOM
            rightMargin = right + dp(12)
            bottomMargin = dp(6)
        }
        scrollTrack.layoutParams = (scrollTrack.layoutParams as FrameLayout.LayoutParams).apply {
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = listTop
            rightMargin = right + dp(2)
        }
        updateScrollThumb(appList.firstVisiblePosition, appList.childCount, adapter.count)
    }

    private fun updateScrollThumb(first: Int, visible: Int, total: Int) {
        // ListView dispatches an initial scroll callback synchronously when the
        // listener is attached, before the custom indicator views are added.
        if (!::scrollTrack.isInitialized || !::scrollThumb.isInitialized) return
        val trackParams = scrollTrack.layoutParams as? FrameLayout.LayoutParams ?: return
        val trackHeight = trackParams.height
        if (total <= visible || total == 0 || trackHeight <= 0) {
            scrollTrack.visibility = View.GONE
            scrollThumb.visibility = View.GONE
            return
        }
        scrollTrack.visibility = View.VISIBLE
        scrollThumb.visibility = View.VISIBLE
        val thumbHeight = max(dp(30), trackHeight * visible / total)
        val maxOffset = max(0, trackHeight - thumbHeight)
        val offset = if (total == visible) 0 else maxOffset * first / max(1, total - visible)
        scrollThumb.layoutParams = (scrollThumb.layoutParams as FrameLayout.LayoutParams).apply {
            height = thumbHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = trackParams.topMargin + offset
            rightMargin = trackParams.rightMargin - dp(1)
        }
    }

    private fun onCatalogChanged(apps: List<AppEntry>) {
        if (apps.isEmpty() && allApps.isNotEmpty()) return
        allApps = apps
        runtimePreferences.edit().putString(CATALOG_CACHE_KEY, CatalogCacheCodec.encode(apps)).apply()
        initializeClassifications()
        reconcileFavorites()
        renderFavorites()
        renderDrawer()
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

    private fun availableFilters(): List<FilterSpec> = FilterCatalog.available(preferences.customFilters)

    private fun membership(filter: FilterSpec): Set<String> = when (val builtIn = filter.builtIn) {
        DrawerFilter.ALL, DrawerFilter.WORK -> emptySet()
        DrawerFilter.DAILY, DrawerFilter.MEDIA -> preferences.membership(builtIn)
        null -> preferences.customMembership(filter.id)
    }

    private fun renderFavorites() {
        favoritesView.removeAllViews()
        val byId = allApps.associateBy { it.stableId }
        preferences.favorites.mapNotNull(byId::get).take(6).forEachIndexed { index, app ->
            favoritesView.addView(Button(this).apply {
                text = app.label.lowercase(Locale.getDefault())
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
    }

    private fun renderDrawer() {
        if (!::searchInput.isInitialized) return
        val scoped = FilterEngine.apply(allApps, currentFilter, membership(currentFilter))
        visibleApps = AppSearch.rank(scoped, searchInput.text?.toString().orEmpty())
        drawerHeader.text = getString(
            R.string.drawer_filter_count,
            currentFilter.displayName.lowercase(Locale.getDefault()),
            scoped.size,
        )
        drawerHeader.setTextColor(accentColor)
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
        appList.post { updateScrollThumb(appList.firstVisiblePosition, appList.childCount, adapter.count) }
    }

    private fun setFilter(filter: FilterSpec) {
        if (currentFilter.id == filter.id) return
        currentFilter = filter
        appList.setSelection(0)
        renderDrawer()
        filtersView.announceForAccessibility("${filter.displayName} filter")
    }

    private fun cycleFilter(step: Int) = setFilter(FilterCatalog.cycle(availableFilters(), currentFilter.id, step))

    private fun openDrawer(seed: String = "") {
        drawerOpen = true
        home.visibility = View.GONE
        drawer.visibility = View.VISIBLE
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

    private fun closeDrawer() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        searchInput.setText("")
        currentFilter = FilterSpec.builtIn(DrawerFilter.ALL)
        drawerOpen = false
        drawer.visibility = View.GONE
        home.visibility = View.VISIBLE
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
        if (event.action == KeyEvent.ACTION_DOWN && !drawerOpen && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
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
        val timePattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "hh:mm"
        timeView.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEddMMM")
        dateView.text = SimpleDateFormat(datePattern, Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        updateWeather()
    }

    private fun adaptHomeForWindow() {
        if (!::timeView.isInitialized || root.width == 0 || root.height == 0) return
        val landscape = root.width > root.height
        timeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(if (landscape) 44f else 64f))
        (favoritesView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = dp(if (landscape) 12 else 92)
            favoritesView.layoutParams = params
        }
        (widgetContainer.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.topMargin = dp(
                if (!preferences.showBuiltInClock) 20
                else if (landscape) 135
                else 205,
            )
            params.bottomMargin = dp(if (landscape) 120 else 300)
            widgetContainer.layoutParams = params
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
                weatherView.text = getString(R.string.weather_location_denied)
            } else renderWeatherCoordinateDecision(decision)
            return
        }
        if (System.currentTimeMillis() - weatherRequestedAt < WEATHER_REFRESH_INTERVAL_MS || locationRequestInFlight) return
        locationRequestInFlight = true
        weatherView.text = getString(R.string.weather_loading)
        coarseLocationResolver.resolve { approximate ->
            locationRequestInFlight = false
            if (!preferences.weatherEnabled || preferences.weatherLocationMode != WeatherLocationMode.APPROXIMATE) return@resolve
            renderWeatherCoordinateDecision(
                WeatherLocationPolicy.decide(WeatherLocationMode.APPROXIMATE, true, approximate, manualWeatherCoordinates()),
            )
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
            WeatherCoordinateDecision.PermissionRequired -> weatherView.text = getString(R.string.weather_location_permission_required)
            WeatherCoordinateDecision.LocationUnavailable -> weatherView.text = getString(R.string.weather_location_unavailable)
            WeatherCoordinateDecision.ManualLocationRequired -> weatherView.text = getString(R.string.weather_set_manual_location)
        }
    }

    private fun loadWeather(coordinates: WeatherCoordinates) {
        if (System.currentTimeMillis() - weatherRequestedAt < WEATHER_REFRESH_INTERVAL_MS) return
        weatherRequestedAt = System.currentTimeMillis()
        weatherView.text = getString(R.string.weather_loading)
        weatherRepository.load(coordinates.latitude, coordinates.longitude) { result ->
            handler.post {
                if (!preferences.weatherEnabled) return@post
                weatherView.text = when (result) {
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
                }
            }
        }
    }

    private fun applyAppearance() {
        val appearance = preferences.appearance
        root.setBackgroundColor(
            if (appearance == Appearance.SOLID) preferences.solidBackgroundColor else Color.TRANSPARENT,
        )
        val autoDecision = if (appearance == Appearance.AUTO) {
            ContrastPolicy.decide(systemWallpaperPrimaryColor(), primaryColor)
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
        val drawerSurfaceColor = if (appearance == Appearance.SOLID) preferences.solidBackgroundColor else Color.BLACK
        drawerBottomSurface.setBackgroundColor(drawerSurfaceColor)
        drawerFade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.TRANSPARENT,
                withAlpha(drawerSurfaceColor, 0x33),
                withAlpha(drawerSurfaceColor, 0xB3),
                drawerSurfaceColor,
            ),
        )
        val localizedDecision = if (appearance == Appearance.AUTO) {
            ContrastPolicy.localizedFallback(decision, primaryColor)
        } else decision
        clockPanel.background = localizedClockScrimColors(localizedDecision)?.let { colors ->
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = dp(8).toFloat() }
        }
        dateView.setTextColor(if (localizedDecision.tone == ScrimTone.NONE) secondaryColor else wallpaperSecondaryColor)
        clockPanel.visibility = if (preferences.showBuiltInClock) View.VISIBLE else View.GONE
        root.post(::adaptHomeForWindow)
    }

    private fun showBuiltInClockActions() {
        AlertDialog.Builder(this)
            .setTitle("built-in clock/date")
            .setItems(arrayOf("hide")) { _, _ ->
                preferences.showBuiltInClock = false
                applyAppearance()
                Toast.makeText(this, "Clock/date hidden. Restore it in Customization.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("cancel", null)
            .show()
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
        val items = arrayOf(
            "add widget · system",
            "favorites",
            "filters",
            "customization",
            "drawer dismissal · ${preferences.drawerDismissDistanceSensitivity}% / ${preferences.drawerDismissSpeedSensitivity}%",
            "weather · ${if (preferences.weatherEnabled) "on" else "off"}",
            "permissions · system",
            "app details · system",
        )
        AlertDialog.Builder(this)
            .setTitle("launcher settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickWidget()
                    1 -> showFavoriteEditor()
                    2 -> showFilterEditor()
                    3 -> showCustomizationMenu()
                    4 -> showDismissSensitivityEditor()
                    5 -> showWeatherEditor()
                    6, 7 -> openAppDetails()
                }
            }
            .setNegativeButton("close", null)
            .show()
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
        val choices = Appearance.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("appearance")
            .setSingleChoiceItems(choices, preferences.appearance.ordinal) { dialog, which ->
                preferences.appearance = Appearance.entries[which]
                applyAppearance()
                dialog.dismiss()
            }
            .show()
    }

    private fun showCustomizationMenu() {
        val items = arrayOf(
            "font size · ${preferences.fontScalePercent}%",
            "font color · ${formatColor(preferences.fontColor)}",
            "accent color · ${formatColor(preferences.accentColor)}",
            "appearance · ${preferences.appearance.name.lowercase()}",
            "solid background · ${formatColor(preferences.solidBackgroundColor)}",
            "built-in clock/date · ${if (preferences.showBuiltInClock) "shown" else "hidden"}",
            "keyboard on drawer · ${if (preferences.autoShowKeyboard) "on" else "off"}",
            "filter labels · ${if (preferences.showFilterBar) "shown" else "hidden"}",
            "bottom gradient · ${if (preferences.showDrawerGradient) "on" else "off"}",
            "search underline · ${if (preferences.showSearchUnderline) "on" else "off"}",
            "app list margins · ${preferences.appListTopMarginDp} / ${preferences.appListRightMarginDp} dp",
            "status bar · ${if (preferences.hideStatusBar) "hidden" else "shown"}",
        )
        AlertDialog.Builder(this)
            .setTitle("customization")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFontSizeEditor()
                    1 -> showColorEditor(accent = false)
                    2 -> showColorEditor(accent = true)
                    3 -> showAppearanceEditor()
                    4 -> showSolidBackgroundColorEditor()
                    5 -> {
                        preferences.showBuiltInClock = !preferences.showBuiltInClock
                        applyAppearance()
                    }
                    6 -> {
                        preferences.autoShowKeyboard = !preferences.autoShowKeyboard
                        Toast.makeText(
                            this,
                            "Automatic keyboard ${if (preferences.autoShowKeyboard) "enabled" else "disabled"}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    7 -> {
                        preferences.showFilterBar = !preferences.showFilterBar
                        recreate()
                    }
                    8 -> {
                        preferences.showDrawerGradient = !preferences.showDrawerGradient
                        recreate()
                    }
                    9 -> {
                        preferences.showSearchUnderline = !preferences.showSearchUnderline
                        recreate()
                    }
                    10 -> showAppListMarginsEditor()
                    11 -> {
                        preferences.hideStatusBar = !preferences.hideStatusBar
                        recreate()
                    }
                }
            }
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
                recreate()
            }
            .setNegativeButton("cancel", null)
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

    private fun showColorEditor(accent: Boolean) {
        val input = EditText(this).apply {
            hint = "#RRGGBB"
            setSingleLine(true)
            setText(formatColor(if (accent) preferences.accentColor else preferences.fontColor))
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle(if (accent) "accent color" else "font color")
            .setView(input)
            .setPositiveButton("save") { _, _ ->
                val parsed = runCatching { Color.parseColor(input.text.toString().trim()) }.getOrNull()
                if (parsed == null) {
                    Toast.makeText(this, "Use a color such as #B7F36B", Toast.LENGTH_SHORT).show()
                } else {
                    val opaque = parsed or 0xFF000000.toInt()
                    if (accent) preferences.accentColor = opaque else preferences.fontColor = opaque
                    recreate()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showSolidBackgroundColorEditor() {
        val input = EditText(this).apply {
            hint = "#RRGGBB"
            setSingleLine(true)
            setText(formatColor(preferences.solidBackgroundColor))
            setSelection(length())
        }
        AlertDialog.Builder(this)
            .setTitle("solid background color")
            .setView(input)
            .setPositiveButton("save") { _, _ ->
                val parsed = runCatching { Color.parseColor(input.text.toString().trim()) }.getOrNull()
                if (parsed == null) {
                    Toast.makeText(this, "Use a color such as #101416", Toast.LENGTH_SHORT).show()
                } else {
                    preferences.solidBackgroundColor = parsed or 0xFF000000.toInt()
                    preferences.appearance = Appearance.SOLID
                    recreate()
                }
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
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showFavoriteActions(index: Int, app: AppEntry) {
        val actions = arrayOf("move up", "move down", "remove")
        AlertDialog.Builder(this)
            .setTitle(settingsAppLabel(app))
            .setItems(actions) { _, which ->
                val favorites = preferences.favorites.toMutableList()
                val actualIndex = favorites.indexOf(app.stableId).takeIf { it >= 0 } ?: index
                when (which) {
                    0 -> if (actualIndex > 0) favorites[actualIndex] = favorites[actualIndex - 1].also { favorites[actualIndex - 1] = favorites[actualIndex] }
                    1 -> if (actualIndex < favorites.lastIndex) favorites[actualIndex] = favorites[actualIndex + 1].also { favorites[actualIndex + 1] = favorites[actualIndex] }
                    2 -> favorites.remove(app.stableId)
                }
                preferences.favorites = favorites
                renderFavorites()
            }
            .show()
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
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun settingsAppLabel(app: AppEntry): String = app.label + if (app.isWorkProfile) " (w)" else ""

    private fun showWeatherEditor() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val enabled = CheckBox(this).apply {
            text = getString(R.string.enable_open_meteo_weather)
            isChecked = preferences.weatherEnabled
        }
        val disclosure = styledText(11f, secondaryColor, regularTypeface).apply {
            setPadding(0, dp(8), 0, dp(8))
        }
        val locationMode = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val manualMode = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.weather_manual_location)
        }
        val approximateMode = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.weather_approximate_location)
        }
        locationMode.addView(manualMode)
        locationMode.addView(approximateMode)
        locationMode.check(
            if (preferences.weatherLocationMode == WeatherLocationMode.APPROXIMATE) approximateMode.id else manualMode.id,
        )
        fun updateDisclosure() {
            disclosure.text = getString(
                if (locationMode.checkedRadioButtonId == approximateMode.id) {
                    R.string.weather_location_disclosure
                } else {
                    R.string.weather_manual_disclosure
                },
            )
        }
        locationMode.setOnCheckedChangeListener { _, _ -> updateDisclosure() }
        updateDisclosure()
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
        form.addView(enabled)
        form.addView(locationMode)
        form.addView(disclosure)
        form.addView(latitude)
        form.addView(longitude)
        AlertDialog.Builder(this)
            .setTitle("weather")
            .setView(form)
            .setPositiveButton("save") { _, _ ->
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                val mode = if (locationMode.checkedRadioButtonId == approximateMode.id) {
                    WeatherLocationMode.APPROXIMATE
                } else {
                    WeatherLocationMode.MANUAL
                }
                val manualProvided = latitude.text.isNotBlank() || longitude.text.isNotBlank()
                val manualValid = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                if (enabled.isChecked && (mode == WeatherLocationMode.MANUAL || manualProvided) && !manualValid) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                } else {
                    preferences.weatherEnabled = enabled.isChecked
                    preferences.weatherLocationMode = mode
                    preferences.weatherLatitude = latitude.text.toString().trim()
                    preferences.weatherLongitude = longitude.text.toString().trim()
                    weatherRequestedAt = 0L
                    locationDeniedThisSession = false
                    coarseLocationResolver.cancel()
                    locationRequestInFlight = false
                    if (enabled.isChecked && mode == WeatherLocationMode.APPROXIMATE &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
                    }
                    updateWeather()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_COARSE_LOCATION) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        locationDeniedThisSession = !granted
        weatherRequestedAt = 0L
        updateWeather()
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
        widgetContainer.removeAllViews()
        val valid = mutableListOf<WidgetPlacement>()
        loadWidgetPlacements().forEach { placement ->
            val id = placement.appWidgetId
            val height = placement.heightDp
            val info = appWidgetManager.getAppWidgetInfo(id) ?: run {
                runCatching { appWidgetHost.deleteAppWidgetId(id) }
                return@forEach
            }
            valid += WidgetPlacement(id, height)
            val hostView = appWidgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                setOnLongClickListener {
                    showWidgetActions(id)
                    true
                }
            }
            widgetContainer.addView(hostView, LinearLayout.LayoutParams(MATCH, dp(height)).apply {
                bottomMargin = dp(8)
            })
        }
        if (valid != loadWidgetPlacements()) saveWidgetPlacements(valid)
    }

    private fun showWidgetActions(id: Int) {
        val items = arrayOf(
            "compact · 100 dp",
            "standard · 160 dp",
            "tall · 240 dp",
            "move up",
            "move down",
            "remove",
        )
        AlertDialog.Builder(this)
            .setTitle("widget")
            .setItems(items) { _, which ->
                val placements = loadWidgetPlacements().toMutableList()
                val index = placements.indexOfFirst { it.appWidgetId == id }
                when (which) {
                    in 0..2 -> {
                        val height = intArrayOf(100, 160, 240)[which]
                        saveWidgetPlacements(placements.map { if (it.appWidgetId == id) WidgetPlacement(id, height) else it })
                        val availableWidthDp = (widgetContainer.width / resources.displayMetrics.density).toInt().coerceAtLeast(1)
                        appWidgetManager.updateAppWidgetOptions(id, Bundle().apply {
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, availableWidthDp)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, availableWidthDp)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                        })
                    }
                    3 -> if (index > 0) {
                        placements[index] = placements[index - 1].also { placements[index - 1] = placements[index] }
                        saveWidgetPlacements(placements)
                    }
                    4 -> if (index >= 0 && index < placements.lastIndex) {
                        placements[index] = placements[index + 1].also { placements[index + 1] = placements[index] }
                        saveWidgetPlacements(placements)
                    }
                    5 -> {
                        appWidgetHost.deleteAppWidgetId(id)
                        saveWidgetPlacements(placements.filterNot { it.appWidgetId == id })
                    }
                }
                renderWidgets()
            }
            .show()
    }

    private fun loadWidgetPlacements(): List<WidgetPlacement> = WidgetPlacementCodec.decode(
        runtimePreferences.getString("widget.placements", "").orEmpty(),
    )

    private fun saveWidgetPlacements(placements: List<WidgetPlacement>) {
        runtimePreferences.edit().putString("widget.placements", WidgetPlacementCodec.encode(placements)).apply()
    }

    private fun styledText(sizeSp: Float, color: Int, face: Typeface): TextView = TextView(this).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(sizeSp))
        setTextColor(color)
        typeface = face
    }

    private fun scaledSp(base: Float): Float = base * preferences.fontScalePercent / 100f

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun formatColor(color: Int): String = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

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
            view.text = app.label.lowercase(Locale.getDefault())
            view.contentDescription = "Open ${app.label}${if (app.isWorkProfile) ", work profile" else ""}"
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
        const val CATALOG_CACHE_KEY = "catalog.entries"
        const val IME_SHOW_DELAY_MS = 120L
        const val WEATHER_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
