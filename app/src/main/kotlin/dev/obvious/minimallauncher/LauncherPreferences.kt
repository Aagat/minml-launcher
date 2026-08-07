package dev.obvious.minimallauncher

import android.content.SharedPreferences

enum class Appearance { AUTO, TRANSPARENT, GRADIENT, SOLID }
enum class WeatherLocationMode { MANUAL, APPROXIMATE }

interface PreferenceBackend {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class SharedPreferenceBackend(private val preferences: SharedPreferences) : PreferenceBackend {
    override fun getString(key: String, default: String): String = preferences.getString(key, default) ?: default
    override fun putString(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply() }
}

object PreferenceCodec {
    fun encode(values: List<String>): String = buildString {
        values.forEach { value -> append(value.length).append(':').append(value) }
    }

    fun decode(encoded: String): List<String> {
        if (encoded.isEmpty()) return emptyList()
        val values = mutableListOf<String>()
        var cursor = 0
        while (cursor < encoded.length) {
            val separator = encoded.indexOf(':', cursor)
            if (separator <= cursor) return emptyList()
            val length = encoded.substring(cursor, separator).toIntOrNull() ?: return emptyList()
            val start = separator + 1
            val end = start + length
            if (length < 0 || end > encoded.length) return emptyList()
            values += encoded.substring(start, end)
            cursor = end
        }
        return values
    }
}

class LauncherPreferences(private val backend: PreferenceBackend) {
    var favorites: List<String>
        get() = PreferenceCodec.decode(backend.getString(KEY_FAVORITES))
        set(value) = backend.putString(KEY_FAVORITES, PreferenceCodec.encode(value.distinct()))

    var appearance: Appearance
        get() = runCatching { Appearance.valueOf(backend.getString(KEY_APPEARANCE, Appearance.AUTO.name)) }
            .getOrDefault(Appearance.AUTO)
        set(value) = backend.putString(KEY_APPEARANCE, value.name)

    var weatherEnabled: Boolean
        get() = backend.getBoolean(KEY_WEATHER_ENABLED)
        set(value) = backend.putBoolean(KEY_WEATHER_ENABLED, value)

    var weatherLatitude: String
        get() = backend.getString(KEY_WEATHER_LATITUDE)
        set(value) = backend.putString(KEY_WEATHER_LATITUDE, value)

    var weatherLongitude: String
        get() = backend.getString(KEY_WEATHER_LONGITUDE)
        set(value) = backend.putString(KEY_WEATHER_LONGITUDE, value)

    var weatherLocationMode: WeatherLocationMode
        get() = runCatching {
            WeatherLocationMode.valueOf(backend.getString(KEY_WEATHER_LOCATION_MODE, WeatherLocationMode.MANUAL.name))
        }.getOrDefault(WeatherLocationMode.MANUAL)
        set(value) = backend.putString(KEY_WEATHER_LOCATION_MODE, value.name)

    var drawerDismissDistanceSensitivity: Int
        get() = sensitivity(KEY_DRAWER_DISMISS_DISTANCE_SENSITIVITY)
        set(value) = backend.putString(KEY_DRAWER_DISMISS_DISTANCE_SENSITIVITY, value.coerceIn(0, 100).toString())

    var drawerDismissSpeedSensitivity: Int
        get() = sensitivity(KEY_DRAWER_DISMISS_SPEED_SENSITIVITY)
        set(value) = backend.putString(KEY_DRAWER_DISMISS_SPEED_SENSITIVITY, value.coerceIn(0, 100).toString())

    var customFilters: List<CustomFilter>
        get() = PreferenceCodec.decode(backend.getString(KEY_CUSTOM_FILTERS)).mapNotNull { encoded ->
            val values = PreferenceCodec.decode(encoded)
            val id = values.getOrNull(0).orEmpty()
            val name = values.getOrNull(1).orEmpty().trim()
            if (values.size == 2 && id.startsWith("custom:") && name.isNotEmpty()) CustomFilter(id, name) else null
        }.distinctBy { it.id }
        set(value) = backend.putString(
            KEY_CUSTOM_FILTERS,
            PreferenceCodec.encode(value.distinctBy { it.id }.map { PreferenceCodec.encode(listOf(it.id, it.name.trim())) }),
        )

