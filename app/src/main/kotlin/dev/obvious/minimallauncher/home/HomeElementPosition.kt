package dev.obvious.minimallauncher.home

data class HomeElementPosition(
    val xPermille: Int = END,
    val yPermille: Int = END,
) {
    fun sanitized(): HomeElementPosition = copy(
        xPermille = xPermille.coerceIn(START, END),
        yPermille = yPermille.coerceIn(START, END),
    )

    companion object {
        const val START = 0
        const val END = 1000
        val DEFAULT = HomeElementPosition()
    }
}

object HomeElementPositionCodec {
    fun encode(position: HomeElementPosition): String = position.sanitized().let {
        "${it.xPermille}:${it.yPermille}"
    }

    fun decode(encoded: String, default: HomeElementPosition = HomeElementPosition.DEFAULT): HomeElementPosition {
        val parts = encoded.split(':')
        if (parts.size != 2) return default.sanitized()
        val x = parts[0].toIntOrNull() ?: return default.sanitized()
        val y = parts[1].toIntOrNull() ?: return default.sanitized()
        return HomeElementPosition(x, y).sanitized()
    }
}
