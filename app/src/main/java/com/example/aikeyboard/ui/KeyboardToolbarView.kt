package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.prediction.SuggestionCandidate
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.roundToInt

/** Fixed-height toolbar: edge actions stay fixed while its center changes to suggestions. */
class KeyboardToolbarView(
    context: Context,
    initialOrder: List<String>,
    private val onTools: () -> Unit,
    private val onMic: () -> Unit,
    private val onToolbarAction: (String) -> Unit,
    private val onCandidateSelected: (SuggestionCandidate) -> Unit,
    private val onCandidateLongPressed: (SuggestionCandidate) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit,
    suggestionSlotCount: Int = 3,
    private val fontScale: () -> Float = { 1f },
) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND_COLOR }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRESSED_COLOR }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIVIDER_COLOR }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = SUGGESTION_TEXT_SIZE_SP * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = 20f * density
        textAlign = Paint.Align.CENTER
    }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val micButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var theme = KeyboardTheme.SOLID_WHITE
    private val fixedSuggestionSlotCount = 3
    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable {
        val item = centerItems.getOrNull(pressedIndex)
        if (item != null && centerMode == CenterMode.TOOLBAR) {
            reorderMode = true
            longPressTriggered = true
            invalidate()
        }
        candidates.getOrNull(pressedIndex)?.let {
            longPressTriggered = true
            onCandidateLongPressed(it)
        }
    }

    private val centerItems = initialOrder.toMutableList()
    private var candidates: List<SuggestionCandidate> = emptyList()
    private var centerMode = CenterMode.TOOLBAR
    private var closePanelMode = false
    private var activePointerId = INVALID_POINTER
    private var pressedIndex = -1
    private var reorderMode = false
    private var longPressTriggered = false

    init {
        isClickable = true
        contentDescription = "Keyboard toolbar. Tools menu and Voice typing."
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        updateBackgroundShader()
        pressedPaint.color = value.pressedKeyColor
        dividerPaint.color = value.borderColor
        textPaint.color = value.textColor
        iconPaint.color = value.textColor
        micButtonPaint.color = value.keyColor
        invalidate()
    }

    private fun updateBackgroundShader() {
        backgroundPaint.color = theme.backgroundColor
        backgroundPaint.shader = if (theme.isGradient) {
            LinearGradient(
                0f, 0f, width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f),
                theme.gradientStart ?: theme.backgroundColor,
                theme.gradientEnd ?: theme.backgroundColor,
                Shader.TileMode.CLAMP,
            )
        } else null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateBackgroundShader()
    }

    fun setCandidates(value: List<SuggestionCandidate>) {
        setCandidates(exactTyped = null, value = value)
    }

    /**
     * Puts the exact current token in cell 1, then fills the remaining cells with candidates.
     * Candidate objects retain their complete text for commit handling.
     */
    fun setCandidates(
        exactTyped: SuggestionCandidate?,
        value: List<SuggestionCandidate>,
    ) {
        if (centerMode != CenterMode.SUGGESTIONS) return
        val next = ArrayList<SuggestionCandidate>(fixedSuggestionSlotCount)
        exactTyped?.let { next += it }
        value.forEach { candidate ->
            if (next.size >= fixedSuggestionSlotCount) return@forEach
            if (exactTyped == null || candidate.text != exactTyped.text) {
                next += candidate
            }
        }
        candidates = next
        invalidate()
    }

    fun clearCandidates() {
        if (candidates.isEmpty()) return
        candidates = emptyList()
        invalidate()
    }

    fun showToolbarActions() {
        centerMode = CenterMode.TOOLBAR
        candidates = emptyList()
        invalidate()
    }

    /** Adds one supported tool without duplicating the persisted toolbar order. */
    fun addToolbarItem(item: String) {
        if (item !in SUPPORTED_TOOLBAR_ITEMS || centerItems.contains(item)) return
        centerItems += item
        onOrderChanged(centerItems.toList())
        invalidate()
    }

    fun removeToolbarItem(item: String) {
        if (item !in centerItems || centerItems.size <= 1) return
        centerItems.remove(item)
        onOrderChanged(centerItems.toList())
        invalidate()
    }

    fun showSuggestions() {
        centerMode = CenterMode.SUGGESTIONS
        invalidate()
    }

    fun isSuggestionsMode(): Boolean = centerMode == CenterMode.SUGGESTIONS

    /** Compatibility boundary for existing callers entering a non-keyboard panel. */
    fun setToolsMode(enabled: Boolean) {
        if (enabled) showToolbarActions() else invalidate()
    }

    /**
     * Reuses the permanently reserved left toolbar cell as a close/back control while an
     * in-keyboard tools panel is open. The cell size and hit target never change.
     */
    fun setClosePanelMode(enabled: Boolean) {
        if (closePanelMode == enabled) return
        closePanelMode = enabled
        invalidate()
    }

    fun isClosePanelMode(): Boolean = closePanelMode

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val edge = EDGE_WIDTH_DP * density
        if (closePanelMode) {
            drawCloseIcon(canvas, edge / 2f, pressedIndex == EDGE_TOOLS)
        } else {
            drawToolsIcon(canvas, edge / 2f, pressedIndex == EDGE_TOOLS)
        }
        drawMicrophoneIcon(canvas, width - edge / 2f, pressedIndex == EDGE_MIC)
        canvas.drawRect(edge - 1f, 0f, edge, height.toFloat(), dividerPaint)
        canvas.drawRect(width - edge, 0f, width - edge + 1f, height.toFloat(), dividerPaint)

        val centerLeft = edge
        val centerRight = width - edge
        val slots = if (centerMode == CenterMode.SUGGESTIONS) {
            fixedSuggestionSlotCount
        } else {
            centerItems.size.coerceAtLeast(1)
        }
        val centerWidth = (centerRight - centerLeft) / slots
        if (centerMode == CenterMode.SUGGESTIONS) {
            for (index in 0 until fixedSuggestionSlotCount) {
                val left = centerLeft + centerWidth * index
                val right = left + centerWidth
                if (index == pressedIndex) canvas.drawRect(left, 0f, right, height.toFloat(), pressedPaint)
                if (index < fixedSuggestionSlotCount - 1) {
                    canvas.drawRect(right - 1f, 0f, right, height.toFloat(), dividerPaint)
                }
                candidates.getOrNull(index)?.let { candidate ->
                    drawText(canvas, candidate.text, left, right)
                }
            }
        } else {
            centerItems.forEachIndexed { index, item ->
                val left = centerLeft + centerWidth * index
                if (index == pressedIndex) canvas.drawRect(left, 0f, left + centerWidth, height.toFloat(), pressedPaint)
                drawIcon(canvas, iconFor(item), left + centerWidth / 2f, reorderMode && index == pressedIndex)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelTouch()
                activePointerId = event.getPointerId(0)
                pressedIndex = indexAt(event.x)
                longPressTriggered = false
                handler.postDelayed(longPress, LONG_PRESS_MILLIS)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val pointer = event.findPointerIndex(activePointerId)
                if (pointer < 0) return true
                if (reorderMode && centerMode == CenterMode.TOOLBAR) {
                    val next = indexAt(event.getX(pointer))
                    if (next in centerItems.indices && pressedIndex in centerItems.indices && next != pressedIndex) {
                        val item = centerItems.removeAt(pressedIndex)
                        centerItems.add(next, item)
                        pressedIndex = next
                        onOrderChanged(centerItems.toList())
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pointer = event.findPointerIndex(activePointerId)
                val index = if (pointer >= 0) indexAt(event.getX(pointer)) else -1
                val valid = index == pressedIndex && !reorderMode && !longPressTriggered
                val candidate = if (valid && centerMode == CenterMode.SUGGESTIONS) {
                    candidates.getOrNull(index)
                } else null
                val item = if (valid && centerMode == CenterMode.TOOLBAR && index >= 0) {
                    centerItems.getOrNull(index)
                } else null
                val edgeAction = if (valid) index else -1
                cancelTouch()
                if (candidate != null) onCandidateSelected(candidate)
                else if (item != null) onToolbarAction(item)
                else if (edgeAction == -2) onTools()
                else if (edgeAction == -3) onMic()
                performClick()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) cancelTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTouch()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelPendingInput() = cancelTouch()

    private fun cancelTouch() {
        handler.removeCallbacks(longPress)
        activePointerId = INVALID_POINTER
        pressedIndex = -1
        reorderMode = false
        longPressTriggered = false
        invalidate()
    }

    private fun indexAt(x: Float): Int {
        val edge = EDGE_WIDTH_DP * density
        if (x < edge) return -2
        if (x >= width - edge) return -3
        val slots = if (centerMode == CenterMode.SUGGESTIONS) {
            fixedSuggestionSlotCount
        } else {
            centerItems.size.coerceAtLeast(1)
        }
        return (((x - edge) / ((width - edge * 2f) / slots)).toInt())
            .coerceIn(0, slots - 1)
    }

    private fun iconFor(item: String): String = when (item) {
        "settings" -> "⚙"
        "search" -> "⌕"
        "resize" -> "↕"
        "language" -> "◎"
        "themes" -> "◉"
        "emoji" -> "☺"
        "clipboard" -> "▣"
        "undo" -> "↶"
        "redo" -> "↷"
        else -> "•"
    }

    private fun drawIcon(canvas: Canvas, icon: String, centerX: Float, pressed: Boolean) {
        if (pressed) canvas.drawRect(centerX - EDGE_WIDTH_DP * density / 2f, 0f, centerX + EDGE_WIDTH_DP * density / 2f, height.toFloat(), pressedPaint)
        canvas.drawText(icon, centerX, (height - (iconPaint.ascent() + iconPaint.descent())) / 2f, iconPaint)
    }

    private fun drawToolsIcon(canvas: Canvas, centerX: Float, pressed: Boolean) {
        if (pressed) {
            canvas.drawRect(centerX - EDGE_WIDTH_DP * density / 2f, 0f,
                centerX + EDGE_WIDTH_DP * density / 2f, height.toFloat(), pressedPaint)
        }
        iconStrokePaint.color = theme.textColor
        iconStrokePaint.style = Paint.Style.FILL
        val centerY = height / 2f
        val radius = 2.2f * density
        val spacing = 7f * density
        canvas.drawCircle(centerX, centerY - spacing, radius, iconStrokePaint)
        canvas.drawCircle(centerX, centerY, radius, iconStrokePaint)
        canvas.drawCircle(centerX, centerY + spacing, radius, iconStrokePaint)
        iconStrokePaint.style = Paint.Style.STROKE
    }

    private fun drawCloseIcon(canvas: Canvas, centerX: Float, pressed: Boolean) {
        if (pressed) {
            canvas.drawRect(centerX - EDGE_WIDTH_DP * density / 2f, 0f,
                centerX + EDGE_WIDTH_DP * density / 2f, height.toFloat(), pressedPaint)
        }
        iconStrokePaint.color = theme.textColor
        iconStrokePaint.style = Paint.Style.STROKE
        iconStrokePaint.strokeWidth = 2.6f * density
        val centerY = height / 2f
        val half = 10f * density
        canvas.drawLine(centerX - half, centerY - half, centerX + half, centerY + half, iconStrokePaint)
        canvas.drawLine(centerX + half, centerY - half, centerX - half, centerY + half, iconStrokePaint)
        iconStrokePaint.strokeWidth = 2.2f * density
    }

    private fun drawMicrophoneIcon(canvas: Canvas, centerX: Float, pressed: Boolean) {
        if (pressed) {
            canvas.drawRect(centerX - EDGE_WIDTH_DP * density / 2f, 0f,
                centerX + EDGE_WIDTH_DP * density / 2f, height.toFloat(), pressedPaint)
        }

        // Reference-shaped microphone inside a compact circular button. The circle is a
        // touch/visual container; the microphone itself remains a scalable vector.
        val scale = (height / (44f * density)).coerceIn(0.72f, 1f)
        val d = density * scale
        val centerY = height / 2f
        val circleRadius = (height / 2f - 1.5f * d).coerceAtLeast(10f * d)

        micButtonPaint.color = theme.keyColor
        micButtonPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, circleRadius, micButtonPaint)

        iconStrokePaint.color = theme.borderColor
        iconStrokePaint.style = Paint.Style.STROKE
        iconStrokePaint.strokeWidth = 1f * d
        canvas.drawCircle(centerX, centerY, circleRadius, iconStrokePaint)

        val bodyWidth = 7.2f * d
        val bodyTop = centerY - 16f * d
        val bodyBottom = centerY + 5f * d
        val arcLeft = centerX - 13.5f * d
        val arcTop = centerY - 7f * d
        val arcRight = centerX + 13.5f * d
        val arcBottom = centerY + 11f * d
        val stemBottom = centerY + 16f * d
        val baseHalfWidth = 8f * d

        fun drawReferenceGeometry() {
            canvas.drawRoundRect(
                centerX - bodyWidth,
                bodyTop,
                centerX + bodyWidth,
                bodyBottom,
                bodyWidth,
                bodyWidth,
                iconStrokePaint,
            )
            canvas.drawArc(
                arcLeft,
                arcTop,
                arcRight,
                arcBottom,
                0f,
                180f,
                false,
                iconStrokePaint,
            )
            canvas.drawLine(centerX, arcBottom, centerX, stemBottom, iconStrokePaint)
            canvas.drawLine(
                centerX - baseHalfWidth,
                stemBottom,
                centerX + baseHalfWidth,
                stemBottom,
                iconStrokePaint,
            )
        }

        // Black on light themes and white/light on dark themes through the existing
        // theme foreground system; no fixed purple and no bitmap.
        iconStrokePaint.color = theme.textColor
        iconStrokePaint.strokeCap = Paint.Cap.ROUND
        iconStrokePaint.strokeJoin = Paint.Join.ROUND

        iconStrokePaint.alpha = 42
        iconStrokePaint.strokeWidth = 5.2f * d
        drawReferenceGeometry()

        iconStrokePaint.alpha = 255
        iconStrokePaint.strokeWidth = 1.9f * d
        drawReferenceGeometry()
        iconStrokePaint.strokeWidth = 2.2f * density
    }

    private fun drawText(canvas: Canvas, value: String, left: Float, right: Float) {
        val horizontalPadding = 8f * density
        val available = (right - left - horizontalPadding * 2f).roundToInt().coerceAtLeast(1)
        val fitted = SuggestionDisplay.fit(
            fullText = value,
            availableWidthPx = available.toFloat(),
            baseTextSizePx = 23f * fontScale().coerceIn(0.75f, 1.35f) * scaledDensity,
            scaledDensity = scaledDensity,
            textPaint = textPaint,
        )
        textPaint.textSize = fitted.textSizePx
        textPaint.textScaleX = fitted.textScaleX
        textPaint.textAlign = Paint.Align.CENTER
        canvas.save()
        canvas.clipRect(left, 0f, right, height.toFloat())
        val centerX = (left + right) / 2f
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(fitted.text, centerX, baseline, textPaint)
        canvas.restore()
        textPaint.textScaleX = 1f
    }

    private fun containsRtl(value: String): Boolean = value.any { it.code in 0x0590..0x08FF }

    private companion object {
        const val DEFAULT_SUGGESTION_SLOT_COUNT = 3
        const val EDGE_TOOLS = -2
        const val EDGE_MIC = -3
        const val EDGE_WIDTH_DP = 46f
        // Keep suggestions at a clearly readable keyboard-label size. This changes only the
        // text paint; the fixed toolbar height and touch geometry remain unchanged.
        const val SUGGESTION_TEXT_SIZE_SP = 23f
        const val LONG_PRESS_MILLIS = 800L
        const val INVALID_POINTER = -1
        val BACKGROUND_COLOR = Color.rgb(248, 249, 251)
        val PRESSED_COLOR = Color.rgb(220, 229, 255)
        val DIVIDER_COLOR = Color.rgb(220, 223, 230)
        val TEXT_COLOR = Color.rgb(32, 33, 36)
        val SUPPORTED_TOOLBAR_ITEMS = setOf(
            "settings", "search", "resize", "language", "themes", "emoji", "clipboard", "undo", "redo",
        )
    }

    private enum class CenterMode { TOOLBAR, SUGGESTIONS }
}
