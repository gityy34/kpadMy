package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper
import com.example.aikeyboard.prediction.SuggestionCandidate
import kotlin.math.roundToInt

/**
 * Fixed-height, low-work candidate strip. It never recreates the keyboard and every slot owns
 * its complete rectangle so candidate selection does not depend on a small label hit target.
 */
class SuggestionStripView(
    context: Context,
    private val slotCount: Int = DEFAULT_SLOT_COUNT,
    private val onCandidateSelected: (SuggestionCandidate) -> Unit,
    private val onCandidateLongPressed: (SuggestionCandidate) -> Unit = {},
) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIVIDER_COLOR }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND_COLOR }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRESSED_COLOR }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE_SP * scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var candidates: List<SuggestionCandidate> = emptyList()
    private var activePointerId = INVALID_POINTER_ID
    private var pressedSlot = -1
    private var longPressTriggered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val candidate = candidates.getOrNull(pressedSlot)
        if (candidate?.isPersonal == true && pressedSlot >= 0) {
            longPressTriggered = true
            onCandidateLongPressed(candidate)
            invalidate()
        }
    }

    init {
        isClickable = true
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun setCandidates(value: List<SuggestionCandidate>) {
        setCandidates(exactTyped = null, value = value)
    }

    fun setCandidates(
        exactTyped: SuggestionCandidate?,
        value: List<SuggestionCandidate>,
    ) {
        val next = ArrayList<SuggestionCandidate>(FIXED_SLOT_COUNT)
        exactTyped?.let { next += it }
        value.forEach { candidate ->
            if (next.size >= FIXED_SLOT_COUNT) return@forEach
            if (exactTyped == null || candidate.text != exactTyped.text) next += candidate
        }
        if (candidates == next) return
        candidates = next
        invalidate()
    }

    fun clearCandidates() {
        if (candidates.isEmpty()) return
        candidates = emptyList()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (DEFAULT_HEIGHT_DP * density).roundToInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (slotCount <= 0 || width <= 0) return

        val slotWidth = width.toFloat() / FIXED_SLOT_COUNT
        for (slot in 0 until FIXED_SLOT_COUNT) {
            val left = slot * slotWidth
            val right = if (slot == FIXED_SLOT_COUNT - 1) width.toFloat() else (slot + 1) * slotWidth
            if (slot == pressedSlot) canvas.drawRect(left, 0f, right, height.toFloat(), pressedPaint)
            if (slot < FIXED_SLOT_COUNT - 1) {
                canvas.drawRect(right - DIVIDER_WIDTH_DP * density, 0f, right, height.toFloat(), dividerPaint)
            }
            candidates.getOrNull(slot)?.let { drawCandidate(canvas, it.text, left, right) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture()
                activePointerId = event.getPointerId(0)
                pressedSlot = slotAt(event.x)
                longPressTriggered = false
                if (candidates.getOrNull(pressedSlot)?.isPersonal == true) {
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MILLIS)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> return true

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    cancelGesture()
                } else {
                    val slot = slotAt(event.getX(index))
                    if (slot != pressedSlot) {
                        mainHandler.removeCallbacks(longPressRunnable)
                        pressedSlot = -1
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) cancelGesture()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(activePointerId)
                val slot = if (index >= 0) slotAt(event.getX(index)) else -1
                val candidate = candidates.getOrNull(if (slot == pressedSlot && !longPressTriggered) slot else -1)
                cancelGesture()
                if (candidate != null) onCandidateSelected(candidate)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelPendingInput() = cancelGesture()

    private fun drawCandidate(canvas: Canvas, text: String, left: Float, right: Float) {
        val horizontalPadding = 8f * density
        val availableWidth = (right - left - horizontalPadding * 2f).coerceAtLeast(1f)
        if (availableWidth <= 0f || text.isEmpty()) return
        val fitted = SuggestionDisplay.fit(
            fullText = text,
            availableWidthPx = availableWidth,
            baseTextSizePx = TEXT_SIZE_SP * scaledDensity,
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

    private fun containsRtl(text: String): Boolean = text.any { character ->
        character.code in 0x0590..0x08FF || character.code in 0xFB1D..0xFDFF
    }

    private fun slotAt(x: Float): Int {
        if (slotCount <= 0 || width <= 0 || x < 0f || x > width.toFloat()) return -1
        return (x / (width.toFloat() / FIXED_SLOT_COUNT)).toInt().coerceIn(0, FIXED_SLOT_COUNT - 1)
    }

    private fun cancelGesture() {
        mainHandler.removeCallbacks(longPressRunnable)
        activePointerId = INVALID_POINTER_ID
        pressedSlot = -1
        longPressTriggered = false
        invalidate()
    }

    private companion object {
        const val DEFAULT_SLOT_COUNT = 3
        const val FIXED_SLOT_COUNT = 3
        // Keep the strip stable and close to a normal Gboard-sized candidate row.
        const val DEFAULT_HEIGHT_DP = 48
        const val TEXT_SIZE_SP = 23f
        const val DIVIDER_WIDTH_DP = 1f
        const val INVALID_POINTER_ID = -1
        const val LONG_PRESS_MILLIS = 800L
        val BACKGROUND_COLOR = Color.rgb(248, 249, 251)
        val PRESSED_COLOR = Color.rgb(220, 229, 255)
        val DIVIDER_COLOR = Color.rgb(220, 223, 230)
        val TEXT_COLOR = Color.rgb(32, 33, 36)
    }
}
