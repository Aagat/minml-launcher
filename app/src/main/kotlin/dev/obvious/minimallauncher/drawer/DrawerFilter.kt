package dev.obvious.minimallauncher.drawer

import dev.obvious.minimallauncher.catalog.AppEntry

enum class DrawerFilter {
    ALL,
    DAILY,
    WORK,
    MEDIA;

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    fun cycle(step: Int): DrawerFilter {
        val filters = entries
        return filters[(ordinal + step).mod(filters.size)]
    }
}

data class CustomFilter(val id: String, val name: String)

data class FilterSpec(
    val id: String,
    val displayName: String,
    val builtIn: DrawerFilter? = null,
) {
    companion object {
        fun builtIn(filter: DrawerFilter) = FilterSpec("builtin:${filter.name.lowercase()}", filter.displayName, filter)
        fun custom(filter: CustomFilter) = FilterSpec(filter.id, filter.name, null)
    }
}

object FilterCatalog {
    fun available(customFilters: List<CustomFilter>): List<FilterSpec> =
        DrawerFilter.entries.map(FilterSpec::builtIn) + customFilters.map(FilterSpec::custom)

    fun cycle(filters: List<FilterSpec>, currentId: String, step: Int): FilterSpec {
        require(filters.isNotEmpty())
        val current = filters.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        return filters[(current + step).mod(filters.size)]
    }
}

data class DrawerHeaderContent(val text: String, val accentEnd: Int)

object DrawerHeaderPolicy {
    fun content(filterName: String, count: Int): DrawerHeaderContent {
        return DrawerHeaderContent("$filterName/$count", filterName.length)
    }
}

object FilterEngine {
    fun apply(
        apps: List<AppEntry>,
        filter: FilterSpec,
        membership: Set<String>,
    ): List<AppEntry> = when (filter.builtIn) {
        DrawerFilter.ALL -> apps.filterNot { it.isWorkProfile }
        DrawerFilter.WORK -> apps.filter { it.isWorkProfile }
        DrawerFilter.DAILY, DrawerFilter.MEDIA, null -> {
            val ids = membership
            apps.filter { !it.isWorkProfile && it.stableId in ids }
        }
    }
}
