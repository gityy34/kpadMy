package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.settings.ResizeConfiguration
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Resize-only interaction layer.
 *
 * It sits above the live KeyboardView, fades the preview, owns all touch input while active,
 * and emits geometry drafts without touching the text/input path.
 */
class ResizePanelView(
    context: Context,
    initialConfiguration: ResizeConfiguration,
    private val onConfigurationChanged: (ResizeConfiguration) -> Unit,
    private val onDone: () -> Unit,
    private val onCancel: () -> Unit,
    private val onReset: () -> Unit,
) : View(context) {
    private enum class DragTarget { NONE, TOP, BOTTOM, MOVE, RESET, DONE }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val resetBounds = RectF()
    private val moveBounds = RectF()
    private val doneBounds = RectF()
    private var configuration = initialConfiguration.clamped()
    private var startConfiguration = configuration
    private var dragTarget = DragTarget.NONE
    private var startY = 0f
    private var theme = KeyboardTheme.NEON

    init {
        isClickable = true
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setConfiguration(value: ResizeConfiguration) {
        configuration = value.clamped()
        invalidate()
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val top = configuration.topOffsetDp * density
        val bottom = top + configuration.keyboardHeightDp * density
        bounds.set(0f, top, width.toFloat(), bottom)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(118, 255, 255, 255)
        canvas.drawRoundRect(bounds, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = theme.accentColor
        canvas.drawRoundRect(bounds, 14f * density, 14f * density, paint)

        drawHandle(canvas, top)
        drawHandle(canvas, bottom)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 13f * density
        paint.typeface = Paint().typeface
        paint.color = theme.textColor
        canvas.drawText(
            "Resize  ${configuration.keyboardHeightDp}dp",
            width / 2f,
            (top + 26f * density).coerceAtMost(bottom - 18f * density),
            paint,
        )

        val centerY = ((top + bottom) / 2f + 18f * density)
            .coerceIn(top + 52f * density, bottom - 42f * density)
        calculateButtonBounds(centerY)
        drawButton(canvas, resetBounds, "Reset", dragTarget == DragTarget.RESET)
        drawButton(canvas, moveBounds, "Move", dragTarget == DragTarget.MOVE)
        drawButton(canvas, doneBounds, "Done", dragTarget == DragTarget.DONE)

        if (configuration.bottomSpaceDp > 0) {
            paint.textSize = 11f * density
            paint.color = theme.textColor
            canvas.drawText(
                "Blank space ${configuration.bottomSpaceDp}dp",
                width / 2f,
                (bottom + configuration.bottomSpaceDp * density / 2f)
                    .coerceAtMost(height - 8f * density),
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
            resetBounds.bottom,
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
        canvas.drawText(label, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                startConfiguration = configuration
                dragTarget = when {
                    nearHandle(event.y, startConfiguration.topOffsetDp * density) -> DragTarget.TOP
                    nearHandle(
                        event.y,
                        (startConfiguration.topOffsetDp + startConfiguration.keyboardHeightDp) * density,
                    ) -> DragTarget.BOTTOM
                    resetBounds.contains(event.x, event.y) -> DragTarget.RESET
                    moveBounds.contains(event.x, event.y) -> DragTarget.MOVE
                    doneBounds.contains(event.x, event.y) -> DragTarget.DONE
                    else -> DragTarget.NONE
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaDp = ((startY - event.y) / density).roundToInt()
                configuration = when (dragTarget) {
                    DragTarget.TOP -> resizeFromTop(deltaDp)
                    DragTarget.BOTTOM -> resizeFromBottom(deltaDp)
                    DragTarget.MOVE -> moveVertically(deltaDp)
                    else -> configuration
                }
                if (dragTarget == DragTarget.TOP || dragTarget == DragTarget.BOTTOM || dragTarget == DragTarget.MOVE) {
                    onConfigurationChanged(configuration)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                when (dragTarget) {
                    DragTarget.RESET -> onReset()
                    DragTarget.DONE -> onDone()
                    else -> Unit
                }
                dragTarget = DragTarget.NONE
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragTarget = DragTarget.NONE
                onCancel()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun nearHandle(y: Float, handleY: Float): Boolean =
        kotlin.math.abs(y - handleY) <= 30f * density

    private fun resizeFromTop(deltaDp: Int): ResizeConfiguration {
        val startBottom = startConfiguration.topOffsetDp +
            startConfiguration.keyboardHeightDp +
            startConfiguration.bottomSpaceDp
        val height = (startConfiguration.keyboardHeightDp + deltaDp)
            .coerceIn(ResizeConfiguration.MIN_KEYBOARD_HEIGHT_DP, ResizeConfiguration.MAX_KEYBOARD_HEIGHT_DP)
        val top = (startBottom - startConfiguration.bottomSpaceDp - height).coerceAtLeast(0)
        return startConfiguration.copy(keyboardHeightDp = height, topOffsetDp = top).clamped()
    }

    private fun resizeFromBottom(deltaDp: Int): ResizeConfiguration {
        val requestedSpace = (startConfiguration.bottomSpaceDp + deltaDp)
            .coerceIn(0, ResizeConfiguration.MAX_BOTTOM_SPACE_DP)
        val movementUp = requestedSpace - startConfiguration.bottomSpaceDp
        val top = (startConfiguration.topOffsetDp - movementUp).coerceAtLeast(0)
        return startConfiguration.copy(topOffsetDp = top, bottomSpaceDp = requestedSpace).clamped()
    }

    private fun moveVertically(deltaDp: Int): ResizeConfiguration =
        startConfiguration.copy(
            topOffsetDp = (startConfiguration.topOffsetDp + deltaDp)
                .coerceIn(0, ResizeConfiguration.MAX_MOVE_OFFSET_DP),
        ).clamped()

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