    var fontScalePercent: Int
        get() = backend.getString(KEY_FONT_SCALE, "100").toIntOrNull()?.coerceIn(75, 150) ?: 100
        set(value) = backend.putString(KEY_FONT_SCALE, value.coerceIn(75, 150).toString())

    var fontColor: Int
        get() = storedColor(KEY_FONT_COLOR, DEFAULT_FONT_COLOR)
        set(value) = backend.putString(KEY_FONT_COLOR, encodeColor(value))

    var accentColor: Int
        get() = storedColor(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR)
        set(value) = backend.putString(KEY_ACCENT_COLOR, encodeColor(value))

    var solidBackgroundColor: Int
        get() = storedColor(KEY_SOLID_BACKGROUND_COLOR, DEFAULT_SOLID_BACKGROUND_COLOR)
        set(value) = backend.putString(KEY_SOLID_BACKGROUND_COLOR, encodeColor(value))

    var showBuiltInClock: Boolean
        get() = backend.getBoolean(KEY_SHOW_BUILT_IN_CLOCK, true)
        set(value) = backend.putBoolean(KEY_SHOW_BUILT_IN_CLOCK, value)

    var autoShowKeyboard: Boolean
        get() = backend.getBoolean(KEY_AUTO_SHOW_KEYBOARD, true)
        set(value) = backend.putBoolean(KEY_AUTO_SHOW_KEYBOARD, value)

    var showFilterBar: Boolean
        get() = backend.getBoolean(KEY_SHOW_FILTER_BAR, true)
        set(value) = backend.putBoolean(KEY_SHOW_FILTER_BAR, value)

    var showDrawerGradient: Boolean
        get() = backend.getBoolean(KEY_SHOW_DRAWER_GRADIENT, true)
        set(value) = backend.putBoolean(KEY_SHOW_DRAWER_GRADIENT, value)

    var showSearchUnderline: Boolean
        get() = backend.getBoolean(KEY_SHOW_SEARCH_UNDERLINE, true)
        set(value) = backend.putBoolean(KEY_SHOW_SEARCH_UNDERLINE, value)

    var appListTopMarginDp: Int
        get() = storedInt(KEY_APP_LIST_TOP_MARGIN, DEFAULT_APP_LIST_TOP_MARGIN, 24, 96)
        set(value) = backend.putString(KEY_APP_LIST_TOP_MARGIN, value.coerceIn(24, 96).toString())

    var appListRightMarginDp: Int
        get() = storedInt(KEY_APP_LIST_RIGHT_MARGIN, DEFAULT_APP_LIST_RIGHT_MARGIN, 0, 64)
        set(value) = backend.putString(KEY_APP_LIST_RIGHT_MARGIN, value.coerceIn(0, 64).toString())

    var hideStatusBar: Boolean
        get() = backend.getBoolean(KEY_HIDE_STATUS_BAR)
        set(value) = backend.putBoolean(KEY_HIDE_STATUS_BAR, value)

    fun membership(filter: DrawerFilter): Set<String> =
        PreferenceCodec.decode(backend.getString(filterKey(filter))).toSet()

    fun setMembership(filter: DrawerFilter, ids: Collection<String>) {
        require(filter != DrawerFilter.ALL)
        backend.putString(filterKey(filter), PreferenceCodec.encode(ids.distinct().sorted()))
        backend.putBoolean(initializedKey(filter), true)
    }

    fun isMembershipInitialized(filter: DrawerFilter): Boolean =
        filter == DrawerFilter.ALL || backend.getBoolean(initializedKey(filter))

