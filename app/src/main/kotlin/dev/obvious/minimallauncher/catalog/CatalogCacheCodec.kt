package dev.obvious.minimallauncher.catalog

import dev.obvious.minimallauncher.preferences.PreferenceCodec

object CatalogCacheCodec {
    fun encode(apps: List<AppEntry>): String = PreferenceCodec.encode(apps.map { app ->
        PreferenceCodec.encode(
            listOf(
                app.stableId,
                app.label,
                app.packageName,
                app.className,
                app.userSerial.toString(),
                app.isWorkProfile.toString(),
                app.isMedia.toString(),
            ),
        )
    })

    fun decode(encoded: String): List<AppEntry> = PreferenceCodec.decode(encoded).mapNotNull { record ->
        val values = PreferenceCodec.decode(record)
        if (values.size != 7) return@mapNotNull null
        val serial = values[4].toLongOrNull() ?: return@mapNotNull null
        val work = values[5].toBooleanStrictOrNull() ?: return@mapNotNull null
        val media = values[6].toBooleanStrictOrNull() ?: return@mapNotNull null
        AppEntry(values[0], values[1], values[2], values[3], serial, work, media)
    }.distinctBy { it.stableId }
}
