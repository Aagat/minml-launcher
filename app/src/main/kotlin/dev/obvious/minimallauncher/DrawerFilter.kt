package dev.obvious.minimallauncher

enum class DrawerFilter {
    ALL,
    DAILY,
    WORK,
    MEDIA,
    STOCK;

    val displayName: String get() = when (this) {
        ALL -> "Personal"
        STOCK -> "Stock"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun cycle(step: Int): DrawerFilter {
        val filters = entries.filter { it != STOCK }
        val current = filters.indexOf(this).takeIf { it >= 0 } ?: 0
        return filters[(current + step).mod(filters.size)]
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
    fun available(customFilters: List<CustomFilter>, separateStockApps: Boolean = false): List<FilterSpec> =
        DrawerFilter.entries
            .filter { it != DrawerFilter.STOCK || separateStockApps }
            .map(FilterSpec::builtIn) + customFilters.map(FilterSpec::custom)

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
        separateStockApps: Boolean = false,
    ): List<AppEntry> = when (filter.builtIn) {
        DrawerFilter.ALL -> apps.filter { !it.isWorkProfile && (!separateStockApps || !it.isStockApp) }
        DrawerFilter.WORK -> apps.filter { it.isWorkProfile && (!separateStockApps || !it.isStockApp) }
        DrawerFilter.STOCK -> apps.filter { it.isStockApp }
        DrawerFilter.DAILY, DrawerFilter.MEDIA, null -> {
            val ids = membership
            apps.filter { !it.isWorkProfile && it.stableId in ids }
        }
    }
}
