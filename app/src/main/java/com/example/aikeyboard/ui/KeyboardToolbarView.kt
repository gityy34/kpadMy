package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.prediction.SuggestionCandidate
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
) : View(context) {

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND_COLOR }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRESSED_COLOR }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIVIDER_COLOR }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = 22f * density
        textAlign = Paint.Align.CENTER
    }
    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable {
        val item = centerItems.getOrNull(pressedIndex)
        if (item != null && candidates.isEmpty()) {
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
    private var activePointerId = INVALID_POINTER
    private var pressedIndex = -1
    private var reorderMode = false
    private var longPressTriggered = false

    init {
        isClickable = true
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun setCandidates(value: List<SuggestionCandidate>) {
        candidates = value.take(SLOT_COUNT)
        invalidate()
    }

    fun clearCandidates() {
        if (candidates.isEmpty()) return
        candidates = emptyList()
        invalidate()
    }

    fun setToolsMode(enabled: Boolean) {
        if (enabled) candidates = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val edge = EDGE_WIDTH_DP * density
        drawIcon(canvas, "☷", edge / 2f, "tools" == pressedAction())
        drawIcon(canvas, "●", width - edge / 2f, "mic" == pressedAction())
        canvas.drawRect(edge - 1f, 0f, edge, height.toFloat(), dividerPaint)
        canvas.drawRect(width - edge, 0f, width - edge + 1f, height.toFloat(), dividerPaint)

        val centerLeft = edge
        val centerRight = width - edge
        val centerWidth = (centerRight - centerLeft) / SLOT_COUNT
        if (candidates.isNotEmpty()) {
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
                if (reorderMode && candidates.isEmpty()) {
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
                val candidate = candidates.getOrNull(if (valid) index else -1)
                val item = if (candidates.isEmpty()) centerItems.getOrNull(if (valid) index else -1) else null
                val edgeAction = if (valid && candidates.isEmpty()) index else -1
                cancelTouch()
                if (candidate != null) onCandidateSelected(candidate)
                else if (item != null) onToolbarAction(item)
                else if (edgeAction == -2) onTools()
                else if (edgeAction == -3) onMic()
                performClick()
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

    private fun pressedAction(): String? = null

    private fun indexAt(x: Float): Int {
        val edge = EDGE_WIDTH_DP * density
        if (x < edge) return -2
        if (x > width - edge) return -3
        return (((x - edge) / ((width - edge * 2f) / SLOT_COUNT)).toInt())
            .coerceIn(0, SLOT_COUNT - 1)
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

    private fun drawText(canvas: Canvas, value: String, left: Float, right: Float, rtl: Boolean) {
        val available = (right - left - 16f * density).roundToInt().coerceAtLeast(1)
        val direction = if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, available)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(1)
            .setTextDirection(direction).build()
        canvas.save()
        canvas.translate(left + 8f * density, ((height - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(canvas)
        canvas.restore()
    }

    private fun containsRtl(value: String): Boolean = value.any { it.code in 0x0590..0x08FF }

    private companion object {
        const val SLOT_COUNT = 3
        const val EDGE_WIDTH_DP = 56f
        const val LONG_PRESS_MILLIS = 800L
        const val INVALID_POINTER = -1
        val BACKGROUND_COLOR = Color.rgb(248, 249, 251)
        val PRESSED_COLOR = Color.rgb(220, 229, 255)
        val DIVIDER_COLOR = Color.rgb(220, 223, 230)
        val TEXT_COLOR = Color.rgb(32, 33, 36)
    }
}
