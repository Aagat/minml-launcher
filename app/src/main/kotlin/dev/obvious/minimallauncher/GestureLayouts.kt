package dev.obvious.minimallauncher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.max

class FilterGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onFilterSwipe: ((Int) -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null
    var canSwipeDown: (() -> Boolean)? = null
    var useImeDismissThreshold: (() -> Boolean)? = null
    var dismissDistanceSensitivity: Int = 65
    var dismissSpeedSensitivity: Int = 65
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val gesture = GestureStateMachine(touchSlop, dominance = 1.25f)
    private val filterSwipeDistance = max(touchSlop * 6f, density * 72f)
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var swipeDownEligible = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.begin(event.x, event.y)
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                swipeDownEligible = canSwipeDown?.invoke() != false
            }
            MotionEvent.ACTION_MOVE -> {
                when (gesture.update(event.x, event.y)) {
                    GestureDecision.HORIZONTAL_LEFT, GestureDecision.HORIZONTAL_RIGHT -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    GestureDecision.VERTICAL_DOWN -> if (swipeDownEligible) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> gesture.reset()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> gesture.update(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val filterStep = DrawerGesturePolicy.filterStep(dx, dy, filterSwipeDistance)
                val dismissThresholds = DrawerGesturePolicy.dismissThresholds(
                    dismissDistanceSensitivity,
                    dismissSpeedSensitivity,
                )
                when {
                    filterStep != null -> onFilterSwipe?.invoke(filterStep)
                    swipeDownEligible && useImeDismissThreshold?.invoke() == true && DrawerGesturePolicy.isVerticalSwipe(
                        dx = dx,
                        dy = dy,
                        minimumDistance = touchSlop * 3f,
                    ) -> onSwipeDown?.invoke()
                    swipeDownEligible && DrawerGesturePolicy.isDismissGesture(
                        dx = dx,
                        dy = dy,
                        durationMillis = event.eventTime - downTime,
                        minimumDistance = density * dismissThresholds.distanceDp,
                        minimumVelocity = density * dismissThresholds.velocityDpPerSecond,
                    ) -> onSwipeDown?.invoke()
                    else -> performClick()
                }
                gesture.reset()
            }
            MotionEvent.ACTION_CANCEL -> gesture.reset()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

class HomeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null
    var onEmptyLongPress: (() -> Unit)? = null
    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 3
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0f
    private var startY = 0f
    private var interceptedSwipe: HomeSwipeDirection? = null
    private var longPressTriggered = false
    private val longPress = Runnable {
        longPressTriggered = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onEmptyLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                interceptedSwipe = null
                longPressTriggered = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) handler.removeCallbacks(longPress)
                val direction = HomeGesturePolicy.swipeDirection(dx, dy, threshold.toFloat())
                if (direction != null) {
                    handler.removeCallbacks(longPress)
                    interceptedSwipe = direction
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                longPressTriggered = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    handler.removeCallbacks(longPress)
                }
                HomeGesturePolicy.swipeDirection(dx, dy, threshold.toFloat())?.let { interceptedSwipe = it }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                when (interceptedSwipe) {
                    HomeSwipeDirection.UP -> onSwipeUp?.invoke()
                    HomeSwipeDirection.DOWN -> onSwipeDown?.invoke()
                    null -> if (!longPressTriggered) performClick()
                }
                interceptedSwipe = null
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                interceptedSwipe = null
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

enum class HomeSwipeDirection { UP, DOWN }

object HomeGesturePolicy {
    fun swipeDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        dominance: Float = 1.15f,
    ): HomeSwipeDirection? {
        if (abs(dy) <= threshold || abs(dy) <= abs(dx) * dominance) return null
        return if (dy < 0f) HomeSwipeDirection.UP else HomeSwipeDirection.DOWN
    }
}
