package dev.obvious.minimallauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.roundToInt

class EditableWidgetFrame(context: Context) : FrameLayout(context) {
    var onGeometryCommitted: ((WidgetGeometry) -> Unit)? = null
    var onRemoveRequested: (() -> Unit)? = null
    var onEditingChanged: ((Boolean) -> Unit)? = null

    var minimumEditorWidthPx: Int = dp(120)
    var minimumEditorHeightPx: Int = dp(80)

    private val moveSurface = View(context)
    private val border = View(context)
    private val doneControl = editorControl()
    private val removeControl = editorControl()
    private val resizeControl = editorControl()
    private var editing = false
    private var itemName = "widget"
    private var resizeEnabled = true
    private var secondaryActionEnabled = true
    private var accentColor = 0xFFB7F36B.toInt()
    private var editorTextColor = Color.WHITE
    private var editorTypeface: Typeface = Typeface.MONOSPACE
    private var startRawX = 0f
    private var startRawY = 0f
    private var startLeft = 0
    private var startTop = 0
    private var startWidth = 0
    private var startHeight = 0

    init {
        clipChildren = false
        clipToPadding = false
        addView(moveSurface, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(border, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(doneControl, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            gravity = Gravity.TOP or Gravity.END
        })
        addView(removeControl, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        })
        addView(resizeControl, LayoutParams(dp(72), dp(52)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        })

        moveSurface.id = R.id.widget_editor_move
        moveSurface.isFocusable = true
        moveSurface.setOnTouchListener { view, event ->
            handleMove(event).also { if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
        }
        moveSurface.setOnKeyListener { _, keyCode, event ->
            handleNudgeKey(keyCode, event, resizing = false)
        }
        resizeControl.text = context.getString(R.string.widget_editor_resize_label)
        resizeControl.id = R.id.widget_editor_resize
        resizeControl.setOnTouchListener { view, event ->
            handleResize(event).also { if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
        }
        resizeControl.setOnKeyListener { _, keyCode, event ->
            handleNudgeKey(keyCode, event, resizing = true)
        }
        doneControl.text = context.getString(R.string.widget_editor_done_label)
        doneControl.id = R.id.widget_editor_done
        doneControl.setOnClickListener { exitEditMode(commit = true) }
        removeControl.text = context.getString(R.string.widget_editor_remove_label)
        removeControl.id = R.id.widget_editor_remove
        removeControl.setOnClickListener { onRemoveRequested?.invoke() }
        configureEditorBehavior(
            itemName = "widget",
            allowResize = true,
            secondaryActionLabel = context.getString(R.string.widget_editor_remove_label),
            secondaryActionDescription = "Remove widget",
        )
        setEditorVisible(false)
    }

    fun configureEditor(accent: Int, textColor: Int, typeface: Typeface) {
        accentColor = accent
        editorTextColor = textColor
        editorTypeface = typeface
        updateEditorStyle()
    }

    fun configureEditorBehavior(
        itemName: String,
        allowResize: Boolean,
        secondaryActionLabel: CharSequence?,
        secondaryActionDescription: String?,
    ) {
        this.itemName = itemName
        resizeEnabled = allowResize
        secondaryActionEnabled = secondaryActionLabel != null
        moveSurface.contentDescription = "Move $itemName"
        resizeControl.contentDescription = "Resize $itemName"
        doneControl.contentDescription = "Finish arranging $itemName"
        removeControl.text = secondaryActionLabel ?: ""
        removeControl.contentDescription = secondaryActionDescription
        setEditorVisible(editing)
    }

    fun editingHint(): String = if (resizeEnabled) {
        "Drag to move · drag resize ↘ to size · tap done when finished"
    } else {
        "Drag to move · tap reset for the default position · tap done when finished"
    }

    fun enterEditMode() {
        if (editing) return
        editing = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setEditorVisible(true)
        moveSurface.requestFocus()
        onEditingChanged?.invoke(true)
        val resizeGuidance = if (resizeEnabled) ", or use the resize handle" else ""
        announceForAccessibility("$itemName editing. Drag to move$resizeGuidance. Arrow keys also move it.")
    }

    fun exitEditMode(commit: Boolean) {
        if (!editing) return
        editing = false
        setEditorVisible(false)
        if (commit) onGeometryCommitted?.invoke(currentGeometry())
        onEditingChanged?.invoke(false)
    }

    fun isEditing(): Boolean = editing

    fun applyGeometry(geometry: WidgetGeometry, automaticTopPx: Int = 0) {
        val parentView = parent as? ViewGroup ?: return
        val safe = geometry.sanitized()
        val parentWidth = parentView.width
        val parentHeight = parentView.height
        if (parentWidth <= 0 || parentHeight <= 0) return
        val width = WidgetGeometryPolicy.widthPixels(safe.widthPermille, parentWidth, minimumEditorWidthPx)
        val minimumHeight = minimumEditorHeightPx.coerceAtMost(parentHeight)
        val height = dp(safe.heightDp).coerceIn(minimumHeight, parentHeight)
        val availableX = (parentWidth - width).coerceAtLeast(0)
        val availableY = (parentHeight - height).coerceAtLeast(0)
        val left = WidgetGeometryPolicy.pixelsFromPermille(safe.xPermille, availableX)
        val top = if (safe.yPermille == WidgetGeometry.AUTO_POSITION) {
            automaticTopPx.coerceIn(0, availableY)
        } else {
            WidgetGeometryPolicy.pixelsFromPermille(safe.yPermille, availableY)
        }
        layoutParams = (layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(width, height)).apply {
            this.width = width
            this.height = height
            leftMargin = left
            topMargin = top
        }
    }

    fun currentGeometry(): WidgetGeometry {
        val params = layoutParams as? FrameLayout.LayoutParams
            ?: return WidgetGeometry((height / density).roundToInt())
        val parentView = parent as? ViewGroup
        val parentWidth = parentView?.width ?: width
        val parentHeight = parentView?.height ?: height
        return WidgetGeometry(
            heightDp = (params.height / density).roundToInt(),
            xPermille = WidgetGeometryPolicy.permilleFromPixels(
                params.leftMargin,
                (parentWidth - params.width).coerceAtLeast(0),
            ),
            yPermille = WidgetGeometryPolicy.permilleFromPixels(
                params.topMargin,
                (parentHeight - params.height).coerceAtLeast(0),
            ),
            widthPermille = WidgetGeometryPolicy.widthPermille(params.width, parentWidth),
        ).sanitized()
    }

    private fun handleMove(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val params = layoutParams as? FrameLayout.LayoutParams ?: return true
                val parentView = parent as? ViewGroup ?: return true
                params.leftMargin = snap(startLeft + (event.rawX - startRawX).roundToInt())
                    .coerceIn(0, (parentView.width - params.width).coerceAtLeast(0))
                params.topMargin = snap(startTop + (event.rawY - startRawY).roundToInt())
                    .coerceIn(0, (parentView.height - params.height).coerceAtLeast(0))
                layoutParams = params
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onGeometryCommitted?.invoke(currentGeometry())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun handleResize(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val params = layoutParams as? FrameLayout.LayoutParams ?: return true
                val parentView = parent as? ViewGroup ?: return true
                val maxWidth = (parentView.width - params.leftMargin).coerceAtLeast(minimumEditorWidthPx)
                val maxHeight = (parentView.height - params.topMargin).coerceAtLeast(minimumEditorHeightPx)
                params.width = snap(startWidth + (event.rawX - startRawX).roundToInt())
                    .coerceIn(minimumEditorWidthPx.coerceAtMost(maxWidth), maxWidth)
                params.height = snap(startHeight + (event.rawY - startRawY).roundToInt())
                    .coerceIn(minimumEditorHeightPx.coerceAtMost(maxHeight), maxHeight)
                layoutParams = params
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onGeometryCommitted?.invoke(currentGeometry())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun beginGesture(event: MotionEvent) {
        val params = layoutParams as? FrameLayout.LayoutParams ?: return
        startRawX = event.rawX
        startRawY = event.rawY
        startLeft = params.leftMargin
        startTop = params.topMargin
        startWidth = params.width
        startHeight = params.height
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun handleNudgeKey(keyCode: Int, event: KeyEvent, resizing: Boolean): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val delta = dp(if (event.isShiftPressed) 16 else 4)
        val params = layoutParams as? FrameLayout.LayoutParams ?: return false
        val parentView = parent as? ViewGroup ?: return false
        if (resizing) {
            val widthDelta = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -delta
                KeyEvent.KEYCODE_DPAD_RIGHT -> delta
                else -> 0
            }
            val heightDelta = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> -delta
                KeyEvent.KEYCODE_DPAD_DOWN -> delta
                else -> 0
            }
            if (widthDelta == 0 && heightDelta == 0) return false
            val availableWidth = (parentView.width - params.leftMargin).coerceAtLeast(0)
            val availableHeight = (parentView.height - params.topMargin).coerceAtLeast(0)
            params.width = (params.width + widthDelta).coerceIn(
                minimumEditorWidthPx.coerceAtMost(availableWidth),
                availableWidth,
            )
            params.height = (params.height + heightDelta).coerceIn(
                minimumEditorHeightPx.coerceAtMost(availableHeight),
                availableHeight,
            )
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> params.leftMargin -= delta
                KeyEvent.KEYCODE_DPAD_RIGHT -> params.leftMargin += delta
                KeyEvent.KEYCODE_DPAD_UP -> params.topMargin -= delta
                KeyEvent.KEYCODE_DPAD_DOWN -> params.topMargin += delta
                else -> return false
            }
            params.leftMargin = params.leftMargin.coerceIn(0, (parentView.width - params.width).coerceAtLeast(0))
            params.topMargin = params.topMargin.coerceIn(0, (parentView.height - params.height).coerceAtLeast(0))
        }
        layoutParams = params
        onGeometryCommitted?.invoke(currentGeometry())
        return true
    }

    private fun setEditorVisible(visible: Boolean) {
        val commonVisibility = if (visible) View.VISIBLE else View.GONE
        moveSurface.visibility = commonVisibility
        border.visibility = commonVisibility
        doneControl.visibility = commonVisibility
        removeControl.visibility = if (visible && secondaryActionEnabled) View.VISIBLE else View.GONE
        resizeControl.visibility = if (visible && resizeEnabled) View.VISIBLE else View.GONE
        if (!visible) clearFocus()
    }

    private fun updateEditorStyle() {
        border.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), accentColor)
            cornerRadius = dp(4).toFloat()
        }
        listOf(doneControl, removeControl, resizeControl).forEach { control ->
            control.setTextColor(if (control === removeControl) editorTextColor else accentColor)
            control.typeface = editorTypeface
            control.background = GradientDrawable().apply {
                setColor(0xE6101416.toInt())
                cornerRadius = dp(3).toFloat()
            }
        }
    }

    private fun editorControl(): EditorControlTextView = EditorControlTextView(context).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
        setPadding(dp(10), 0, dp(10), 0)
    }

    private fun snap(value: Int): Int {
        val grid = dp(4).coerceAtLeast(1)
        return (value / grid.toFloat()).roundToInt() * grid
    }

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()
}

private class EditorControlTextView(context: Context) : TextView(context) {
    override fun performClick(): Boolean = super.performClick()
}
