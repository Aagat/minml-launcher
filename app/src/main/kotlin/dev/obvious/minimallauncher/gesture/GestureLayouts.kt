package dev.obvious.minimallauncher.gesture

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
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
    private var emptyLongPressEligible = true
    private val longPress = Runnable {
        if (!emptyLongPressEligible) return@Runnable
        longPressTriggered = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onEmptyLongPress?.invoke()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            emptyLongPressEligible = !hasInteractiveDescendantAt(this, event.x, event.y)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                interceptedSwipe = null
                longPressTriggered = false
                handler.removeCallbacks(longPress)
                if (emptyLongPressEligible) {
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
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
                if (emptyLongPressEligible) {
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
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

    private fun hasInteractiveDescendantAt(parent: ViewGroup, x: Float, y: Float): Boolean {
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.alpha == 0f) continue
            val left = child.left + child.translationX
            val top = child.top + child.translationY
            if (x < left || x >= left + child.width || y < top || y >= top + child.height) continue
            val childX = x - left + child.scrollX
            val childY = y - top + child.scrollY
            if (child is ViewGroup && hasInteractiveDescendantAt(child, childX, childY)) return true
            if (child.isClickable || child.isLongClickable) return true
        }
        return false
    }
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
