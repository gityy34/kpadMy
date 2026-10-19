package com.example.aikeyboard.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class ThemePreviewView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var theme = KeyboardTheme.SOLID_WHITE

    fun setTheme(value: KeyboardTheme) { theme = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)
        paint.color = theme.textColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 16f * resources.displayMetrics.density
        canvas.drawText(theme.name, width / 2f, 28f * resources.displayMetrics.density, paint)
        val gap = 6f * resources.displayMetrics.density
        val keyWidth = (width - gap * 5f) / 4f
        for (index in 0 until 4) {
            paint.color = theme.keyColor
            val left = gap + index * (keyWidth + gap)
            canvas.drawRoundRect(left, 44f * resources.displayMetrics.density, left + keyWidth, 92f * resources.displayMetrics.density, 8f, 8f, paint)
            paint.color = theme.textColor
            canvas.drawText("ASDF"[index].toString(), left + keyWidth / 2f, 74f * resources.displayMetrics.density, paint)
        }
        paint.color = theme.accentColor
        canvas.drawRect(0f, 104f * resources.displayMetrics.density, width.toFloat(), 108f * resources.displayMetrics.density, paint)
    }
}
