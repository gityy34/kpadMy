package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        textSize = 22f * density
        textAlign = Paint.Align.CENTER
    }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var theme = KeyboardTheme.SOLID_WHITE
    private val suggestionSlotCount = suggestionSlotCount.coerceIn(3, 4)
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
    private var activePointerId = INVALID_POINTER
    private var pressedIndex = -1
    private var reorderMode = false
    private var longPressTriggered = false

    init {
        isClickable = true
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        backgroundPaint.color = value.backgroundColor
        pressedPaint.color = value.pressedKeyColor
        dividerPaint.color = value.borderColor
        textPaint.color = value.textColor
        iconPaint.color = value.textColor
        invalidate()
    }

    fun setCandidates(value: List<SuggestionCandidate>) {
        if (centerMode != CenterMode.SUGGESTIONS) return
        candidates = value.take(suggestionSlotCount)
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

    fun showSuggestions() {
        centerMode = CenterMode.SUGGESTIONS
        invalidate()
    }

    fun isSuggestionsMode(): Boolean = centerMode == CenterMode.SUGGESTIONS

    /** Compatibility boundary for existing callers entering a non-keyboard panel. */
    fun setToolsMode(enabled: Boolean) {
        if (enabled) showToolbarActions() else invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val edge = EDGE_WIDTH_DP * density
        drawToolsIcon(canvas, edge / 2f, pressedIndex == EDGE_TOOLS)
        drawMicrophoneIcon(canvas, width - edge / 2f, pressedIndex == EDGE_MIC)
        canvas.drawRect(edge - 1f, 0f, edge, height.toFloat(), dividerPaint)
        canvas.drawRect(width - edge, 0f, width - edge + 1f, height.toFloat(), dividerPaint)

        val centerLeft = edge
        val centerRight = width - edge
        val slots = if (centerMode == CenterMode.SUGGESTIONS) {
            suggestionSlotCount
        } else {
            centerItems.size.coerceAtLeast(1)
        }
        val centerWidth = (centerRight - centerLeft) / slots
        if (centerMode == CenterMode.SUGGESTIONS) {
            candidates.forEachIndexed { index, candidate ->
                val left = centerLeft + centerWidth * index
                if (index == pressedIndex) canvas.drawRect(left, 0f, left + centerWidth, height.toFloat(), pressedPaint)
                drawText(canvas, candidate.text, left, left + centerWidth, containsRtl(candidate.text))
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
            suggestionSlotCount
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
        val half = 10f * density
        val knob = 2.5f * density
        val y1 = height / 2f - 8f * density
        val y2 = height / 2f
        val y3 = height / 2f + 8f * density
        canvas.drawLine(centerX - half, y1, centerX + half, y1, iconStrokePaint)
        canvas.drawLine(centerX - half, y2, centerX + half, y2, iconStrokePaint)
        canvas.drawLine(centerX - half, y3, centerX + half, y3, iconStrokePaint)
        iconStrokePaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX - 3f * density, y1, knob, iconStrokePaint)
        canvas.drawCircle(centerX + 4f * density, y2, knob, iconStrokePaint)
        canvas.drawCircle(centerX - 1f * density, y3, knob, iconStrokePaint)
        iconStrokePaint.style = Paint.Style.STROKE
    }

    private fun drawMicrophoneIcon(canvas: Canvas, centerX: Float, pressed: Boolean) {
        if (pressed) {
            canvas.drawRect(centerX - EDGE_WIDTH_DP * density / 2f, 0f,
                centerX + EDGE_WIDTH_DP * density / 2f, height.toFloat(), pressedPaint)
        }
        iconStrokePaint.color = theme.textColor
        val centerY = height / 2f
        val capsuleWidth = 7f * density
        val capsuleTop = centerY - 13f * density
        val capsuleBottom = centerY + 3f * density
        iconStrokePaint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            centerX - capsuleWidth,
            capsuleTop,
            centerX + capsuleWidth,
            capsuleBottom,
            capsuleWidth,
            capsuleWidth,
            iconStrokePaint,
        )
        iconStrokePaint.style = Paint.Style.STROKE
        iconStrokePaint.strokeWidth = 2.6f * density
        val arcLeft = centerX - 13f * density
        val arcTop = centerY - 5f * density
        val arcRight = centerX + 13f * density
        val arcBottom = centerY + 10f * density
        canvas.drawArc(arcLeft, arcTop, arcRight, arcBottom, 0f, 180f, false, iconStrokePaint)
        canvas.drawLine(centerX, arcBottom, centerX, centerY + 16f * density, iconStrokePaint)
        canvas.drawLine(centerX - 8f * density, centerY + 16f * density,
            centerX + 8f * density, centerY + 16f * density, iconStrokePaint)
        iconStrokePaint.strokeWidth = 2.2f * density
    }

    private fun drawText(canvas: Canvas, value: String, left: Float, right: Float, rtl: Boolean) {
        val available = (right - left - 16f * density).roundToInt().coerceAtLeast(1)
        val direction = if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        // Keep normal suggestions readable. Longer words step down through a few sizes before
        // ellipsis is used; they never shrink to an unreadable label.
        val sizes = floatArrayOf(23f, 21f, 19f, 18f)
        var selectedSize = sizes.last()
        for (size in sizes) {
            textPaint.textSize = size * scaledDensity
            selectedSize = size
            if (textPaint.measureText(value) <= available.toFloat()) break
        }
        textPaint.textSize = selectedSize * scaledDensity
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, available)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(direction).build()
        canvas.save()
        // The center cells are a hard visual boundary. This prevents long LTR/RTL labels from
        // ever drawing over the permanently reserved Tools and microphone cells.
        canvas.clipRect(left, 0f, right, height.toFloat())
        canvas.translate(left + 8f * density, ((height - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(canvas)
        canvas.restore()
    }

    private fun containsRtl(value: String): Boolean = value.any { it.code in 0x0590..0x08FF }

    private companion object {
        const val DEFAULT_SUGGESTION_SLOT_COUNT = 3
        const val EDGE_TOOLS = -2
        const val EDGE_MIC = -3
        const val EDGE_WIDTH_DP = 56f
        // Keep suggestions at a clearly readable keyboard-label size. This changes only the
        // text paint; the fixed toolbar height and touch geometry remain unchanged.
        const val SUGGESTION_TEXT_SIZE_SP = 23f
        const val LONG_PRESS_MILLIS = 800L
        const val INVALID_POINTER = -1
        val BACKGROUND_COLOR = Color.rgb(248, 249, 251)
        val PRESSED_COLOR = Color.rgb(220, 229, 255)
        val DIVIDER_COLOR = Color.rgb(220, 223, 230)
        val TEXT_COLOR = Color.rgb(32, 33, 36)
    }

    private enum class CenterMode { TOOLBAR, SUGGESTIONS }
}
