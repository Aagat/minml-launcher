package dev.obvious.minimallauncher

import android.content.SharedPreferences

enum class Appearance { AUTO, TRANSPARENT, GRADIENT }

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

    var drawerDismissSensitivity: Int
        get() = backend.getString(KEY_DRAWER_DISMISS_SENSITIVITY, DEFAULT_DRAWER_DISMISS_SENSITIVITY.toString())
            .toIntOrNull()
            ?.coerceIn(0, 100)
            ?: DEFAULT_DRAWER_DISMISS_SENSITIVITY
        set(value) = backend.putString(KEY_DRAWER_DISMISS_SENSITIVITY, value.coerceIn(0, 100).toString())

    fun membership(filter: DrawerFilter): Set<String> =
        PreferenceCodec.decode(backend.getString(filterKey(filter))).toSet()

    fun setMembership(filter: DrawerFilter, ids: Collection<String>) {
        require(filter != DrawerFilter.ALL)
        backend.putString(filterKey(filter), PreferenceCodec.encode(ids.distinct().sorted()))
        backend.putBoolean(initializedKey(filter), true)
    }

    fun isMembershipInitialized(filter: DrawerFilter): Boolean =
        filter == DrawerFilter.ALL || backend.getBoolean(initializedKey(filter))

    private fun filterKey(filter: DrawerFilter) = "filter.${filter.name.lowercase()}.members"
    private fun initializedKey(filter: DrawerFilter) = "filter.${filter.name.lowercase()}.initialized"

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_WEATHER_ENABLED = "weather.enabled"
        const val KEY_WEATHER_LATITUDE = "weather.latitude"
        const val KEY_WEATHER_LONGITUDE = "weather.longitude"
        const val KEY_DRAWER_DISMISS_SENSITIVITY = "drawer.dismiss.sensitivity"
        const val DEFAULT_DRAWER_DISMISS_SENSITIVITY = 65
    }
}
