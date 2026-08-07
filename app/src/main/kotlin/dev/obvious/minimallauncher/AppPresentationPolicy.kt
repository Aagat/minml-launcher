package dev.obvious.minimallauncher

object AppPresentationPolicy {
    fun visibleCatalog(
        apps: List<AppEntry>,
        hiddenIds: Set<String>,
        aliases: Map<String, String>,
    ): List<AppEntry> = apps.asSequence()
        .filterNot { it.stableId in hiddenIds }
        .map { app -> presented(app, aliases) }
        .toList()

    fun presented(app: AppEntry, aliases: Map<String, String>): AppEntry {
        val alias = aliases[app.stableId]?.trim().orEmpty()
        return if (alias.isEmpty() || alias == app.label) app else app.copy(label = alias)
    }
}
