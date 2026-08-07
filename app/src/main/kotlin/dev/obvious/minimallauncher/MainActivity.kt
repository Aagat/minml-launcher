package dev.obvious.minimallauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private lateinit var favoritesView: LinearLayout
    private lateinit var widgetContainer: LinearLayout
    private lateinit var drawerHeader: TextView
    private lateinit var appList: ListView
    private lateinit var emptyState: TextView
    private lateinit var filtersView: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var scrollTrack: View
    private lateinit var scrollThumb: View

    private lateinit var regularTypeface: Typeface
    private lateinit var mediumTypeface: Typeface
    private lateinit var preferences: LauncherPreferences
    private lateinit var runtimePreferences: android.content.SharedPreferences
    private lateinit var catalog: AppCatalog
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = AppListAdapter()
    private var allApps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var currentFilter = DrawerFilter.ALL
    private var drawerOpen = false
    private var imeVisible = false
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var weatherRequestedAt = 0L

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
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APP_WIDGET_HOST_ID)

        buildUi()
        setContentView(root)
        applyAppearance()
        restoreCachedCatalog()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::handleBack)
        }

        currentFilter = savedInstanceState?.getString(STATE_FILTER)
            ?.let { runCatching { DrawerFilter.valueOf(it) }.getOrNull() }
            ?: DrawerFilter.ALL
        val restoredDrawer = savedInstanceState?.getBoolean(STATE_DRAWER_OPEN, false) == true
        val restoredQuery = savedInstanceState?.getString(STATE_QUERY).orEmpty()
        if (restoredDrawer) openDrawer(focusSearch = false, seed = restoredQuery)

        catalog = AppCatalog(this, ::onCatalogChanged)
        catalog.start()
        if (intent.action == Intent.ACTION_SEARCH) {
            openDrawer(focusSearch = true, seed = intent.getStringExtra(SearchManager.QUERY).orEmpty())
        }
        updateClock()
        handler.post(clockTicker)
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
        renderWidgets()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEARCH) {
            openDrawer(focusSearch = true, seed = intent.getStringExtra(SearchManager.QUERY).orEmpty())
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
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DRAWER_OPEN, drawerOpen)
        outState.putString(STATE_FILTER, currentFilter.name)
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
            drawer.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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
                        openDrawer(focusSearch = true)
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
        }
        timeView = styledText(64f, PRIMARY, mediumTypeface).apply {
            includeFontPadding = false
            letterSpacing = -0.06f
            isSingleLine = true
        }
        dateView = styledText(10f, SECONDARY, mediumTypeface).apply {
            letterSpacing = 0.08f
            isAllCaps = true
        }
        weatherView = styledText(10f, WALLPAPER_SECONDARY, regularTypeface).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        clockPanel.addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(dateView, LinearLayout.LayoutParams(WRAP, WRAP))
        clockPanel.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP))
        home.addView(clockPanel, FrameLayout.LayoutParams(MATCH, WRAP).apply {
            gravity = Gravity.TOP
            setMargins(dp(20), dp(14), dp(12), 0)
        })

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

    private fun buildDrawer() {
        drawer = FilterGestureLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "App drawer"
            onFilterSwipe = { cycleFilter(it) }
        }

        appList = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            setSelector(android.R.color.transparent)
            isVerticalScrollBarEnabled = false
            choiceMode = ListView.CHOICE_MODE_NONE
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

        emptyState = styledText(12f, SECONDARY, regularTypeface).apply {
            text = "no matching apps"
            gravity = Gravity.END or Gravity.TOP
            setPadding(0, dp(32), dp(22), 0)
        }
        drawer.addView(emptyState, FrameLayout.LayoutParams(dp(300), dp(120)).apply { gravity = Gravity.END })
        appList.emptyView = emptyState

        val fade = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0x55000000, 0xE6000000.toInt(), Color.BLACK),
            )
        }
        drawer.addView(fade, FrameLayout.LayoutParams(MATCH, dp(270)).apply { gravity = Gravity.BOTTOM })

        drawerHeader = styledText(8f, PRIMARY, mediumTypeface).apply {
            gravity = Gravity.END
            letterSpacing = 0.08f
        }
        drawer.addView(drawerHeader, FrameLayout.LayoutParams(dp(300), dp(24)).apply { gravity = Gravity.END })

        scrollTrack = View(this).apply { setBackgroundColor(0x6620201F) }
        scrollThumb = View(this).apply { setBackgroundColor(ACCENT) }
        drawer.addView(scrollTrack, FrameLayout.LayoutParams(dp(1), dp(200)).apply { gravity = Gravity.END })
        drawer.addView(scrollThumb, FrameLayout.LayoutParams(dp(3), dp(36)).apply { gravity = Gravity.END })

        filtersView = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        DrawerFilter.entries.forEach { filter ->
            filtersView.addView(Button(this).apply {
                tag = filter
                text = filter.displayName
                contentDescription = "${filter.displayName} apps filter"
                isAllCaps = false
                typeface = regularTypeface
                textSize = 9f
                minHeight = dp(48)
                minWidth = dp(48)
                minimumHeight = dp(48)
                minimumWidth = dp(48)
                setPadding(dp(4), 0, dp(4), 0)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { setFilter(filter) }
            }, LinearLayout.LayoutParams(WRAP, dp(48)))
        }
        drawer.addView(filtersView, FrameLayout.LayoutParams(dp(300), dp(48)).apply { gravity = Gravity.END or Gravity.BOTTOM })

        val searchFrame = FrameLayout(this)
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(styledText(12f, ACCENT, mediumTypeface).apply {
            text = ">"
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(36), MATCH))
        searchInput = EditText(this).apply {
            hint = "search"
            contentDescription = getString(R.string.search_apps)
            setHintTextColor(PRIMARY)
            setTextColor(PRIMARY)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
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
        searchFrame.addView(View(this).apply { setBackgroundColor(ACCENT) }, FrameLayout.LayoutParams(MATCH, dp(1)).apply { gravity = Gravity.BOTTOM })
        drawer.addView(searchFrame, FrameLayout.LayoutParams(dp(300), dp(48)).apply { gravity = Gravity.END or Gravity.BOTTOM })
    }

    private fun positionDrawerChildren() {
        if (drawer.width == 0 || drawer.height == 0) return
        val availableHeight = drawer.height - drawer.paddingTop - drawer.paddingBottom
        val availableWidth = drawer.width - drawer.paddingLeft - drawer.paddingRight
        val columnWidth = min(dp(300), max(dp(220), availableWidth - dp(32)))
        val listTop = if (availableHeight < dp(650)) dp(16) else (availableHeight * 0.33f).toInt()
        val controlsHeight = dp(112)
        val listHeight = max(dp(120), availableHeight - listTop - controlsHeight)
        val right = drawer.paddingRight + dp(12)
        val absoluteTop = drawer.paddingTop + listTop

        appList.layoutParams = (appList.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = absoluteTop
            rightMargin = right
        }
        emptyState.layoutParams = (emptyState.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = absoluteTop
            rightMargin = right
        }
        drawerHeader.layoutParams = (drawerHeader.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            height = dp(24)
            gravity = Gravity.END or Gravity.TOP
            topMargin = max(drawer.paddingTop, absoluteTop - dp(26))
            rightMargin = right + dp(14)
        }
        filtersView.layoutParams = (filtersView.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            gravity = Gravity.END or Gravity.BOTTOM
            rightMargin = right
            bottomMargin = drawer.paddingBottom + dp(54)
        }
        val searchFrame = searchInput.parent.parent as View
        searchFrame.layoutParams = (searchFrame.layoutParams as FrameLayout.LayoutParams).apply {
            width = columnWidth
            gravity = Gravity.END or Gravity.BOTTOM
            rightMargin = right
            bottomMargin = drawer.paddingBottom + dp(6)
        }
        scrollTrack.layoutParams = (scrollTrack.layoutParams as FrameLayout.LayoutParams).apply {
            height = listHeight
            gravity = Gravity.END or Gravity.TOP
            topMargin = absoluteTop
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

    private fun memberships(): Map<DrawerFilter, Set<String>> = DrawerFilter.entries.associateWith { filter ->
        if (filter == DrawerFilter.ALL) allApps.mapTo(mutableSetOf()) { it.stableId } else preferences.membership(filter)
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
                textSize = 16f
                setTextColor(PRIMARY)
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
        val scoped = FilterEngine.apply(allApps, currentFilter, memberships())
        visibleApps = AppSearch.rank(scoped, searchInput.text?.toString().orEmpty())
        drawerHeader.text = if (scoped.size >= 24) "apps / ${scoped.size}" else "apps"
        adapter.notifyDataSetChanged()
        filtersView.children().forEach { button ->
            val active = button.tag == currentFilter
            (button as TextView).setTextColor(if (active) ACCENT else SECONDARY)
            button.isSelected = active
            button.contentDescription = "${(button.tag as DrawerFilter).displayName} apps filter${if (active) ", selected" else ""}"
        }
        appList.post { updateScrollThumb(appList.firstVisiblePosition, appList.childCount, adapter.count) }
    }

    private fun setFilter(filter: DrawerFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        appList.setSelection(0)
        renderDrawer()
        filtersView.announceForAccessibility("${filter.displayName} filter")
    }

    private fun cycleFilter(step: Int) = setFilter(currentFilter.cycle(step))

    private fun openDrawer(focusSearch: Boolean = false, seed: String = "") {
        drawerOpen = true
        home.visibility = View.GONE
        drawer.visibility = View.VISIBLE
        if (seed.isNotEmpty()) searchInput.setText(seed)
        renderDrawer()
        drawer.post {
            positionDrawerChildren()
            if (focusSearch) {
                searchInput.requestFocus()
                searchInput.setSelection(searchInput.length())
                getSystemService(InputMethodManager::class.java).showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            } else {
                drawer.requestFocus()
            }
        }
    }

    private fun closeDrawer() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        searchInput.setText("")
        currentFilter = DrawerFilter.ALL
        drawerOpen = false
        drawer.visibility = View.GONE
        home.visibility = View.VISIBLE
        home.requestFocus()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Handled by OnBackInvokedDispatcher on Android 13+")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        if (drawerOpen && imeVisible) {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(searchInput.windowToken, 0)
            searchInput.clearFocus()
            drawer.requestFocus()
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
                openDrawer(focusSearch = true, seed = String(Character.toChars(unicode)))
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && drawerOpen && event.keyCode == KeyEvent.KEYCODE_ENTER && !searchInput.hasFocus()) {
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
        timeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (landscape) 44f else 64f)
        (favoritesView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = dp(if (landscape) 12 else 92)
            favoritesView.layoutParams = params
        }
        (widgetContainer.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.topMargin = dp(if (landscape) 135 else 205)
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
            return
        }
        val latitude = preferences.weatherLatitude.toDoubleOrNull()
        val longitude = preferences.weatherLongitude.toDoubleOrNull()
        weatherView.visibility = View.VISIBLE
        if (latitude == null || longitude == null) {
            weatherView.text = "weather · set manual location"
            return
        }
        if (System.currentTimeMillis() - weatherRequestedAt < 60 * 60 * 1000L) return
        weatherRequestedAt = System.currentTimeMillis()
        weatherView.text = "weather · loading"
        weatherRepository.load(latitude, longitude) { result ->
            handler.post {
                if (!preferences.weatherEnabled) return@post
                weatherView.text = when (result) {
                    is WeatherResult.Available -> with(result.snapshot) {
                        "$temperature°$unit  •  $condition  ·  H$high  L$low${if (result.stale) "  stale" else ""}"
                    }
                    is WeatherResult.Unavailable -> result.message
                }
            }
        }
    }

    private fun applyAppearance() {
        val appearance = preferences.appearance
        val overlayColors = when (appearance) {
            Appearance.TRANSPARENT -> intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
            Appearance.AUTO -> intArrayOf(0x22000000, 0x33000000, 0x99000000.toInt())
            Appearance.GRADIENT -> intArrayOf(0x55000000, 0x77000000, 0xBB000000.toInt())
        }
        contrastOverlay.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, overlayColors)
        clockPanel.background = if (appearance == Appearance.TRANSPARENT) null else GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xD9000000.toInt(), 0x99000000.toInt(), 0x33000000),
        ).apply { cornerRadius = dp(8).toFloat() }
        dateView.setTextColor(if (appearance == Appearance.TRANSPARENT) SECONDARY else WALLPAPER_SECONDARY)
    }

    private fun showLauncherSettings() {
        val items = arrayOf(
            "default home · system",
            "add widget · system",
            "favorites",
            "filters",
            "appearance · ${preferences.appearance.name.lowercase()}",
            "weather · ${if (preferences.weatherEnabled) "on" else "off"}",
            "permissions · system",
            "app details · system",
        )
        AlertDialog.Builder(this)
            .setTitle("launcher settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestHomeRole()
                    1 -> pickWidget()
                    2 -> showFavoriteEditor()
                    3 -> showFilterEditor()
                    4 -> showAppearanceEditor()
                    5 -> showWeatherEditor()
                    6, 7 -> openAppDetails()
                }
            }
            .setNegativeButton("close", null)
            .show()
    }

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        val intent = if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Default Home settings are unavailable", Toast.LENGTH_SHORT).show()
        }
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

    private fun showFavoriteEditor() {
        if (allApps.isEmpty()) return
        val selected = preferences.favorites.toMutableSet()
        val labels = allApps.map { it.label }.toTypedArray()
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
            .setTitle(app.label)
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
        val editable = arrayOf(DrawerFilter.DAILY, DrawerFilter.WORK, DrawerFilter.MEDIA)
        AlertDialog.Builder(this)
            .setTitle("edit filter")
            .setItems(editable.map { it.displayName }.toTypedArray()) { _, which -> showMembershipEditor(editable[which]) }
            .show()
    }

    private fun showMembershipEditor(filter: DrawerFilter) {
        val selected = preferences.membership(filter).toMutableSet()
        val checked = BooleanArray(allApps.size) { allApps[it].stableId in selected }
        AlertDialog.Builder(this)
            .setTitle("${filter.displayName} apps")
            .setMultiChoiceItems(allApps.map { it.label }.toTypedArray(), checked) { _, which, enabled ->
                if (enabled) selected += allApps[which].stableId else selected -= allApps[which].stableId
            }
            .setPositiveButton("save") { _, _ ->
                preferences.setMembership(filter, selected)
                renderDrawer()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showWeatherEditor() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val enabled = CheckBox(this).apply {
            text = "Enable Open-Meteo weather"
            isChecked = preferences.weatherEnabled
        }
        val disclosure = styledText(11f, SECONDARY, regularTypeface).apply {
            text = "Manual coordinates are sent to Open-Meteo. Location permission is not required."
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
        form.addView(enabled)
        form.addView(disclosure)
        form.addView(latitude)
        form.addView(longitude)
        AlertDialog.Builder(this)
            .setTitle("weather")
            .setView(form)
            .setPositiveButton("save") { _, _ ->
                val lat = latitude.text.toString().toDoubleOrNull()
                val lon = longitude.text.toString().toDoubleOrNull()
                if (enabled.isChecked && (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0)) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_LONG).show()
                } else {
                    preferences.weatherEnabled = enabled.isChecked
                    preferences.weatherLatitude = latitude.text.toString().trim()
                    preferences.weatherLongitude = longitude.text.toString().trim()
                    weatherRequestedAt = 0L
                    updateWeather()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
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
        val placements = loadWidgetPlacements().filterNot { it.first == id }.toMutableList()
        placements += id to 160
        saveWidgetPlacements(placements)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        renderWidgets()
    }

    private fun renderWidgets() {
        if (!::widgetContainer.isInitialized) return
        widgetContainer.removeAllViews()
        val valid = mutableListOf<Pair<Int, Int>>()
        loadWidgetPlacements().forEach { (id, height) ->
            val info = appWidgetManager.getAppWidgetInfo(id) ?: run {
                runCatching { appWidgetHost.deleteAppWidgetId(id) }
                return@forEach
            }
            valid += id to height
            val hostView = appWidgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                setOnLongClickListener {
                    showWidgetActions(id, height)
                    true
                }
            }
            widgetContainer.addView(hostView, LinearLayout.LayoutParams(MATCH, dp(height)).apply {
                bottomMargin = dp(8)
            })
        }
        if (valid != loadWidgetPlacements()) saveWidgetPlacements(valid)
    }

    private fun showWidgetActions(id: Int, currentHeight: Int) {
        val items = arrayOf("compact · 100 dp", "standard · 160 dp", "tall · 240 dp", "remove")
        AlertDialog.Builder(this)
            .setTitle("widget")
            .setItems(items) { _, which ->
                if (which == 3) {
                    appWidgetHost.deleteAppWidgetId(id)
                    saveWidgetPlacements(loadWidgetPlacements().filterNot { it.first == id })
                } else {
                    val height = intArrayOf(100, 160, 240)[which]
                    saveWidgetPlacements(loadWidgetPlacements().map { if (it.first == id) id to height else it })
                    appWidgetManager.updateAppWidgetOptions(id, Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                    })
                }
                renderWidgets()
            }
            .show()
    }

    private fun loadWidgetPlacements(): List<Pair<Int, Int>> = runtimePreferences
        .getString("widget.placements", "")
        .orEmpty()
        .split(';')
        .mapNotNull { value ->
            val parts = value.split(':')
            val id = parts.getOrNull(0)?.toIntOrNull()
            val height = parts.getOrNull(1)?.toIntOrNull()
            if (id != null && height != null) id to height.coerceIn(80, 320) else null
        }

    private fun saveWidgetPlacements(placements: List<Pair<Int, Int>>) {
        runtimePreferences.edit().putString("widget.placements", placements.joinToString(";") { "${it.first}:${it.second}" }).apply()
    }

    private fun styledText(sizeSp: Float, color: Int, face: Typeface): TextView = TextView(this).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = face
    }

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
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(PRIMARY)
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
        const val ACTION_OPEN_APPS = 0x01020001
        const val STATE_DRAWER_OPEN = "drawer.open"
        const val STATE_FILTER = "drawer.filter"
        const val STATE_QUERY = "drawer.query"
        const val CATALOG_CACHE_KEY = "catalog.entries"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val PRIMARY = 0xFFF4F4F2.toInt()
        const val SECONDARY = 0xFF7D7D7A.toInt()
        const val WALLPAPER_SECONDARY = 0xFFD8D8D8.toInt()
        const val ACCENT = 0xFFB7F36B.toInt()
    }
}
