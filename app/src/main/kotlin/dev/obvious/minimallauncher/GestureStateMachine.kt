package dev.obvious.minimallauncher

import kotlin.math.abs

enum class GestureDecision {
    UNDECIDED,
    HORIZONTAL_LEFT,
    HORIZONTAL_RIGHT,
    VERTICAL_UP,
    VERTICAL_DOWN,
}

class GestureStateMachine(
    private val threshold: Float,
    private val dominance: Float = 1.15f,
) {
    private var startX = 0f
    private var startY = 0f
    var decision: GestureDecision = GestureDecision.UNDECIDED
        private set

    fun begin(x: Float, y: Float) {
        startX = x
        startY = y
        decision = GestureDecision.UNDECIDED
    }

    fun update(x: Float, y: Float): GestureDecision {
        if (decision != GestureDecision.UNDECIDED) return decision
        val dx = x - startX
        val dy = y - startY
        val absX = abs(dx)
        val absY = abs(dy)
        if (absX < threshold && absY < threshold) return decision
        decision = when {
            absX > absY * dominance -> if (dx < 0) GestureDecision.HORIZONTAL_LEFT else GestureDecision.HORIZONTAL_RIGHT
            absY > absX * dominance -> if (dy < 0) GestureDecision.VERTICAL_UP else GestureDecision.VERTICAL_DOWN
            else -> GestureDecision.UNDECIDED
        }
        return decision
    }

    fun reset() {
        decision = GestureDecision.UNDECIDED
    }
}

object DrawerGesturePolicy {
    data class DismissThresholds(
        val distanceDp: Float,
        val velocityDpPerSecond: Float,
    )

    fun dismissThresholds(distanceSensitivity: Int, speedSensitivity: Int): DismissThresholds {
        val distance = distanceSensitivity.coerceIn(0, 100) / 100f
        val speed = speedSensitivity.coerceIn(0, 100) / 100f
        return DismissThresholds(
            distanceDp = 180f - 148f * distance,
            velocityDpPerSecond = 1_200f - 1_200f * speed,
        )
    }

    fun isVerticalSwipe(
        dx: Float,
        dy: Float,
        minimumDistance: Float,
        dominance: Float = 1.15f,
    ): Boolean = dy >= minimumDistance && dy > abs(dx) * dominance

    fun filterStep(
        dx: Float,
        dy: Float,
        minimumDistance: Float,
        dominance: Float = 1.35f,
    ): Int? {
        if (abs(dx) < minimumDistance || abs(dx) <= abs(dy) * dominance) return null
        return if (dx < 0f) 1 else -1
    }

    fun isDismissGesture(
        dx: Float,
        dy: Float,
        durationMillis: Long,
        minimumDistance: Float,
        minimumVelocity: Float,
        dominance: Float = 1.35f,
    ): Boolean {
        if (durationMillis <= 0L || dy < minimumDistance || dy <= abs(dx) * dominance) return false
        return dy * 1_000f / durationMillis >= minimumVelocity
    }
}
