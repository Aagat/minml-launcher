package dev.obvious.minimallauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class HsvColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onColorChanged: ((Int) -> Unit)? = null

    var color: Int
        get() = Color.HSVToColor(hsv)
        set(value) {
            Color.colorToHSV(value, hsv)
            updateDescription()
            invalidate()
        }

    private val hsv = floatArrayOf(0f, 1f, 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette = RectF()
    private val hueBar = RectF()
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private var activeRegion = Region.NONE

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Visual color picker"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(dp(220))
        val desiredHeight = dp(250) + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(requestedWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop.toFloat()
        val hueHeight = dp(28).toFloat()
        val gap = dp(16).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        palette.set(left, top, right, bottom - hueHeight - gap)
        hueBar.set(left, palette.bottom + gap, right, bottom)

        val hueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        val saturation = LinearGradient(
            palette.left,
            palette.top,
            palette.right,
            palette.top,
            Color.WHITE,
            hueColor,
            Shader.TileMode.CLAMP,
        )
        val value = LinearGradient(
            palette.left,
            palette.top,
            palette.left,
            palette.bottom,
            Color.WHITE,
            Color.BLACK,
            Shader.TileMode.CLAMP,
        )
        paint.shader = ComposeShader(saturation, value, PorterDuff.Mode.MULTIPLY)
        canvas.drawRoundRect(palette, dp(5).toFloat(), dp(5).toFloat(), paint)

        paint.shader = LinearGradient(
            hueBar.left,
            hueBar.top,
            hueBar.right,
            hueBar.top,
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(hueBar, dp(5).toFloat(), dp(5).toFloat(), paint)
        paint.shader = null

        val selectorX = palette.left + hsv[1] * palette.width()
        val selectorY = palette.bottom - hsv[2] * palette.height()
        selectorPaint.color = if (hsv[2] < 0.55f) Color.WHITE else Color.BLACK
        canvas.drawCircle(selectorX, selectorY, dp(7).toFloat(), selectorPaint)

        val hueX = hueBar.left + hsv[0] / 360f * hueBar.width()
        selectorPaint.color = Color.WHITE
        selectorPaint.strokeWidth = dp(3).toFloat()
        canvas.drawRoundRect(
            RectF(hueX - dp(3), hueBar.top - dp(3), hueX + dp(3), hueBar.bottom + dp(3)),
            dp(2).toFloat(),
            dp(2).toFloat(),
            selectorPaint,
        )
        selectorPaint.strokeWidth = dp(2).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeRegion = when {
                    palette.contains(event.x, event.y) -> Region.PALETTE
                    hueBar.contains(event.x, event.y) -> Region.HUE
                    else -> Region.NONE
                }
                if (activeRegion == Region.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeRegion == Region.NONE) return false
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activeRegion == Region.NONE) return false
                updateFromTouch(event.x, event.y)
                activeRegion = Region.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activeRegion = Region.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun updateFromTouch(x: Float, y: Float) {
        when (activeRegion) {
            Region.PALETTE -> {
                hsv[1] = ((x - palette.left) / palette.width()).coerceIn(0f, 1f)
                hsv[2] = (1f - (y - palette.top) / palette.height()).coerceIn(0f, 1f)
            }
            Region.HUE -> hsv[0] = ((x - hueBar.left) / hueBar.width()).coerceIn(0f, 1f) * 360f
            Region.NONE -> return
        }
        updateDescription()
        invalidate()
        onColorChanged?.invoke(color)
    }

    private fun updateDescription() {
        contentDescription = "Visual color picker, ${LauncherColorPalette.formatHex(color)}, " +
            "hue ${hsv[0].roundToInt()}, saturation ${(hsv[1] * 100).roundToInt()} percent, " +
            "brightness ${(hsv[2] * 100).roundToInt()} percent"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private enum class Region { NONE, PALETTE, HUE }
}
