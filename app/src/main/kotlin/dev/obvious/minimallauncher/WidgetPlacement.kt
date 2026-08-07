package dev.obvious.minimallauncher

import kotlin.math.roundToInt

data class WidgetGeometry(
    val heightDp: Int,
    val xPermille: Int = 0,
    val yPermille: Int = AUTO_POSITION,
    val widthPermille: Int = FULL_WIDTH,
) {
    fun sanitized(): WidgetGeometry = copy(
        heightDp = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        xPermille = xPermille.coerceIn(0, POSITION_SCALE),
        yPermille = if (yPermille == AUTO_POSITION) AUTO_POSITION else yPermille.coerceIn(0, POSITION_SCALE),
        widthPermille = widthPermille.coerceIn(MIN_WIDTH_PERMILLE, FULL_WIDTH),
    )

    companion object {
        const val AUTO_POSITION = -1
        const val POSITION_SCALE = 1000
        const val FULL_WIDTH = POSITION_SCALE
        const val MIN_WIDTH_PERMILLE = 250
        const val MIN_HEIGHT_DP = 80
        const val MAX_HEIGHT_DP = 640
    }
}

data class WidgetPlacement(
    val appWidgetId: Int,
    val heightDp: Int,
    val xPermille: Int = 0,
    val yPermille: Int = WidgetGeometry.AUTO_POSITION,
    val widthPermille: Int = WidgetGeometry.FULL_WIDTH,
) {
    val geometry: WidgetGeometry
        get() = WidgetGeometry(heightDp, xPermille, yPermille, widthPermille).sanitized()

    fun withGeometry(value: WidgetGeometry): WidgetPlacement {
        val safe = value.sanitized()
        return copy(
            heightDp = safe.heightDp,
            xPermille = safe.xPermille,
            yPermille = safe.yPermille,
            widthPermille = safe.widthPermille,
        )
    }
}

object WidgetGeometryPolicy {
    fun pixelsFromPermille(permille: Int, availablePixels: Int): Int {
        if (availablePixels <= 0) return 0
        return (availablePixels * permille.coerceIn(0, WidgetGeometry.POSITION_SCALE) /
            WidgetGeometry.POSITION_SCALE.toFloat()).roundToInt()
    }

    fun permilleFromPixels(pixels: Int, availablePixels: Int): Int {
        if (availablePixels <= 0) return 0
        return (pixels.coerceIn(0, availablePixels) * WidgetGeometry.POSITION_SCALE /
            availablePixels.toFloat()).roundToInt().coerceIn(0, WidgetGeometry.POSITION_SCALE)
    }

    fun widthPixels(widthPermille: Int, parentWidthPixels: Int, minimumWidthPixels: Int): Int {
        if (parentWidthPixels <= 0) return minimumWidthPixels.coerceAtLeast(0)
        val requested = (parentWidthPixels * widthPermille.coerceIn(
            WidgetGeometry.MIN_WIDTH_PERMILLE,
            WidgetGeometry.FULL_WIDTH,
        ) / WidgetGeometry.POSITION_SCALE.toFloat()).roundToInt()
        return requested.coerceIn(minimumWidthPixels.coerceAtMost(parentWidthPixels), parentWidthPixels)
    }

    fun widthPermille(widthPixels: Int, parentWidthPixels: Int): Int {
        if (parentWidthPixels <= 0) return WidgetGeometry.FULL_WIDTH
        return (widthPixels.coerceIn(0, parentWidthPixels) * WidgetGeometry.POSITION_SCALE /
            parentWidthPixels.toFloat()).roundToInt().coerceIn(
            WidgetGeometry.MIN_WIDTH_PERMILLE,
            WidgetGeometry.FULL_WIDTH,
        )
    }
}

object WidgetPlacementCodec {
    fun encode(placements: List<WidgetPlacement>): String = placements
        .distinctBy { it.appWidgetId }
        .filter { it.appWidgetId > 0 }
        .joinToString(";") { placement ->
            val safe = placement.geometry
            listOf(
                placement.appWidgetId,
                safe.heightDp,
                safe.xPermille,
                safe.yPermille,
                safe.widthPermille,
            ).joinToString(":")
        }

    fun decode(encoded: String): List<WidgetPlacement> = encoded
        .split(';')
        .mapNotNull(::decodePlacement)
        .distinctBy { it.appWidgetId }

    private fun decodePlacement(value: String): WidgetPlacement? {
        val parts = value.split(':')
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val height = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (id <= 0) return null
        if (parts.size == 2) {
            return WidgetPlacement(id, height.coerceIn(WidgetGeometry.MIN_HEIGHT_DP, WidgetGeometry.MAX_HEIGHT_DP))
        }
        if (parts.size != 5) return null
        val x = parts[2].toIntOrNull() ?: return null
        val y = parts[3].toIntOrNull() ?: return null
        val width = parts[4].toIntOrNull() ?: return null
        return WidgetPlacement(id, height, x, y, width).withGeometry(
            WidgetGeometry(height, x, y, width),
        )
    }
}

object WidgetGeometryCodec {
    fun encode(geometry: WidgetGeometry): String = geometry.sanitized().let {
        "${it.heightDp}:${it.xPermille}:${it.yPermille}:${it.widthPermille}"
    }

    fun decode(encoded: String, default: WidgetGeometry): WidgetGeometry {
        val parts = encoded.split(':')
        if (parts.size != 4) return default.sanitized()
        val height = parts[0].toIntOrNull() ?: return default.sanitized()
        val x = parts[1].toIntOrNull() ?: return default.sanitized()
        val y = parts[2].toIntOrNull() ?: return default.sanitized()
        val width = parts[3].toIntOrNull() ?: return default.sanitized()
        return WidgetGeometry(height, x, y, width).sanitized()
    }
}