    fun customMembership(filterId: String): Set<String> =
        PreferenceCodec.decode(backend.getString(customFilterKey(filterId))).toSet()

    fun setCustomMembership(filterId: String, ids: Collection<String>) {
        require(filterId.startsWith("custom:"))
        backend.putString(customFilterKey(filterId), PreferenceCodec.encode(ids.distinct().sorted()))
    }

    private fun filterKey(filter: DrawerFilter) = "filter.${filter.name.lowercase()}.members"
    private fun initializedKey(filter: DrawerFilter) = "filter.${filter.name.lowercase()}.initialized"
    private fun customFilterKey(filterId: String) = "filter.custom.${filterId.removePrefix("custom:")}.members"
    private fun sensitivity(key: String): Int {
        val legacy = backend.getString(KEY_DRAWER_DISMISS_SENSITIVITY, DEFAULT_DRAWER_DISMISS_SENSITIVITY.toString())
        return backend.getString(key, legacy).toIntOrNull()?.coerceIn(0, 100) ?: DEFAULT_DRAWER_DISMISS_SENSITIVITY
    }
    private fun storedColor(key: String, default: Int): Int = backend.getString(key, encodeColor(default))
        .toLongOrNull(16)
        ?.toInt()
        ?: default
    private fun encodeColor(color: Int): String = (color.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
    private fun storedInt(key: String, default: Int, minimum: Int, maximum: Int): Int =
        backend.getString(key, default.toString()).toIntOrNull()?.coerceIn(minimum, maximum) ?: default

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_WEATHER_ENABLED = "weather.enabled"
        const val KEY_WEATHER_LATITUDE = "weather.latitude"
        const val KEY_WEATHER_LONGITUDE = "weather.longitude"
        const val KEY_WEATHER_LOCATION_MODE = "weather.location_mode"
        const val KEY_DRAWER_DISMISS_SENSITIVITY = "drawer.dismiss.sensitivity"
        const val KEY_DRAWER_DISMISS_DISTANCE_SENSITIVITY = "drawer.dismiss.distance_sensitivity"
        const val KEY_DRAWER_DISMISS_SPEED_SENSITIVITY = "drawer.dismiss.speed_sensitivity"
        const val DEFAULT_DRAWER_DISMISS_SENSITIVITY = 65
        const val KEY_CUSTOM_FILTERS = "filter.custom.categories"
        const val KEY_FONT_SCALE = "customization.font_scale"
        const val KEY_FONT_COLOR = "customization.font_color"
        const val KEY_ACCENT_COLOR = "customization.accent_color"
        const val KEY_SOLID_BACKGROUND_COLOR = "customization.solid_background_color"
        const val KEY_SHOW_BUILT_IN_CLOCK = "customization.show_built_in_clock"
        const val KEY_AUTO_SHOW_KEYBOARD = "customization.auto_show_keyboard"
        const val KEY_SHOW_FILTER_BAR = "customization.show_filter_bar"
        const val KEY_SHOW_DRAWER_GRADIENT = "customization.show_drawer_gradient"
        const val KEY_SHOW_SEARCH_UNDERLINE = "customization.show_search_underline"
        const val KEY_APP_LIST_TOP_MARGIN = "customization.app_list_top_margin"
        const val KEY_APP_LIST_RIGHT_MARGIN = "customization.app_list_right_margin"
        const val KEY_HIDE_STATUS_BAR = "customization.hide_status_bar"
        const val DEFAULT_FONT_COLOR = 0xFFF4F4F2.toInt()
        const val DEFAULT_ACCENT_COLOR = 0xFFB7F36B.toInt()
        const val DEFAULT_SOLID_BACKGROUND_COLOR = 0xFF000000.toInt()
        const val DEFAULT_APP_LIST_TOP_MARGIN = 24
        const val DEFAULT_APP_LIST_RIGHT_MARGIN = 20
    }
}
