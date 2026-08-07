package dev.obvious.minimallauncher

data class WidgetPlacement(val appWidgetId: Int, val heightDp: Int)

object WidgetPlacementCodec {
    fun encode(placements: List<WidgetPlacement>): String = placements
        .distinctBy { it.appWidgetId }
        .joinToString(";") { "${it.appWidgetId}:${it.heightDp.coerceIn(80, 320)}" }

    fun decode(encoded: String): List<WidgetPlacement> = encoded
        .split(';')
        .mapNotNull { value ->
            val parts = value.split(':')
            val id = parts.getOrNull(0)?.toIntOrNull()
            val height = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && id != null && id > 0 && height != null) {
                WidgetPlacement(id, height.coerceIn(80, 320))
            } else null
        }
        .distinctBy { it.appWidgetId }
}
