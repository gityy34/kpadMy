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
import com.example.aikeyboard.core.KeyboardState
import com.example.aikeyboard.glide.GlideGesture
import com.example.aikeyboard.glide.GlideKeyGeometry
import com.example.aikeyboard.glide.GlidePathBuffer
import kotlin.math.hypot
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
    private val glideEnabled: () -> Boolean = { false },
    private val onGlideStarted: () -> Unit = {},
    private val onGlideCompleted: (GlideGesture) -> Unit = {},
    private val onGlideCancelled: (GlideGesture) -> Unit = {},
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
    private val touchTracker = TouchTracker()
    private val glidePath = GlidePathBuffer()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GLIDE_TRAIL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = GLIDE_TRAIL_WIDTH_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = touchTracker.pressedKey
            val pointerId = touchTracker.activePointerId
            if (pointerId == TouchTracker.INVALID_POINTER_ID || key?.type != KeyType.BACKSPACE) return

            onKeyPressed(key)
            // The callback can synchronously close the editor/IME. Do not reschedule a repeat
            // after lifecycle cleanup has already cleared the active gesture.
            if (!touchTracker.owns(pointerId) || touchTracker.pressedKey !== key) return
            backspaceRepeatCount++
            invalidate()
            repeatHandler.postDelayed(this, backspaceRepeatInterval())
        }
    }

    private var keyboardLayout: KeyboardLayout = layoutProvider.layoutFor(state.language, state.mode)
    private var rowMetrics: Array<RowMetrics> = emptyArray()
    private var pressedRow = -1
    private var pressedColumn = -1
    private var backspaceRepeatCount = 0
    private var glideGeometry: List<GlideKeyGeometry> = emptyList()
    private var glideEligible = false
    private var glideActive = false
    private var glideDistance = 0f
    private var lastGlideX = 0f
    private var lastGlideY = 0f
    private var glideProvisionalText = ""

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
        val currentLayout = layoutProvider.layoutFor(state.language, state.mode)
        if (currentLayout !== keyboardLayout) {
            keyboardLayout = currentLayout
            rebuildMetrics()
        }

        canvas.drawColor(BACKGROUND_COLOR)
        drawGlideTrail(canvas)
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
                    key.type == KeyType.MODE_SWITCH ||
                        key.type == KeyType.LANGUAGE_SWITCH ||
                        key.type == KeyType.ENTER -> SPECIAL_KEY_COLOR
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
                // ACTION_DOWN starts a new gesture. A stale gesture is cancelled defensively;
                // this also guarantees that two touches can never share one repeat loop.
                clearPressedState()
                val hit = findKey(event.x, event.y)
                touchTracker.start(event.getPointerId(0), hit)
                if (hit != null) {
                    if (glideEnabled() && isGlideKey(hit)) {
                        glideEligible = true
                        glideProvisionalText = hit.textFor(state.shiftActive())
                        glidePath.add(event.x, event.y)
                        lastGlideX = event.x
                        lastGlideY = event.y
                        onGlideStarted()
                    }
                    // Schedule the visual pressed state before calling into InputConnection.
                    // The text commit is still synchronous and happens in this same event.
                    invalidate()
                    onKeyPressed(hit)
                    // State changes such as one-shot Shift consumption and mode switching must
                    // be reflected without making the next key wait for a later event.
                    invalidate()
                    if (hit.type == KeyType.BACKSPACE) {
                        backspaceRepeatCount = 0
                        repeatHandler.postDelayed(repeatRunnable, BACKSPACE_REPEAT_START_DELAY_MS)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // The first pointer owns the key. Extra pointers are consumed but never commit.
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Keep the original key latched through small finger movement. This prevents a
                // single gesture from committing a second key when crossing a boundary.
                if (touchTracker.activePointerId != TouchTracker.INVALID_POINTER_ID &&
                    event.findPointerIndex(touchTracker.activePointerId) < 0
                ) {
                    clearPressedState()
                } else if (glideEligible) {
                    val index = event.findPointerIndex(touchTracker.activePointerId)
                    if (index >= 0) updateGlidePath(event.getX(index), event.getY(index))
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (touchTracker.owns(event.getPointerId(event.actionIndex))) {
                    if (glideActive) notifyGlideCancelled()
                    clearPressedState()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (touchTracker.owns(event.getPointerId(event.actionIndex))) {
                    if (glideActive) notifyGlideCompleted()
                    clearPressedState()
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (glideActive) notifyGlideCancelled()
                clearPressedState()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelPendingInput() {
        clearPressedState()
    }

    fun currentGlideGeometry(): List<GlideKeyGeometry> = glideGeometry

    override fun onDetachedFromWindow() {
        clearPressedState()
        super.onDetachedFromWindow()
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
        val geometry = ArrayList<GlideKeyGeometry>()
        for (rowIndex in keyboardLayout.rows.indices) {
            val row = keyboardLayout.rows[rowIndex]
            val metrics = rowMetrics[rowIndex]
            for (column in row.keys.indices) {
                geometry += GlideKeyGeometry(
                    key = row.keys[column],
                    left = metrics.left[column],
                    top = metrics.top,
                    right = metrics.right[column],
                    bottom = metrics.bottom,
                )
            }
        }
        glideGeometry = geometry
    }

    private fun updateGlidePath(x: Float, y: Float) {
        val distance = hypot(x - lastGlideX, y - lastGlideY)
        if (distance < GLIDE_SAMPLE_DISTANCE_PX * density) return
        glideDistance += distance
        lastGlideX = x
        lastGlideY = y
        glidePath.add(x, y)
        if (!glideActive && glideDistance >= GLIDE_START_DISTANCE_PX * density &&
            distinctGlideKeyCount() >= 2
        ) {
            glideActive = true
        }
        if (glideActive) invalidate()
    }

    private fun distinctGlideKeyCount(): Int {
        var count = 0
        var previous: KeySpec? = null
        for (index in 0 until glidePath.size) {
            val current = keyAt(glidePath.pointAt(index).x, glidePath.pointAt(index).y)
            if (current != null && current !== previous && isGlideKey(current)) {
                count++
                previous = current
            }
        }
        return count
    }

    private fun drawGlideTrail(canvas: Canvas) {
        if (!glideActive || glidePath.size < 2) return
        var previous = glidePath.pointAt(0)
        for (index in 1 until glidePath.size) {
            val current = glidePath.pointAt(index)
            canvas.drawLine(previous.x, previous.y, current.x, current.y, trailPaint)
            previous = current
        }
    }

    private fun notifyGlideCompleted() {
        onGlideCompleted(
            GlideGesture(glidePath.toList(), state.language, glideProvisionalText),
        )
    }

    private fun notifyGlideCancelled() {
        onGlideCancelled(
            GlideGesture(glidePath.toList(), state.language, glideProvisionalText),
        )
    }

    private fun findKey(x: Float, y: Float): KeySpec? {
        if (rowMetrics.isEmpty()) return null
        // The visible key rectangles are the hit rectangles. Clamping only at the outer view
        // edge provides safe edge tolerance without stealing pixels from a neighboring key.
        val touchX = x.coerceIn(0f, width.toFloat())
        val touchY = y.coerceIn(0f, height.toFloat())
        for (rowIndex in rowMetrics.indices) {
            val metrics = rowMetrics[rowIndex]
            val isLastRow = rowIndex == rowMetrics.lastIndex
            if (touchY >= metrics.top && (touchY < metrics.bottom || (isLastRow && touchY == height.toFloat()))) {
                val row = keyboardLayout.rows[rowIndex]
                for (column in row.keys.indices) {
                    if (touchX >= metrics.left[column] && touchX < metrics.right[column]) {
                        pressedRow = rowIndex
                        pressedColumn = column
                        return row.keys[column]
                    }
                }
                // A boundary pixel at the far edge belongs to the final key.
                if (touchX == width.toFloat()) {
                    val last = row.keys.lastIndex
                    pressedRow = rowIndex
                    pressedColumn = last
                    return row.keys[last]
                }
            }
        }
        return null
    }

    private fun keyAt(x: Float, y: Float): KeySpec? {
        val touchX = x.coerceIn(0f, width.toFloat())
        val touchY = y.coerceIn(0f, height.toFloat())
        for (geometry in glideGeometry) {
            if (touchX >= geometry.left && touchX <= geometry.right &&
                touchY >= geometry.top && touchY <= geometry.bottom
            ) return geometry.key
        }
        return null
    }

    private fun isGlideKey(key: KeySpec): Boolean =
        key.type == KeyType.CHARACTER && key.outputText.any { it.isLetterOrDigit() }

    private fun clearPressedState() {
        repeatHandler.removeCallbacks(repeatRunnable)
        backspaceRepeatCount = 0
        touchTracker.cancel()
        pressedRow = -1
        pressedColumn = -1
        glidePath.clear()
        glideEligible = false
        glideActive = false
        glideDistance = 0f
        glideProvisionalText = ""
        invalidate()
    }

    private fun backspaceRepeatInterval(): Long = when {
        backspaceRepeatCount < 5 -> BACKSPACE_REPEAT_SLOW_INTERVAL_MS
        backspaceRepeatCount < 15 -> BACKSPACE_REPEAT_MEDIUM_INTERVAL_MS
        backspaceRepeatCount < 30 -> BACKSPACE_REPEAT_FAST_INTERVAL_MS
        else -> BACKSPACE_REPEAT_MIN_INTERVAL_MS
    }

    private class RowMetrics(val keyCount: Int) {
        var top = 0f
        var bottom = 0f
        val left = FloatArray(keyCount)
        val right = FloatArray(keyCount)
    }

    private companion object {
        const val KEYBOARD_HEIGHT_DP = 270
        const val BACKSPACE_REPEAT_START_DELAY_MS = 360L
        const val BACKSPACE_REPEAT_SLOW_INTERVAL_MS = 85L
        const val BACKSPACE_REPEAT_MEDIUM_INTERVAL_MS = 60L
        const val BACKSPACE_REPEAT_FAST_INTERVAL_MS = 42L
        const val BACKSPACE_REPEAT_MIN_INTERVAL_MS = 30L
        const val KEY_INSET_DP = 2f
        const val CORNER_RADIUS_DP = 6f
        const val LETTER_TEXT_SIZE_DP = 21f
        const val ACTION_TEXT_SIZE_DP = 14f
        val BACKGROUND_COLOR = Color.rgb(243, 244, 246)
        val KEY_COLOR = Color.WHITE
        val SPECIAL_KEY_COLOR = Color.rgb(221, 227, 244)
        val PRESSED_KEY_COLOR = Color.rgb(215, 224, 255)
        val SHIFT_ACTIVE_COLOR = Color.rgb(190, 204, 255)
        val GLIDE_TRAIL_COLOR = Color.rgb(83, 111, 220)
        const val GLIDE_TRAIL_WIDTH_DP = 3f
        const val GLIDE_SAMPLE_DISTANCE_PX = 5f
        const val GLIDE_START_DISTANCE_PX = 14f
        val TEXT_COLOR = Color.rgb(32, 33, 36)
    }
}
