package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import com.example.aikeyboard.theme.KeyStyle
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight canvas keyboard. It has no per-key child Views and dispatches on ACTION_DOWN,
 * keeping touch-to-InputConnection latency independent of animation or layout work.
 */
class KeyboardView(
    context: Context,
    private val layoutProvider: KeyboardLayoutProvider,
    private val state: KeyboardState,
    private val onKeyPressed: (KeySpec) -> Boolean,
    private val glideEnabled: () -> Boolean = { false },
    private val onGlideStarted: () -> Unit = {},
    private val onGlideCompleted: (GlideGesture) -> Unit = {},
    private val onGlideCancelled: (GlideGesture) -> Unit = {},
    private val onSpaceLongPressed: () -> Unit = {},
    private val onSpaceCursorStarted: () -> Boolean = { false },
    private val onSpaceCursorMoved: (Int) -> Unit = {},
    private val onBackspaceSwipeStarted: () -> Boolean = { false },
    private val onBackspaceSwipeMoved: () -> Unit = {},
    private val onBackspaceSwipeFinished: () -> Unit = {},
    private val onBackspaceSwipeCancelled: () -> Unit = {},
) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
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
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var theme = KeyboardTheme.SOLID_WHITE
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

    private val spaceLongPressRunnable = Runnable {
        val key = touchTracker.pressedKey
        val pointerId = touchTracker.activePointerId
        if (pointerId == TouchTracker.INVALID_POINTER_ID ||
            key?.type != KeyType.SPACE ||
            !spaceCommitted ||
            !touchTracker.owns(pointerId)
        ) return@Runnable

        spaceLongPressTriggered = true
        spaceCommitted = false
        onSpaceLongPressed()
    }

    private var keyboardLayout: KeyboardLayout = layoutProvider.layoutFor(state.language, state.mode)
    private var rowMetrics: Array<RowMetrics> = emptyArray()
    private var pressedRow = -1
    private var pressedColumn = -1
    private var backspaceRepeatCount = 0
    private var backspaceSwipeActive = false
    private var backspaceLastX = 0f
    private var backspaceStartY = 0f
    private var backspaceAccumulatedLeft = 0f
    private var spaceCommitted = false
    private var spaceLongPressTriggered = false
    private var spaceCursorActive = false
    private var spaceLastX = 0f
    private var spaceStartY = 0f
    private var spaceAccumulatedDelta = 0f
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

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        backgroundPaint.shader = if (value.isGradient) {
            LinearGradient(
                0f, 0f, width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f),
                value.gradientStart ?: value.backgroundColor,
                value.gradientEnd ?: value.backgroundColor,
                Shader.TileMode.CLAMP,
            )
        } else null
        trailPaint.color = value.accentColor
        invalidate()
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
        if (theme.isGradient) applyTheme(theme)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLayout = layoutProvider.layoutFor(state.language, state.mode)
        if (currentLayout !== keyboardLayout) {
            keyboardLayout = currentLayout
            rebuildMetrics()
        }

        if (backgroundPaint.shader == null) canvas.drawColor(theme.backgroundColor)
        else canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
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
                    isPressed -> theme.pressedKeyColor
                    key.type == KeyType.SHIFT && shifted -> theme.shiftColor
                    key.type == KeyType.MODE_SWITCH ||
                        key.type == KeyType.LANGUAGE_SWITCH ||
                        key.type == KeyType.ENTER -> theme.specialKeyColor
                    else -> theme.keyColor
                }
                keyPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(
                    left + keyInset,
                    metrics.top + keyInset,
                    right - keyInset,
                    metrics.bottom - keyInset,
                    cornerRadius,
                    cornerRadius,
                    keyPaint,
                )
                if (theme.keyStyle == KeyStyle.BORDERED) {
                    keyPaint.style = Paint.Style.STROKE
                    keyPaint.strokeWidth = density
                    keyPaint.color = theme.borderColor
                    canvas.drawRoundRect(
                        left + keyInset, metrics.top + keyInset,
                        right - keyInset, metrics.bottom - keyInset,
                        cornerRadius, cornerRadius, keyPaint,
                    )
                    keyPaint.style = Paint.Style.FILL
                }

                val label = key.labelFor(shifted)
                textPaint.color = theme.textColor
                val rowHeightPx = (metrics.bottom - metrics.top - keyInset * 2f).coerceAtLeast(1f)
                textPaint.textSize = keyTextSizeSp(key, rowHeightPx) * scaledDensity
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
                    // Backspace is deferred until ACTION_UP so a left swipe can show a word
                    // selection first instead of deleting text during the gesture.
                    invalidate()
                    val committed = if (hit.type == KeyType.BACKSPACE) {
                        true
                    } else {
                        onKeyPressed(hit)
                    }
                    // State changes such as one-shot Shift consumption and mode switching must
                    // be reflected without making the next key wait for a later event.
                    invalidate()
                    if (hit.type == KeyType.SPACE && committed) {
                        spaceCommitted = true
                        spaceLastX = event.x
                        spaceStartY = event.y
                        spaceAccumulatedDelta = 0f
                        repeatHandler.postDelayed(spaceLongPressRunnable, SPACE_LONG_PRESS_DELAY_MS)
                    }
                    if (hit.type == KeyType.BACKSPACE) {
                        backspaceRepeatCount = 0
                        backspaceLastX = event.x
                        backspaceStartY = event.y
                        backspaceAccumulatedLeft = 0f
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
                } else if (spaceCommitted || spaceCursorActive) {
                    val index = event.findPointerIndex(touchTracker.activePointerId)
                    if (index >= 0) updateSpaceCursor(event.getX(index), event.getY(index))
                } else if (touchTracker.pressedKey?.type == KeyType.BACKSPACE) {
                    val index = event.findPointerIndex(touchTracker.activePointerId)
                    if (index >= 0) updateBackspaceSwipe(event.getX(index), event.getY(index))
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
                    val pressedKey = touchTracker.pressedKey
                    if (backspaceSwipeActive) {
                        backspaceSwipeActive = false
                        onBackspaceSwipeFinished()
                    } else if (pressedKey?.type == KeyType.BACKSPACE && backspaceRepeatCount == 0) {
                        // A quick Backspace tap still deletes exactly once, but only after we
                        // know that the gesture was not becoming a swipe selection.
                        onKeyPressed(pressedKey)
                    }
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

    fun currentRowCount(): Int = layoutProvider.layoutFor(state.language, state.mode).rows.size

    /**
     * Returns the actual visual key block height for the current row count. The outer IME may
     * contain additional blank space, but keys are never stretched to consume it.
     */
    fun visualKeyBlockHeightDp(availableHeightDp: Int): Int {
        val normalRows = (keyboardLayout.rows.size - 1).coerceAtLeast(1)
        val gap = ROW_GAP_DP * normalRows
        val (normalHeight, bottomHeight) = rowHeightsDp(availableHeightDp.toFloat(), normalRows)
        return (normalHeight * normalRows + gap + bottomHeight).roundToInt()
    }

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

        val normalRows = (keyboardLayout.rows.size - 1).coerceAtLeast(1)
        val rowGap = ROW_GAP_DP * density
        val (normalRowHeightDp, bottomRowHeightDp) = rowHeightsDp(
            availableHeightDp = height / density,
            normalRows = normalRows,
        )
        val normalRowHeight = normalRowHeightDp * density
        val bottomRowHeight = bottomRowHeightDp * density
        var visualTop = 0f
        for (rowIndex in keyboardLayout.rows.indices) {
            val row = keyboardLayout.rows[rowIndex]
            val metrics = rowMetrics[rowIndex]
            val isBottomRow = rowIndex == keyboardLayout.rows.lastIndex
            val visualHeight = if (isBottomRow) bottomRowHeight else normalRowHeight
            val visualBottom = visualTop + visualHeight
            // The metric bounds include only the existing key inset. This keeps the complete
            // visible rectangle tappable while leaving the configured gap between neighbors.
            metrics.top = visualTop - keyInset
            metrics.bottom = visualBottom + keyInset

            val visualBounds = horizontalVisualBounds(rowIndex, row)
            for (column in row.keys.indices) {
                metrics.left[column] = visualBounds[column * 2] - keyInset
                metrics.right[column] = visualBounds[column * 2 + 1] + keyInset
            }
            visualTop = visualBottom + if (isBottomRow) 0f else rowGap
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

    private fun keyTextSizeSp(key: KeySpec, rowHeightPx: Float): Float {
        val baseRowHeightDp = if (
            key.type == KeyType.SPACE ||
            key.type == KeyType.MODE_SWITCH ||
            key.type == KeyType.ENTER
        ) BOTTOM_ROW_HEIGHT_DP else NORMAL_KEY_HEIGHT_DP
        val scale = (rowHeightPx / density / baseRowHeightDp)
            .coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE)
        val baseTextSize = when (key.type) {
            KeyType.SPACE -> SPACE_LABEL_TEXT_SIZE_SP
            KeyType.MODE_SWITCH -> MODE_LABEL_TEXT_SIZE_SP
            KeyType.CHARACTER -> MAIN_KEY_TEXT_SIZE_SP
            else -> ACTION_TEXT_SIZE_SP
        }
        return baseTextSize * scale
    }

    /**
     * Distributes only the explicit Resize height delta across the normal and bottom rows.
     * The compact default keeps the approved key proportions; larger/smaller settings genuinely
     * stretch/shrink the key boxes instead of merely translating the keyboard.
     */
    private fun rowHeightsDp(availableHeightDp: Float, normalRows: Int): Pair<Float, Float> {
        val delta = availableHeightDp - RESIZE_BASE_HEIGHT_DP
        val normalHeight = (
            NORMAL_KEY_HEIGHT_DP + delta * NORMAL_HEIGHT_DELTA_SHARE / normalRows
        ).coerceIn(MIN_NORMAL_KEY_HEIGHT_DP, MAX_NORMAL_KEY_HEIGHT_DP)
        val bottomHeight = (
            BOTTOM_ROW_HEIGHT_DP + delta * BOTTOM_HEIGHT_DELTA_SHARE
        ).coerceIn(MIN_BOTTOM_ROW_HEIGHT_DP, MAX_BOTTOM_ROW_HEIGHT_DP)
        return normalHeight to bottomHeight
    }

    /**
     * Builds visual key rectangles independently of the logical weights in the language file.
     * This keeps the approved proportions stable while remaining responsive to the live IME
     * width. The returned array stores left/right pairs in pixels.
     */
    private fun horizontalVisualBounds(rowIndex: Int, row: com.example.aikeyboard.core.KeyboardRow): FloatArray {
        val count = row.keys.size
        val result = FloatArray(count * 2)
        if (count == 0) return result

        val gap = HORIZONTAL_GAP_DP * density
        val widths = FloatArray(count)
        val widthDp = width / density
        val isBottomRow = rowIndex == keyboardLayout.rows.lastIndex
        val hasWideActions = row.keys.any {
            it.type == KeyType.SHIFT || it.type == KeyType.BACKSPACE
        }

        when {
            isBottomRow -> {
                row.keys.forEachIndexed { index, key ->
                    widths[index] = when (key.type) {
                        KeyType.SPACE -> SPACE_KEY_WIDTH_DP * density
                        KeyType.MODE_SWITCH, KeyType.ENTER, KeyType.BACKSPACE -> ACTION_KEY_WIDTH_DP * density
                        else -> NORMAL_KEY_WIDTH_DP * density
                    }
                }
                val targetWidth = widths.sum() + gap * (count - 1)
                if (targetWidth > width) {
                    val scale = width / targetWidth
                    for (index in widths.indices) widths[index] *= scale
                }
            }

            hasWideActions -> {
                val normalCount = row.keys.count {
                    it.type != KeyType.SHIFT && it.type != KeyType.BACKSPACE
                }.coerceAtLeast(1)
                val fixed = row.keys.sumOf { key ->
                    when (key.type) {
                        KeyType.SHIFT -> SHIFT_KEY_WIDTH_DP * density
                        KeyType.BACKSPACE -> BACKSPACE_KEY_WIDTH_DP * density
                        else -> 0f
                    }.toDouble()
                }.toFloat()
                val normalWidth = (
                    width - fixed - gap * (count - 1)
                ) / normalCount
                row.keys.forEachIndexed { index, key ->
                    widths[index] = when (key.type) {
                        KeyType.SHIFT -> SHIFT_KEY_WIDTH_DP * density
                        KeyType.BACKSPACE -> BACKSPACE_KEY_WIDTH_DP * density
                        else -> normalWidth
                    }
                }
            }

            rowIndex == 1 -> {
                // The A–L row is intentionally centered. At the 360dp baseline this produces
                // roughly 22dp of side inset and 31dp normal keys.
                val preferredInset = SECOND_ROW_INSET_DP * density
                val normalWidthAtInset = (
                    width - preferredInset * 2f - gap * (count - 1)
                ) / count
                val inset = if (normalWidthAtInset > 0f) preferredInset else 0f
                val normalWidth = (
                    width - inset * 2f - gap * (count - 1)
                ) / count
                for (index in widths.indices) widths[index] = normalWidth
            }

            else -> {
                val normalWidth = (width - gap * (count - 1)) / count
                for (index in widths.indices) widths[index] = normalWidth
            }
        }

        val totalWidth = widths.sum() + gap * (count - 1)
        val start = when {
            rowIndex == 1 && totalWidth < width -> SECOND_ROW_INSET_DP * density
            else -> ((width - totalWidth) / 2f).coerceAtLeast(0f)
        }
        var cursor = start
        for (index in widths.indices) {
            result[index * 2] = cursor
            result[index * 2 + 1] = cursor + widths[index]
            cursor += widths[index] + gap
        }
        return result
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
        repeatHandler.removeCallbacks(spaceLongPressRunnable)
        if (backspaceSwipeActive) {
            backspaceSwipeActive = false
            onBackspaceSwipeCancelled()
        }
        backspaceRepeatCount = 0
        backspaceLastX = 0f
        backspaceStartY = 0f
        backspaceAccumulatedLeft = 0f
        spaceCommitted = false
        spaceLongPressTriggered = false
        spaceCursorActive = false
        spaceLastX = 0f
        spaceStartY = 0f
        spaceAccumulatedDelta = 0f
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

    private fun updateBackspaceSwipe(x: Float, y: Float) {
        if (!backspaceSwipeActive) {
            val leftDistance = backspaceLastX - x
            val verticalDistance = kotlin.math.abs(y - backspaceStartY)
            if (leftDistance < BACKSPACE_SWIPE_START_DISTANCE_DP * density ||
                leftDistance < verticalDistance
            ) return

            // A confirmed swipe takes over from the normal hold-repeat loop. Text remains
            // untouched while the editor selection grows under the finger.
            repeatHandler.removeCallbacks(repeatRunnable)
            if (!onBackspaceSwipeStarted()) return
            backspaceSwipeActive = true
            backspaceAccumulatedLeft = 0f
            backspaceLastX = x
            onBackspaceSwipeMoved()
            return
        }

        val leftDelta = backspaceLastX - x
        backspaceLastX = x
        backspaceAccumulatedLeft += leftDelta
        val step = BACKSPACE_SWIPE_STEP_DP * density
        while (backspaceAccumulatedLeft >= step) {
            onBackspaceSwipeMoved()
            backspaceAccumulatedLeft -= step
        }
    }

    private fun updateSpaceCursor(x: Float, y: Float) {
        if (!spaceCursorActive) {
            val horizontalDistance = kotlin.math.abs(x - spaceLastX)
            val verticalDistance = kotlin.math.abs(y - spaceStartY)
            if (horizontalDistance < SPACE_CURSOR_START_DISTANCE_DP * density ||
                horizontalDistance < verticalDistance
            ) return
            repeatHandler.removeCallbacks(spaceLongPressRunnable)
            if (!onSpaceCursorStarted()) return
            spaceCursorActive = true
            spaceCommitted = false
            spaceAccumulatedDelta = 0f
        }

        val delta = x - spaceLastX
        spaceLastX = x
        spaceAccumulatedDelta += delta
        val step = SPACE_CURSOR_STEP_DP * density
        while (kotlin.math.abs(spaceAccumulatedDelta) >= step) {
            val direction = if (spaceAccumulatedDelta > 0f) 1 else -1
            onSpaceCursorMoved(direction)
            spaceAccumulatedDelta -= direction * step
        }
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
        const val BACKSPACE_SWIPE_START_DISTANCE_DP = 18f
        const val BACKSPACE_SWIPE_STEP_DP = 14f
        const val SPACE_LONG_PRESS_DELAY_MS = 2_000L
        const val SPACE_CURSOR_START_DISTANCE_DP = 18f
        const val SPACE_CURSOR_STEP_DP = 14f
        const val KEY_INSET_DP = 2f
        const val CORNER_RADIUS_DP = 6f
        const val NORMAL_KEY_WIDTH_DP = 31f
        const val NORMAL_KEY_HEIGHT_DP = 47f
        const val BOTTOM_ROW_HEIGHT_DP = 50f
        const val RESIZE_BASE_HEIGHT_DP = 270f
        const val NORMAL_HEIGHT_DELTA_SHARE = 0.75f
        const val BOTTOM_HEIGHT_DELTA_SHARE = 0.25f
        const val MIN_NORMAL_KEY_HEIGHT_DP = 40f
        const val MAX_NORMAL_KEY_HEIGHT_DP = 110f
        const val MIN_BOTTOM_ROW_HEIGHT_DP = 44f
        const val MAX_BOTTOM_ROW_HEIGHT_DP = 92f
        const val MIN_LABEL_SCALE = 0.9f
        const val MAX_LABEL_SCALE = 1.25f
        const val SHIFT_KEY_WIDTH_DP = 48f
        const val BACKSPACE_KEY_WIDTH_DP = 49f
        const val SPACE_KEY_WIDTH_DP = 138f
        const val ACTION_KEY_WIDTH_DP = 49f
        const val HORIZONTAL_GAP_DP = 5f
        const val ROW_GAP_DP = 8f
        const val SECOND_ROW_INSET_DP = 22f
        const val MAIN_KEY_TEXT_SIZE_SP = 29f
        const val ACTION_TEXT_SIZE_SP = 28f
        const val MODE_LABEL_TEXT_SIZE_SP = 19f
        const val SPACE_LABEL_TEXT_SIZE_SP = 20f
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
