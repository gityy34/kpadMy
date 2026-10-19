package com.example.aikeyboard.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.ceil

class EmojiPanelView(
    context: Context,
    private val usage: EmojiUsageStore,
    private val onEmojiSelected: (String) -> Unit,
    private val onBack: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var categoryIndex = 2
    private var searchQuery = ""
    private var scroll = 0
    private var theme = KeyboardTheme.SOLID_WHITE
    private var downY = 0f
    private var moved = false

    fun setTheme(value: KeyboardTheme) { theme = value; invalidate() }

    fun setSearchQuery(value: String) {
        searchQuery = value
        scroll = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)
        val tabHeight = 42f * density
        paint.color = theme.keyColor
        canvas.drawRect(0f, 0f, width.toFloat(), tabHeight, paint)
        paint.color = theme.textColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 17f * density
        EmojiCatalog.categories.forEachIndexed { index, category ->
            val x = (index + 0.5f) * width / EmojiCatalog.categories.size
            canvas.drawText(category.title.first().toString(), x, 27f * density, paint)
            if (index == categoryIndex) {
                paint.color = theme.accentColor
                canvas.drawRect(x - 12f * density, tabHeight - 3f * density, x + 12f * density, tabHeight, paint)
                paint.color = theme.textColor
            }
        }
        val values = currentEmoji()
        val columns = 8
        val cellWidth = width / columns.toFloat()
        val cellHeight = 42f * density
        val startY = tabHeight + 4f * density
        val visibleRows = ceil((height - startY - 42f * density) / cellHeight).toInt().coerceAtLeast(1)
        val first = scroll * columns
        val last = (first + visibleRows * columns).coerceAtMost(values.size)
        paint.textSize = 26f * density
        values.subList(first, last).forEachIndexed { offset, emoji ->
            val column = offset % columns
            val row = offset / columns
            val x = cellWidth * (column + 0.5f)
            val y = startY + row * cellHeight + 31f * density
            canvas.drawText(emoji, x, y, paint)
        }
        paint.color = theme.keyColor
        canvas.drawRect(0f, height - 42f * density, width.toFloat(), height.toFloat(), paint)
        paint.color = theme.textColor
        paint.textSize = 14f * density
        canvas.drawText("Back to Keyboard", width / 2f, height - 15f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; moved = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.y - downY) > 12f * density) moved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moved) {
                    val delta = ((downY - event.y) / (42f * density)).toInt()
                    scroll = (scroll + delta).coerceAtLeast(0)
                    invalidate()
                    return true
                }
                if (event.y >= height - 48f * density) { onBack(); return true }
                val tabHeight = 42f * density
                if (event.y < tabHeight) {
                    categoryIndex = (event.x / (width / EmojiCatalog.categories.size.toFloat())).toInt()
                        .coerceIn(0, EmojiCatalog.categories.lastIndex)
                    scroll = 0
                    invalidate()
                    return true
                }
                val values = currentEmoji()
                val columns = 8
                val cellWidth = width / columns.toFloat()
                val cellHeight = 42f * density
                val row = ((event.y - tabHeight - 4f * density) / cellHeight).toInt()
                val column = (event.x / cellWidth).toInt()
                val index = (scroll + row) * columns + column
                values.getOrNull(index)?.let { onEmojiSelected(it) }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { moved = false; return true }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun currentEmoji(): List<String> {
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            return EmojiCatalog.keywords.filterKeys { it.contains(query) }.values.flatten().distinct()
        }
        return when (EmojiCatalog.categories[categoryIndex].id) {
            "recent" -> usage.recent()
            "frequent" -> usage.frequent()
            else -> EmojiCatalog.categories[categoryIndex].emoji
        }
    }
}
