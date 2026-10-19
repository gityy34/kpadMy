package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class ToolsPanelView(
    context: Context,
    private val onToolSelected: (String) -> Unit,
) : View(context) {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(243, 244, 246) }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 33, 36)
        textSize = 15f * resources.displayMetrics.density
        textAlign = Paint.Align.LEFT
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 67, 100)
        textSize = 21f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val items = listOf(
        "settings" to ("⚙" to "Settings"),
        "search" to ("⌕" to "Search settings"),
        "resize" to ("↕" to "Resize"),
        "undo" to ("↶" to "Undo"),
        "redo" to ("↷" to "Redo"),
        "themes" to ("◈" to "Themes"),
        "emoji" to ("☺" to "Emoji"),
        "ai" to ("✦" to "AI tools"),
    )
    private var pressed = -1

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        val gap = 8f * resources.displayMetrics.density
        val cardWidth = (width - gap * 3f) / 2f
        val cardHeight = 58f * resources.displayMetrics.density
        items.forEachIndexed { index, item ->
            val column = index % 2
            val row = index / 2
            val left = gap + column * (cardWidth + gap)
            val top = gap + row * (cardHeight + gap)
            card.color = if (index == pressed) Color.rgb(225, 232, 250) else Color.WHITE
            canvas.drawRoundRect(left, top, left + cardWidth, top + cardHeight, 12f, 12f, card)
            icon.textAlign = Paint.Align.CENTER
            canvas.drawText(item.second.first, left + 28f * resources.displayMetrics.density, top + cardHeight / 2f + 8f, icon)
            text.textSize = 15f * resources.displayMetrics.density
            canvas.drawText(item.second.second, left + 52f * resources.displayMetrics.density, top + cardHeight / 2f + 5f, text)
            canvas.drawText("›", left + cardWidth - 18f * resources.displayMetrics.density, top + cardHeight / 2f + 7f, icon)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = indexAt(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val item = indexAt(event.x, event.y)
                val selected = if (item == pressed) items.getOrNull(item)?.first else null
                pressed = -1
                invalidate()
                selected?.let(onToolSelected)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun indexAt(x: Float, y: Float): Int {
        val gap = 8f * resources.displayMetrics.density
        val cardWidth = (width - gap * 3f) / 2f
        val cardHeight = 58f * resources.displayMetrics.density
        val column = ((x - gap) / (cardWidth + gap)).toInt()
        val row = ((y - gap) / (cardHeight + gap)).toInt()
        if (column !in 0..1 || row < 0) return -1
        val index = row * 2 + column
        return if (index in items.indices) index else -1
    }
}
