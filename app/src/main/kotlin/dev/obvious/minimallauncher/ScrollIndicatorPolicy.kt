package dev.obvious.minimallauncher

import kotlin.math.max

data class ScrollThumbGeometry(
    val height: Int,
    val offset: Float,
)

object ScrollIndicatorPolicy {
    fun calculate(
        totalItems: Int,
        firstVisibleItem: Int,
        firstChildTop: Int,
        rowHeight: Int,
        viewportHeight: Int,
        trackHeight: Int,
        minimumThumbHeight: Int,
    ): ScrollThumbGeometry? {
        if (totalItems <= 0 || rowHeight <= 0 || viewportHeight <= 0 || trackHeight <= 0) return null
        val visibleRows = viewportHeight.toFloat() / rowHeight
        if (totalItems <= visibleRows) return null

        val thumbHeight = max(
            minimumThumbHeight,
            (trackHeight * visibleRows / totalItems).toInt(),
        ).coerceAtMost(trackHeight)
        val scrollableRows = totalItems - visibleRows
        val scrolledRows = (firstVisibleItem - firstChildTop.toFloat() / rowHeight)
            .coerceIn(0f, scrollableRows)
        val offset = (trackHeight - thumbHeight) * scrolledRows / scrollableRows
        return ScrollThumbGeometry(thumbHeight, offset)
    }
}
