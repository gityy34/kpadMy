package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.settings.ResizeConfiguration
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Resize-only interaction layer.
 *
 * It is attached to the complete keyboard workspace, including the reserved safe area. This is
 * important: the lower handle must never be clipped at the old content height or fall into the
 * Android navigation region.
 */
class ResizePanelView(
    context: Context,
    initialConfiguration: ResizeConfiguration,
    private val onConfigurationChanged: (ResizeConfiguration) -> Unit,
    private val onDone: (ResizeConfiguration) -> Unit,
    private val onCancel: () -> Unit,
    private val onReset: () -> Unit,
    private val visualKeyBlockHeightDp: (Int) -> Int = {
        (it - INTERNAL_BOTTOM_ROOM_DP).coerceAtLeast(1)
    },
    private val bottomBoundaryRangeDp: (ResizeConfiguration) -> IntRange = {
        ResizeConfiguration.MIN_BOTTOM_BOUNDARY_OFFSET_DP..
            ResizeConfiguration.MAX_BOTTOM_BOUNDARY_OFFSET_DP
    },
) : View(context) {

    private enum class DragTarget { NONE, TOP, BOTTOM, MOVE, RESET, DONE }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val resetBounds = RectF()
    private val moveBounds = RectF()
    private val doneBounds = RectF()
    private var configuration = normalize(initialConfiguration)
    private var startConfiguration = configuration
    private var dragTarget = DragTarget.NONE
    private var startY = 0f
    private var gestureActive = false
    private var theme = KeyboardTheme.NEON

    init {
        isClickable = true
        isFocusable = true
        // Keep Back routed through this overlay after it is attached. Calling requestFocus
        // before addView can be ignored by Android, leaving the IME Back path inconsistent.
        post {
            if (isAttachedToWindow) requestFocus()
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setConfiguration(value: ResizeConfiguration) {
        configuration = normalize(value)
        invalidate()
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val top = configuration.topOffsetDp * density
        val visualBlock = visualKeyBlockHeightDp(configuration.keyboardHeightDp)
        val bottom = top +
            (visualBlock + configuration.bottomBoundaryOffsetDp) * density
        bounds.set(0f, top, width.toFloat(), bottom)

        // Dim the entire bounded workspace, not the host application. The live keyboard remains
        // visible underneath so the user can judge the result while typing is disabled by this
        // overlay.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(118, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(82, 255, 255, 255)
        canvas.drawRoundRect(bounds, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = theme.accentColor
        canvas.drawRoundRect(bounds, 14f * density, 14f * density, paint)

        drawHandle(canvas, top.coerceIn(8f * density, height - 8f * density))
        drawHandle(canvas, bottom.coerceIn(8f * density, height - 8f * density))

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 13f * density
        paint.color = theme.textColor
        canvas.drawText(
            "Resize  " + configuration.keyboardHeightDp + "dp",
            width / 2f,
            (top + 26f * density).coerceIn(18f * density, height - 8f * density),
            paint,
        )

        val centerY = ((top + bottom) / 2f + 18f * density)
            .coerceIn(top + 52f * density, bottom - 42f * density)
            .coerceIn(18f * density, height - 22f * density)
        calculateButtonBounds(centerY)
        drawButton(canvas, resetBounds, "Reset", dragTarget == DragTarget.RESET)
        drawButton(canvas, moveBounds, "Move", dragTarget == DragTarget.MOVE)
        drawButton(canvas, doneBounds, "Done", dragTarget == DragTarget.DONE)

        if (configuration.bottomBoundaryOffsetDp != 0 || configuration.bottomSpaceDp > 0) {
            paint.textSize = 11f * density
            paint.color = theme.textColor
            canvas.drawText(
                "Blank space " + configuration.bottomSpaceDp + "dp",
                width / 2f,
                (bottom + 18f * density).coerceIn(18f * density, height - 8f * density),
                paint,
            )
        }

        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
    }

    private fun drawHandle(canvas: Canvas, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = theme.accentColor
        canvas.drawRoundRect(
            width / 2f - 42f * density,
            y - 5f * density,
            width / 2f + 42f * density,
            y + 5f * density,
            7f * density,
            7f * density,
            paint,
        )
        paint.color = Color.argb(210, 255, 255, 255)
        canvas.drawCircle(width / 2f, y, 3f * density, paint)
    }

    private fun calculateButtonBounds(centerY: Float) {
        val buttonWidth = 76f * density
        val buttonHeight = 38f * density
        val gap = 8f * density
        val left = (width - buttonWidth * 3f - gap * 2f) / 2f
        resetBounds.set(left, centerY - buttonHeight / 2f, left + buttonWidth, centerY + buttonHeight / 2f)
        moveBounds.set(
            resetBounds.right + gap,
            resetBounds.top,
            resetBounds.right + gap + buttonWidth,
            resetBounds.bottom,
        )
        doneBounds.set(
            moveBounds.right + gap,
            resetBounds.top,
            moveBounds.right + gap + buttonWidth,
            moveBounds.bottom,
        )
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (pressed) theme.pressedKeyColor else theme.specialKeyColor
        canvas.drawRoundRect(rect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = theme.borderColor
        canvas.drawRoundRect(rect, 18f * density, 18f * density, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * density
        paint.color = theme.textColor
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() - (paint.ascent() + paint.descent()) / 2f,
            paint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureActive = true
                startY = event.y
                startConfiguration = configuration
                // Recompute bounds before hit-testing so an immediate tap after opening cannot
                // miss Done/Reset simply because the first draw has not completed yet.
                val top = startConfiguration.topOffsetDp * density
                val bottom = top + (
                    visualKeyBlockHeightDp(startConfiguration.keyboardHeightDp) +
                        startConfiguration.bottomBoundaryOffsetDp
                    ) * density
                calculateButtonBounds(
                    ((top + bottom) / 2f + 18f * density)
                        .coerceIn(top + 52f * density, bottom - 42f * density)
                        .coerceIn(18f * density, height - 22f * density),
                )
                val topHandle = top.coerceIn(8f * density, height - 8f * density)
                val bottomHandle = bottom.coerceIn(8f * density, height - 8f * density)
                dragTarget = when {
                    resetBounds.contains(event.x, event.y) -> DragTarget.RESET
                    moveBounds.contains(event.x, event.y) -> DragTarget.MOVE
                    doneBounds.contains(event.x, event.y) -> DragTarget.DONE
                    nearHandle(event.y, topHandle) -> DragTarget.TOP
                    nearHandle(event.y, bottomHandle) -> DragTarget.BOTTOM
                    else -> DragTarget.NONE
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureActive) return true
                val deltaDp = ((startY - event.y) / density).roundToInt()
                configuration = when (dragTarget) {
                    DragTarget.TOP -> resizeFromTop(deltaDp)
                    DragTarget.BOTTOM -> moveBottomBoundary(deltaDp)
                    DragTarget.MOVE -> moveVertically(deltaDp)
                    else -> configuration
                }
                if (dragTarget == DragTarget.TOP ||
                    dragTarget == DragTarget.BOTTOM ||
                    dragTarget == DragTarget.MOVE
                ) {
                    onConfigurationChanged(configuration)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return true
                val completedTarget = dragTarget
                dragTarget = DragTarget.NONE
                gestureActive = false
                invalidate()
                when (completedTarget) {
                    DragTarget.RESET -> onReset()
                    DragTarget.DONE -> onDone(configuration)
                    else -> Unit
                }
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Android may cancel this pointer stream when the live preview changes the IME
                // window/layout. That is a gesture cancellation, not a request to leave Resize.
                // Reverting only this gesture keeps Reset/Move/Done usable for the next touch.
                val cancelledStart = startConfiguration
                dragTarget = DragTarget.NONE
                gestureActive = false
                configuration = cancelledStart
                post {
                    if (isAttachedToWindow) onConfigurationChanged(cancelledStart)
                }
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                gestureActive = false
                dragTarget = DragTarget.NONE
                onCancel()
                return true
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private fun nearHandle(y: Float, handleY: Float): Boolean =
        kotlin.math.abs(y - handleY) <= 30f * density

    private fun resizeFromTop(deltaDp: Int): ResizeConfiguration {
        val startBottom = startConfiguration.topOffsetDp +
            startConfiguration.keyboardHeightDp +
            startConfiguration.bottomSpaceDp
        val height = (startConfiguration.keyboardHeightDp + deltaDp)
            .coerceIn(
                ResizeConfiguration.MIN_KEYBOARD_HEIGHT_DP,
                ResizeConfiguration.MAX_KEYBOARD_HEIGHT_DP,
            )
        val top = (startBottom - startConfiguration.bottomSpaceDp - height).coerceAtLeast(0)
        return normalize(startConfiguration.copy(keyboardHeightDp = height, topOffsetDp = top))
    }

    private fun moveBottomBoundary(deltaDp: Int): ResizeConfiguration {
        val range = bottomBoundaryRangeDp(startConfiguration)
        val requested = startConfiguration.bottomBoundaryOffsetDp - deltaDp
        return normalize(
            startConfiguration.copy(
                bottomBoundaryOffsetDp = requested.coerceIn(range.first, range.last),
            ),
        )
    }

    private fun moveVertically(deltaDp: Int): ResizeConfiguration =
        normalize(
            startConfiguration.copy(
                topOffsetDp = (startConfiguration.topOffsetDp - deltaDp)
                    .coerceIn(0, ResizeConfiguration.MAX_MOVE_OFFSET_DP),
            ),
        )

    private fun normalize(value: ResizeConfiguration): ResizeConfiguration {
        val clamped = value.clamped()
        val range = bottomBoundaryRangeDp(clamped)
        return clamped.copy(
            bottomBoundaryOffsetDp = clamped.bottomBoundaryOffsetDp.coerceIn(
                range.first,
                range.last,
            ),
        )
    }

    override fun onDetachedFromWindow() {
        gestureActive = false
        dragTarget = DragTarget.NONE
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val INTERNAL_BOTTOM_ROOM_DP = 55
    }
}
