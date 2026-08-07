package dev.obvious.minimallauncher

enum class DrawerFilter {
    ALL,
    DAILY,
    WORK,
    MEDIA;

    val displayName: String get() = name.lowercase()

    fun cycle(step: Int): DrawerFilter {
        val filters = entries
        return filters[(ordinal + step).mod(filters.size)]
    }
}

object FilterEngine {
    fun apply(
        apps: List<AppEntry>,
        filter: DrawerFilter,
        memberships: Map<DrawerFilter, Set<String>>,
    ): List<AppEntry> = when (filter) {
        DrawerFilter.ALL -> apps.filterNot { it.isWorkProfile }
        DrawerFilter.WORK -> apps.filter { it.isWorkProfile }
        DrawerFilter.DAILY, DrawerFilter.MEDIA -> {
            val ids = memberships[filter].orEmpty()
            apps.filter { !it.isWorkProfile && it.stableId in ids }
        }
    }
}
