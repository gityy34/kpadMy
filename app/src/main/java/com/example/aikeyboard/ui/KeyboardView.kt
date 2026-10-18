package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyType
import com.example.aikeyboard.core.KeyboardLayout
import com.example.aikeyboard.core.KeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardMode
import com.example.aikeyboard.core.KeyboardState
import kotlin.math.roundToInt

/**
 * Lightweight canvas keyboard. It has no per-key child Views and dispatches on ACTION_DOWN,
 * keeping touch-to-InputConnection latency independent of animation or layout work.
 */
class KeyboardView(
    context: Context,
    private val layoutProvider: KeyboardLayoutProvider,
    private val state: KeyboardState,
    private val onKeyPressed: (KeySpec) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fontMetrics = Paint.FontMetrics()
    private val keyInset = KEY_INSET_DP * density
    private val cornerRadius = CORNER_RADIUS_DP * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
    }
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = pressedKey
            if (key?.type == KeyType.BACKSPACE) {
                onKeyPressed(key)
                invalidate()
                repeatHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            }
        }
    }

    private var keyboardLayout: KeyboardLayout = layoutProvider.layoutFor(state.mode)
    private var rowMetrics: Array<RowMetrics> = emptyArray()
    private var pressedKey: KeySpec? = null
    private var pressedRow = -1
    private var pressedColumn = -1

    init {
        isFocusable = true
        isClickable = true
        setBackgroundColor(BACKGROUND_COLOR)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (KEYBOARD_HEIGHT_DP * density).roundToInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildMetrics()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLayout = layoutProvider.layoutFor(state.mode)
        if (currentLayout !== keyboardLayout) {
            keyboardLayout = currentLayout
            rebuildMetrics()
        }

        canvas.drawColor(BACKGROUND_COLOR)
        val shifted = state.shiftActive()
        for (rowIndex in keyboardLayout.rows.indices) {
            val row = keyboardLayout.rows[rowIndex]
            val metrics = rowMetrics[rowIndex]
            for (column in row.keys.indices) {
                val key = row.keys[column]
                val left = metrics.left[column]
                val right = metrics.right[column]
                val isPressed = rowIndex == pressedRow && column == pressedColumn
                keyPaint.color = when {
                    isPressed -> PRESSED_KEY_COLOR
                    key.type == KeyType.SHIFT && shifted -> SHIFT_ACTIVE_COLOR
                    key.type == KeyType.MODE_SWITCH || key.type == KeyType.ENTER -> SPECIAL_KEY_COLOR
                    else -> KEY_COLOR
                }
                canvas.drawRoundRect(
                    left + keyInset,
                    metrics.top + keyInset,
                    right - keyInset,
                    metrics.bottom - keyInset,
                    cornerRadius,
                    cornerRadius,
                    keyPaint,
                )

                val label = key.labelFor(shifted)
                textPaint.textSize =
                    (if (key.type == KeyType.CHARACTER) LETTER_TEXT_SIZE_DP else ACTION_TEXT_SIZE_DP) * density
                val textWidth = textPaint.measureText(label)
                textPaint.getFontMetrics(fontMetrics)
                val baseline = (metrics.top + metrics.bottom - fontMetrics.ascent - fontMetrics.descent) / 2f
                canvas.drawText(label, (left + right - textWidth) / 2f, baseline, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedRow = -1
                pressedColumn = -1
                val hit = findKey(event.x, event.y)
                if (hit != null) {
                    pressedKey = hit
                    onKeyPressed(hit)
                    if (hit.type == KeyType.BACKSPACE) {
                        repeatHandler.removeCallbacks(repeatRunnable)
                        repeatHandler.postDelayed(repeatRunnable, BACKSPACE_REPEAT_START_DELAY_MS)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                repeatHandler.removeCallbacks(repeatRunnable)
                pressedKey = null
                pressedRow = -1
                pressedColumn = -1
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_MOVE -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelPendingInput() {
        repeatHandler.removeCallbacks(repeatRunnable)
        pressedKey = null
        pressedRow = -1
        pressedColumn = -1
        invalidate()
    }

    private fun rebuildMetrics() {
        if (width <= 0 || height <= 0) return

        var metricsNeedRebuild = rowMetrics.size != keyboardLayout.rows.size
        if (!metricsNeedRebuild) {
            for (index in rowMetrics.indices) {
                if (rowMetrics[index].keyCount != keyboardLayout.rows[index].keys.size) {
                    metricsNeedRebuild = true
                    break
                }
            }
        }
        if (metricsNeedRebuild) {
            rowMetrics = Array(keyboardLayout.rows.size) { index ->
                RowMetrics(keyboardLayout.rows[index].keys.size)
            }
        }

        val rowHeight = height.toFloat() / keyboardLayout.rows.size
        for (rowIndex in keyboardLayout.rows.indices) {
            val row = keyboardLayout.rows[rowIndex]
            val metrics = rowMetrics[rowIndex]
            metrics.top = rowIndex * rowHeight
            metrics.bottom = if (rowIndex == keyboardLayout.rows.lastIndex) height.toFloat() else (rowIndex + 1) * rowHeight
            var totalWeight = 0f
            for (key in row.keys) totalWeight += key.weight
            var cursor = 0f
            for (column in row.keys.indices) {
                metrics.left[column] = cursor
                cursor += width * (row.keys[column].weight / totalWeight)
                metrics.right[column] = if (column == row.keys.lastIndex) width.toFloat() else cursor
            }
        }
    }

    private fun findKey(x: Float, y: Float): KeySpec? {
        if (rowMetrics.isEmpty()) return null
        for (rowIndex in rowMetrics.indices) {
            val metrics = rowMetrics[rowIndex]
            val isLastRow = rowIndex == rowMetrics.lastIndex
            if (y >= metrics.top && (y < metrics.bottom || (isLastRow && y == height.toFloat()))) {
                val row = keyboardLayout.rows[rowIndex]
                for (column in row.keys.indices) {
                    if (x >= metrics.left[column] && x < metrics.right[column]) {
                        pressedRow = rowIndex
                        pressedColumn = column
                        return row.keys[column]
                    }
                }
                // A boundary pixel at the far edge belongs to the final key.
                if (x == width.toFloat()) {
                    val last = row.keys.lastIndex
                    pressedRow = rowIndex
                    pressedColumn = last
                    return row.keys[last]
                }
            }
        }
        return null
    }

    private class RowMetrics(val keyCount: Int) {
        var top = 0f
        var bottom = 0f
        val left = FloatArray(keyCount)
        val right = FloatArray(keyCount)
    }

    private companion object {
        const val KEYBOARD_HEIGHT_DP = 270
        const val BACKSPACE_REPEAT_START_DELAY_MS = 420L
        const val BACKSPACE_REPEAT_INTERVAL_MS = 45L
        const val KEY_INSET_DP = 2f
        const val CORNER_RADIUS_DP = 6f
        const val LETTER_TEXT_SIZE_DP = 21f
        const val ACTION_TEXT_SIZE_DP = 14f
        val BACKGROUND_COLOR = Color.rgb(243, 244, 246)
        val KEY_COLOR = Color.WHITE
        val SPECIAL_KEY_COLOR = Color.rgb(221, 227, 244)
        val PRESSED_KEY_COLOR = Color.rgb(215, 224, 255)
        val SHIFT_ACTIVE_COLOR = Color.rgb(190, 204, 255)
        val TEXT_COLOR = Color.rgb(32, 33, 36)
    }
}
