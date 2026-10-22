package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyType
import com.example.aikeyboard.core.KeyboardLayout
import com.example.aikeyboard.core.KeyboardMode
import com.example.aikeyboard.core.KeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardLanguage
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
    private val onBackspaceSwipeMoved: (Float) -> Unit = {},
    private val onBackspaceSwipeFinished: () -> Unit = {},
    private val onBackspaceSwipeCancelled: () -> Unit = {},
    private val fontScale: () -> Float = { 1f },
    private val showLanguageSwitch: () -> Boolean = { false },
    private val showNumberRow: () -> Boolean = { false },
    private val longPressDelayMillis: () -> Long = { DEFAULT_LONG_PRESS_DELAY_MS },
    private val longPressAlternatesEnabled: () -> Boolean = { true },
    private val emojiShortcutEnabled: () -> Boolean = { true },
    private val onLongPressAlternateSelected: (String) -> Unit = {},
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
    private var backgroundBitmap: Bitmap? = null
    private var backgroundImageKey: String? = null
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

    private val alternateLongPressRunnable = Runnable {
        val key = touchTracker.pressedKey
        val pointerId = touchTracker.activePointerId
        if (pointerId == TouchTracker.INVALID_POINTER_ID ||
            key?.type != KeyType.CHARACTER ||
            !hasLongPressAlternates(key) ||
            !touchTracker.owns(pointerId)
        ) return@Runnable

        alternatePopupVisible = true
        alternatePopupOptions = key.alternateTexts
        alternatePopupSelected = -1
        val metrics = rowMetrics.getOrNull(pressedRow)
        if (metrics != null && pressedColumn in metrics.left.indices) {
            alternatePopupAnchorX = (metrics.left[pressedColumn] + metrics.right[pressedColumn]) / 2f
            alternatePopupAnchorY = metrics.top
            alternatePopupAnchorBottomY = metrics.bottom
        }
        invalidate()
    }

    private var keyboardLayout: KeyboardLayout = layoutProvider.layoutFor(state.language, state.mode)
    private var lastBaseLayout: KeyboardLayout = keyboardLayout
    private var lastLanguageSwitchVisible = false
    private var lastNumberRowVisible = false
    private var alternatePopupVisible = false
    private var alternatePopupOptions: List<String> = emptyList()
    private var alternatePopupSelected = -1
    private var alternatePopupAnchorX = 0f
    private var alternatePopupAnchorY = 0f
    private var alternatePopupAnchorBottomY = 0f
    private val alternatePopupRect = RectF()
    private var rowMetrics: Array<RowMetrics> = emptyArray()
    private var pressedRow = -1
    private var pressedColumn = -1
    private var backspaceRepeatCount = 0
    private var backspaceSwipeActive = false
    private var backspaceLastX = 0f
    private var backspaceSwipeStartX = 0f
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
        contentDescription = "Keyboard. Space: ${activeSpaceLabel()}. Switch language."
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        if (backgroundImageKey != value.backgroundImageUri) {
            backgroundImageKey = value.backgroundImageUri
            backgroundBitmap = decodeBackground(value.backgroundImageUri)
        }
        backgroundPaint.shader = if (value.isGradient && backgroundBitmap == null) {
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
        synchronizeLayout()

        val image = backgroundBitmap
        if (image != null) {
            backgroundPaint.shader = null
            canvas.drawBitmap(image, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), backgroundPaint)
            backgroundPaint.color = Color.argb(54, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        } else if (backgroundPaint.shader == null) {
            canvas.drawColor(theme.backgroundColor)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }
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
                // Numeric mode uses a visually distinct operator rail while keeping the
                // original touch rectangles and keyboard geometry unchanged.
                val isNumericRailKey = state.mode == KeyboardMode.NUMERIC_PAD && (
                    (rowIndex < 4 && column == 0) ||
                        (rowIndex < 3 && column == row.keys.lastIndex)
                    )
                val isNumericCircularAbc = state.mode == KeyboardMode.NUMERIC_PAD &&
                    rowIndex == keyboardLayout.rows.lastIndex &&
                    key.type == KeyType.MODE_SWITCH &&
                    key.labelFor(false) == "ABC"
                keyPaint.color = when {
                    isPressed -> theme.pressedKeyColor
                    key.type == KeyType.SHIFT && shifted -> theme.shiftColor
                    isNumericRailKey -> theme.specialKeyColor
                    key.type == KeyType.MODE_SWITCH ||
                        key.type == KeyType.LANGUAGE_SWITCH ||
                        key.type == KeyType.ENTER -> theme.specialKeyColor
                    else -> theme.keyColor
                }
                keyPaint.style = Paint.Style.FILL
                if (isNumericCircularAbc) {
                    canvas.drawOval(
                        left + keyInset,
                        metrics.top + keyInset,
                        right - keyInset,
                        metrics.bottom - keyInset,
                        keyPaint,
                    )
                } else {
                    canvas.drawRoundRect(
                        left + keyInset,
                        metrics.top + keyInset,
                        right - keyInset,
                        metrics.bottom - keyInset,
                        if (isNumericRailKey) cornerRadius * 0.7f else cornerRadius,
                        if (isNumericRailKey) cornerRadius * 0.7f else cornerRadius,
                        keyPaint,
                    )
                }
                if (theme.keyStyle == KeyStyle.BORDERED) {
                    keyPaint.style = Paint.Style.STROKE
                    keyPaint.strokeWidth = density
                    keyPaint.color = theme.borderColor
                    if (isNumericCircularAbc) {
                        canvas.drawOval(
                            left + keyInset, metrics.top + keyInset,
                            right - keyInset, metrics.bottom - keyInset, keyPaint,
                        )
                    } else {
                        val railRadius = if (isNumericRailKey) cornerRadius * 0.7f else cornerRadius
                        canvas.drawRoundRect(
                            left + keyInset, metrics.top + keyInset,
                            right - keyInset, metrics.bottom - keyInset,
                            railRadius, railRadius, keyPaint,
                        )
                    }
                    keyPaint.style = Paint.Style.FILL
                }

                if (key.type == KeyType.LANGUAGE_SWITCH) {
                    drawLanguageSwitchIcon(
                        canvas,
                        (left + right) / 2f,
                        (metrics.top + metrics.bottom) / 2f,
                    )
                    continue
                }
                val label = displayLabel(key, shifted)
                textPaint.color = theme.textColor
                val rowHeightPx = (metrics.bottom - metrics.top - keyInset * 2f).coerceAtLeast(1f)
                textPaint.textSize = fittedKeyTextSizePx(key, label, left, right, rowHeightPx)
                val textWidth = textPaint.measureText(label)
                textPaint.getFontMetrics(fontMetrics)
                val baseline = (metrics.top + metrics.bottom - fontMetrics.ascent - fontMetrics.descent) / 2f
                canvas.drawText(label, (left + right - textWidth) / 2f, baseline, textPaint)
                if (hasLongPressAlternates(key)) {
                    drawAlternateHint(canvas, key.alternateTexts.first(), left, right, metrics.top)
                }
            }
        }
        drawAlternatePopup(canvas)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            state.handleBack()
        ) {
            clearPressedState()
            invalidate()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ACTION_DOWN starts a new gesture. A stale gesture is cancelled defensively;
                // this also guarantees that two touches can never share one repeat loop.
                clearPressedState()
                // Rebuild mode-dependent hit boxes before the next touch is dispatched.
                synchronizeLayout()
                val hit = findKey(event.x, event.y)
                touchTracker.start(event.getPointerId(0), hit)
                if (hit != null) {
                    if (hasLongPressAlternates(hit)) {
                        repeatHandler.postDelayed(
                            alternateLongPressRunnable,
                            longPressDelayMillis().coerceIn(MIN_LONG_PRESS_DELAY_MS, MAX_LONG_PRESS_DELAY_MS),
                        )
                    }
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
                        backspaceSwipeStartX = event.x
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
                } else if (alternatePopupVisible) {
                    val index = event.findPointerIndex(touchTracker.activePointerId)
                    if (index >= 0) updateAlternatePopupSelection(event.getX(index), event.getY(index))
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
                    if (alternatePopupVisible) {
                        val selected = alternatePopupOptions.getOrNull(alternatePopupSelected)
                        if (!selected.isNullOrEmpty()) onLongPressAlternateSelected(selected)
                        clearPressedState()
                        performClick()
                        return true
                    }
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

    fun currentRowCount(): Int {
        synchronizeLayout()
        return keyboardLayout.rows.size
    }

    /**
     * Returns the actual visual key block height for the current row count. The outer IME may
     * contain additional blank space, but keys are never stretched to consume it.
     */
    fun visualKeyBlockHeightDp(availableHeightDp: Int): Int {
        val activeLayout = resolvedLayout()
        val normalRows = (activeLayout.rows.size - 1).coerceAtLeast(1)
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

    private fun fittedKeyTextSizePx(
        key: KeySpec,
        label: String,
        left: Float,
        right: Float,
        rowHeightPx: Float,
    ): Float {
        val base = keyTextSizeSp(key, rowHeightPx) *
            fontScale().coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) * scaledDensity
        if (label.isEmpty()) return base

        // Fit every label, not only the space label. This keeps ABC, !?#, =<\\ and
        // other multi-character actions inside their key at every font-size setting.
        val availableWidth = (right - left - keyInset * 2f - 8f * density).coerceAtLeast(1f)
        val availableHeight = (rowHeightPx - 5f * density).coerceAtLeast(1f)
        val minimumSize = (if (label.length > 2) 11f else 13f) * scaledDensity
        var size = base
        textPaint.textSize = size
        textPaint.getFontMetrics(fontMetrics)
        while (
            (textPaint.measureText(label) > availableWidth ||
                fontMetrics.bottom - fontMetrics.top > availableHeight) &&
            size > minimumSize
        ) {
            size *= 0.92f
            textPaint.textSize = size
            textPaint.getFontMetrics(fontMetrics)
        }
        return size
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
            KeyType.SHIFT -> SHIFT_ICON_TEXT_SIZE_SP
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
        val safeRows = normalRows.coerceAtLeast(1)
        val delta = availableHeightDp - RESIZE_BASE_HEIGHT_DP
        val bottomHeight = (
            BOTTOM_ROW_HEIGHT_DP + delta * BOTTOM_HEIGHT_DELTA_SHARE
        ).coerceIn(MIN_BOTTOM_ROW_HEIGHT_DP, MAX_BOTTOM_ROW_HEIGHT_DP)
        val availableForNormalRows = (
            availableHeightDp - bottomHeight - ROW_GAP_DP * safeRows
        ).coerceAtLeast(0f)
        val fittingNormalHeight = availableForNormalRows / safeRows
        val desiredNormalHeight =
            NORMAL_KEY_HEIGHT_DP + delta * NORMAL_HEIGHT_DELTA_SHARE / safeRows
        val normalHeight = if (state.isSymbolOrNumericMode() && safeRows == 3) {
            // Primary/Extended/Numeric pages use their full saved height instead of leaving the
            // compact English alphabet's unused room below the third row.
            fittingNormalHeight
        } else {
            desiredNormalHeight.coerceAtMost(fittingNormalHeight)
        }.coerceIn(MIN_NORMAL_KEY_HEIGHT_DP, MAX_NORMAL_KEY_HEIGHT_DP)
        return normalHeight to bottomHeight
    }

    /**
     * Builds visual key rectangles independently of the logical weights in the language file.
     * This keeps the approved proportions stable while remaining responsive to the live IME
     * width. The returned array stores left/right pairs in pixels.
     */
    private fun horizontalVisualBounds(
        rowIndex: Int,
        row: com.example.aikeyboard.core.KeyboardRow,
    ): FloatArray {
        val count = row.keys.size
        val result = FloatArray(count * 2)
        if (count == 0) return result

        val gap = HORIZONTAL_GAP_DP * density
        val widths = FloatArray(count)
        val isBottomRow = rowIndex == keyboardLayout.rows.lastIndex
        val centeredHomeRowIndex =
            if (showNumberRow() && state.mode == KeyboardMode.LETTERS) 2 else 1
        val isCenteredHomeRow =
            state.mode == KeyboardMode.LETTERS &&
                rowIndex == centeredHomeRowIndex &&
                row.keys.all { it.type == KeyType.CHARACTER }
        val hasWideActions = row.keys.any {
            it.type == KeyType.SHIFT || it.type == KeyType.BACKSPACE
        }

        when {
            isBottomRow -> {
                row.keys.forEachIndexed { index, key ->
                    widths[index] = when (key.type) {
                        KeyType.SPACE -> SPACE_KEY_WIDTH_DP * density
                        KeyType.MODE_SWITCH,
                        KeyType.ENTER,
                        KeyType.BACKSPACE -> ACTION_KEY_WIDTH_DP * density
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
                val normalKeys = row.keys.filter {
                    it.type != KeyType.SHIFT && it.type != KeyType.BACKSPACE
                }
                val normalWeight = normalKeys.sumOf { it.weight.toDouble() }
                    .toFloat()
                    .coerceAtLeast(1f)
                val fixed = row.keys.sumOf { key ->
                    when (key.type) {
                        KeyType.SHIFT -> SHIFT_KEY_WIDTH_DP * density
                        KeyType.BACKSPACE -> BACKSPACE_KEY_WIDTH_DP * density
                        else -> 0f
                    }.toDouble()
                }.toFloat()
                val normalWidth = (width - fixed - gap * (count - 1)) / normalWeight
                row.keys.forEachIndexed { index, key ->
                    widths[index] = when (key.type) {
                        KeyType.SHIFT -> SHIFT_KEY_WIDTH_DP * density
                        KeyType.BACKSPACE -> BACKSPACE_KEY_WIDTH_DP * density
                        else -> normalWidth * key.weight
                    }
                }
            }

            isCenteredHomeRow -> {
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
                val totalWeight = row.keys.sumOf { it.weight.toDouble() }
                    .toFloat()
                    .coerceAtLeast(1f)
                val weightedWidth = (width - gap * (count - 1)) / totalWeight
                for (index in widths.indices) {
                    widths[index] = weightedWidth * row.keys[index].weight
                }
            }
        }

        val totalWidth = widths.sum() + gap * (count - 1)
        val start = when {
            isCenteredHomeRow && totalWidth < width -> SECOND_ROW_INSET_DP * density
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

    private fun activeSpaceLabel(): String = when (state.language) {
        KeyboardLanguage.ENGLISH -> "English"
        KeyboardLanguage.URDU -> "اردو"
        KeyboardLanguage.ROMAN_URDU -> "Roman Urdu"
    }

    private fun displayLabel(key: KeySpec, shifted: Boolean): String = when (key.type) {
        KeyType.SPACE -> activeSpaceLabel()
        else -> key.labelFor(shifted)
    }

    /** Compact globe vector; its bounds are inset from the key rectangle by construction. */
    private fun hasLongPressAlternates(key: KeySpec): Boolean {
        if (key.type != KeyType.CHARACTER || key.alternateTexts.isEmpty()) return false
        return if (key.outputText == "," || key.outputText == "،") {
            emojiShortcutEnabled()
        } else {
            longPressAlternatesEnabled()
        }
    }

    private fun updateAlternatePopupSelection(x: Float, y: Float) {
        if (!alternatePopupRect.contains(x, y)) {
            alternatePopupSelected = -1
            invalidate()
            return
        }
        val cellWidth = alternatePopupRect.width() / alternatePopupOptions.size.coerceAtLeast(1)
        alternatePopupSelected = ((x - alternatePopupRect.left) / cellWidth)
            .toInt()
            .coerceIn(0, alternatePopupOptions.lastIndex)
        invalidate()
    }

    private fun drawAlternateHint(
        canvas: Canvas,
        alternate: String,
        left: Float,
        right: Float,
        rowTop: Float,
    ) {
        textPaint.color = theme.textColor
        textPaint.textSize = ALTERNATE_HINT_TEXT_SIZE_SP * scaledDensity
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.getFontMetrics(fontMetrics)
        val baseline = rowTop + keyInset + ALTERNATE_HINT_TOP_PADDING_DP * density -
            fontMetrics.ascent
        canvas.drawText(
            alternate,
            right - keyInset - ALTERNATE_HINT_RIGHT_PADDING_DP * density,
            baseline,
            textPaint,
        )
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawAlternatePopup(canvas: Canvas) {
        if (!alternatePopupVisible || alternatePopupOptions.isEmpty()) return
        val cellWidth = POPUP_CELL_WIDTH_DP * density
        val popupHeight = POPUP_HEIGHT_DP * density
        val popupWidth = cellWidth * alternatePopupOptions.size
        val left = (alternatePopupAnchorX - popupWidth / 2f)
            .coerceIn(0f, (width - popupWidth).coerceAtLeast(0f))
        val gap = POPUP_GAP_DP * density
        val preferredTop = alternatePopupAnchorY - popupHeight - gap
        val belowTop = alternatePopupAnchorBottomY + gap
        val top = when {
            preferredTop >= 0f -> preferredTop
            belowTop + popupHeight <= height -> belowTop
            else -> 0f
        }.coerceIn(0f, (height - popupHeight).coerceAtLeast(0f))
        alternatePopupRect.set(left, top, left + popupWidth, top + popupHeight)
        keyPaint.style = Paint.Style.FILL
        keyPaint.color = theme.keyColor
        canvas.drawRoundRect(alternatePopupRect, cornerRadius * 1.5f, cornerRadius * 1.5f, keyPaint)
        keyPaint.style = Paint.Style.STROKE
        keyPaint.strokeWidth = density
        keyPaint.color = theme.borderColor
        canvas.drawRoundRect(alternatePopupRect, cornerRadius * 1.5f, cornerRadius * 1.5f, keyPaint)
        keyPaint.style = Paint.Style.FILL
        alternatePopupOptions.forEachIndexed { index, option ->
            val cellLeft = left + index * cellWidth
            if (index == alternatePopupSelected) {
                keyPaint.color = theme.pressedKeyColor
                canvas.drawRoundRect(
                    cellLeft + density,
                    top + density,
                    cellLeft + cellWidth - density,
                    top + popupHeight - density,
                    cornerRadius,
                    cornerRadius,
                    keyPaint,
                )
            }
            textPaint.color = theme.textColor
            textPaint.textSize = POPUP_TEXT_SIZE_SP * scaledDensity
            textPaint.getFontMetrics(fontMetrics)
            val baseline = top + (popupHeight - fontMetrics.ascent - fontMetrics.descent) / 2f
            val textWidth = textPaint.measureText(option)
            canvas.drawText(option, cellLeft + (cellWidth - textWidth) / 2f, baseline, textPaint)
        }
    }

    private fun drawLanguageSwitchIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        keyPaint.color = theme.textColor
        keyPaint.style = Paint.Style.STROKE
        keyPaint.strokeWidth = 1.8f * density
        keyPaint.strokeCap = Paint.Cap.ROUND
        keyPaint.strokeJoin = Paint.Join.ROUND
        val radius = 8.5f * density
        canvas.drawCircle(centerX, centerY, radius, keyPaint)
        canvas.drawOval(
            centerX - 4.2f * density,
            centerY - radius,
            centerX + 4.2f * density,
            centerY + radius,
            keyPaint,
        )
        canvas.drawOval(
            centerX - radius,
            centerY - 4.2f * density,
            centerX + radius,
            centerY + 4.2f * density,
            keyPaint,
        )
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, keyPaint)
        keyPaint.style = Paint.Style.FILL
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
            repeatHandler.removeCallbacks(alternateLongPressRunnable)
            alternatePopupVisible = false
            alternatePopupOptions = emptyList()
            alternatePopupSelected = -1
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

    private fun decodeBackground(uriValue: String?): Bitmap? {
        if (uriValue.isNullOrBlank()) return null
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(Uri.parse(uriValue)).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
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
        repeatHandler.removeCallbacks(alternateLongPressRunnable)
        alternatePopupVisible = false
        alternatePopupOptions = emptyList()
        alternatePopupSelected = -1
        alternatePopupRect.setEmpty()
        alternatePopupAnchorBottomY = 0f
        if (backspaceSwipeActive) {
            backspaceSwipeActive = false
            onBackspaceSwipeCancelled()
        }
        backspaceRepeatCount = 0
        backspaceLastX = 0f
        backspaceSwipeStartX = 0f
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
            val leftDistance = backspaceSwipeStartX - x
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
            onBackspaceSwipeMoved(leftDistance)
            return
        }

        backspaceLastX = x
        // Absolute travel keeps the visible selection reversible when the finger moves right.
        onBackspaceSwipeMoved((backspaceSwipeStartX - x).coerceAtLeast(0f))
    }

    private fun resolvedLayout(): KeyboardLayout {
        val base = layoutProvider.layoutFor(state.language, state.mode)
        // The normal globe/language key is only present in Alphabet mode. Symbol layers use
        // ABC/1234/?123 navigation in its place.
        val visible = showLanguageSwitch() && state.isAlphabetMode()
        val numberVisible = showNumberRow() && state.mode == KeyboardMode.LETTERS
        if (base !== lastBaseLayout ||
            visible != lastLanguageSwitchVisible ||
            numberVisible != lastNumberRowVisible
        ) {
            lastBaseLayout = base
            lastLanguageSwitchVisible = visible
            lastNumberRowVisible = numberVisible
            var resolved = if (numberVisible) withNumberRow(base) else base
            if (visible) resolved = withLanguageSwitch(resolved)
            keyboardLayout = resolved
        }
        return keyboardLayout
    }

    private fun synchronizeLayout() {
        val previousLayout = keyboardLayout
        val currentLayout = resolvedLayout()
        if (currentLayout !== previousLayout) rebuildMetrics()
    }

    private fun withNumberRow(base: KeyboardLayout): KeyboardLayout {
        val firstRow = base.rows.firstOrNull()
        if (firstRow != null && firstRow.keys.size == 10 &&
            firstRow.keys.all { it.outputText in "1234567890" }
        ) return base
        val numberText = "1234567890"
        val numberRow = com.example.aikeyboard.core.KeyboardRow(
            Array(numberText.length) { index -> KeySpec.punctuation(numberText[index].toString()) },
        )
        return KeyboardLayout(arrayOf(numberRow, *base.rows))
    }

    private fun withLanguageSwitch(base: KeyboardLayout): KeyboardLayout {
        val lastRow = base.rows.lastOrNull() ?: return base
        if (lastRow.keys.any { it.type == KeyType.LANGUAGE_SWITCH }) return base
        val insertAt = lastRow.keys.indexOfFirst { it.type == KeyType.MODE_SWITCH }
            .let { if (it < 0) 0 else it + 1 }
        val keys = lastRow.keys.toMutableList().apply {
            add(insertAt, KeySpec.languageSwitch(""))
        }.toTypedArray()
        val rows = base.rows.copyOf()
        rows[rows.lastIndex] = com.example.aikeyboard.core.KeyboardRow(keys)
        return KeyboardLayout(rows)
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
        const val MIN_NORMAL_KEY_HEIGHT_DP = 32f
        const val MAX_NORMAL_KEY_HEIGHT_DP = 110f
        const val MIN_BOTTOM_ROW_HEIGHT_DP = 44f
        const val MAX_BOTTOM_ROW_HEIGHT_DP = 92f
        const val MIN_LABEL_SCALE = 0.9f
        const val MAX_LABEL_SCALE = 1.25f
        const val MIN_FONT_SCALE = 0.75f
        const val MAX_FONT_SCALE = 1.35f
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
        const val SHIFT_ICON_TEXT_SIZE_SP = 34f
        const val POPUP_TEXT_SIZE_SP = 24f
        const val ALTERNATE_HINT_TEXT_SIZE_SP = 11f
        const val ALTERNATE_HINT_TOP_PADDING_DP = 3f
        const val ALTERNATE_HINT_RIGHT_PADDING_DP = 4f
        const val POPUP_CELL_WIDTH_DP = 44f
        const val POPUP_HEIGHT_DP = 58f
        const val POPUP_GAP_DP = 3f
        const val DEFAULT_LONG_PRESS_DELAY_MS = 500L
        const val MIN_LONG_PRESS_DELAY_MS = 250L
        const val MAX_LONG_PRESS_DELAY_MS = 1200L
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
