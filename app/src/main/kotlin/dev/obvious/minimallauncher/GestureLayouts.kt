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
    private val gesture = GestureStateMachine(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val swipeDownThreshold = max(
        ViewConfiguration.get(context).scaledTouchSlop * 4f,
        resources.displayMetrics.density * 48f,
    )
    private var downY = 0f
    private var handled = false
    private var swipeDownEligible = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.begin(event.x, event.y)
                downY = event.y
                handled = false
                swipeDownEligible = canSwipeDown?.invoke() != false
            }
            MotionEvent.ACTION_MOVE -> {
                val decision = gesture.update(event.x, event.y)
                if (decision == GestureDecision.HORIZONTAL_LEFT || decision == GestureDecision.HORIZONTAL_RIGHT) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                if (
                    decision == GestureDecision.VERTICAL_DOWN &&
                    swipeDownEligible
                ) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> gesture.reset()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val decision = gesture.update(event.x, event.y)
                if (!handled && decision == GestureDecision.HORIZONTAL_LEFT) {
                    handled = true
                    onFilterSwipe?.invoke(1)
                } else if (!handled && decision == GestureDecision.HORIZONTAL_RIGHT) {
                    handled = true
                    onFilterSwipe?.invoke(-1)
                } else if (
                    !handled &&
                    decision == GestureDecision.VERTICAL_DOWN &&
                    event.y - downY >= swipeDownThreshold &&
                    swipeDownEligible
                ) {
                    handled = true
                    onSwipeDown?.invoke()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!handled) performClick()
                gesture.reset()
                handled = false
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture.reset()
                handled = false
            }
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
    var onEmptyLongPress: (() -> Unit)? = null
    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 3
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0f
    private var startY = 0f
    private var intercepting = false
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
                intercepting = false
                longPressTriggered = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) handler.removeCallbacks(longPress)
                if (dy < -threshold && abs(dy) > abs(dx) * 1.15f) {
                    handler.removeCallbacks(longPress)
                    intercepting = true
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
                if (dy < -threshold && abs(dy) > abs(dx) * 1.15f) intercepting = true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (intercepting) onSwipeUp?.invoke()
                else if (!longPressTriggered) performClick()
                intercepting = false
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                intercepting = false
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
