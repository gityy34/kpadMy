package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Lightweight overlay; the keyboard remains visible underneath while height changes live. */
class ResizePanelView(
    context: Context,
    initialHeightDp: Int,
    private val onHeightChanged: (Int) -> Unit,
    private val onDone: () -> Unit,
    private val onCancel: () -> Unit,
    private val onReset: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var heightDp = initialHeightDp
    private var dragging = false

    init { setBackgroundColor(Color.TRANSPARENT) }

    fun setHeightDp(value: Int) {
        heightDp = value.coerceIn(260, 360)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val handleY = 34f * density
        paint.color = Color.argb(220, 30, 35, 48)
        canvas.drawRoundRect(width / 2f - 42f * density, handleY - 4f * density, width / 2f + 42f * density, handleY + 4f * density, 8f, 8f, paint)
        paint.textSize = 13f * density
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Resize  ${heightDp}dp", width / 2f, handleY + 28f * density, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Reset", 16f * density, height - 18f * density, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Cancel     Done", width - 16f * density, height - 18f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = event.y < 90f * density
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val next = (360f - ((event.y / height.coerceAtLeast(1)) * 100f)).toInt()
                        .coerceIn(260, 360)
                    heightDp = next
                    onHeightChanged(next)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) dragging = false
                else when {
                    event.y > height - 52f * density && event.x < 80f * density -> onReset()
                    event.y > height - 52f * density && event.x > width - 150f * density && event.x < width - 70f * density -> onCancel()
                    event.y > height - 52f * density && event.x >= width - 70f * density -> onDone()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
