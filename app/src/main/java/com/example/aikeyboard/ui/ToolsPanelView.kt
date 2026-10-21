package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.PopupMenu
import android.view.View
import com.example.aikeyboard.theme.KeyboardTheme
import kotlin.math.abs

/**
 * Compact, bounded tools grid. Scrolling is handled by this view so the IME window never needs
 * to grow when more tools are present than fit in the saved keyboard area.
 */
class ToolsPanelView(
    context: Context,
    private val onToolSelected: (String) -> Unit,
    private val isToolEnabled: (String) -> Boolean = { true },
    private val onAddToToolbar: (String) -> Unit = {},
    private val isToolInToolbar: (String) -> Boolean = { false },
) : View(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val item = items.getOrNull(pressed)
        if (item == null || dragging) return@Runnable
        longPressTriggered = true
        showToolActions(item.first)
        invalidate()
    }
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * density
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
    }
    private val items = listOf(
        "settings" to ("⚙" to "Settings"),
        "search" to ("⌕" to "Search settings"),
        "language" to ("文" to "Language"),
        "resize" to ("↕" to "Resize"),
        "undo" to ("↶" to "Undo"),
        "redo" to ("↷" to "Redo"),
        "themes" to ("◈" to "Themes"),
        "emoji" to ("☺" to "Emoji"),
        "clipboard" to ("▣" to "Clipboard"),
        "ai" to ("✦" to "AI tools"),
    )
    private var theme = KeyboardTheme.SOLID_WHITE
    private var pressed = -1
    private var scrollOffset = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var longPressTriggered = false

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        background.color = value.backgroundColor
        text.color = value.textColor
        icon.color = value.accentColor
        contentDescription = "Tools panel. Clipboard opens local recent and pinned text."
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        val gap = 5f * density
        val cardWidth = ((width - gap * 3f) / 2f).coerceAtLeast(1f)
        val cardHeight = 44f * density
        val contentHeight = contentHeight(cardHeight, gap)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll(contentHeight))

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(0f, -scrollOffset)
        items.forEachIndexed { index, item ->
            val enabled = isToolEnabled(item.first)
            val column = index % 2
            val row = index / 2
            val left = gap + column * (cardWidth + gap)
            val top = gap + row * (cardHeight + gap)
            card.color = if (index == pressed && enabled) theme.pressedKeyColor else theme.keyColor
            canvas.drawRoundRect(left, top, left + cardWidth, top + cardHeight, 8f * density, 8f * density, card)

            icon.textSize = 18f * density
            icon.textAlign = Paint.Align.CENTER
            icon.alpha = if (enabled) 255 else 90
            canvas.drawText(
                item.second.first,
                left + 25f * density,
                top + cardHeight / 2f - (icon.ascent() + icon.descent()) / 2f,
                icon,
            )

            val labelLeft = left + 46f * density
            val labelRight = left + cardWidth - 28f * density
            text.textSize = 15f * density
            text.alpha = if (enabled) 255 else 100
            canvas.save()
            canvas.clipRect(labelLeft, top, labelRight, top + cardHeight)
            canvas.drawText(
                item.second.second,
                labelLeft,
                top + cardHeight / 2f - (text.ascent() + text.descent()) / 2f,
                text,
            )
            canvas.restore()

            icon.textSize = 22f * density
            icon.alpha = if (enabled) 255 else 90
            canvas.drawText(
                "›",
                left + cardWidth - 15f * density,
                top + cardHeight / 2f - (icon.ascent() + icon.descent()) / 2f,
                icon,
            )
        }
        canvas.restore()

        if (maxScroll(contentHeight) > 0f) {
            val trackHeight = height.toFloat()
            val thumbHeight = (trackHeight * height / contentHeight).coerceAtLeast(18f * density)
            val thumbTop = (trackHeight - thumbHeight) * (scrollOffset / maxScroll(contentHeight))
            val scrollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentColor
                alpha = 150
            }
            canvas.drawRoundRect(
                width - 3f * density,
                thumbTop,
                width - 1f * density,
                thumbTop + thumbHeight,
                density,
                density,
                scrollPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                lastY = event.y
                dragging = false
                longPressTriggered = false
                pressed = indexAt(event.x, event.y)
                if (pressed >= 0) {
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.y - lastY
                if (!dragging && abs(event.y - downY) > touchSlop) {
                    dragging = true
                    handler.removeCallbacks(longPressRunnable)
                    pressed = -1
                }
                if (dragging) {
                    scrollOffset = (scrollOffset - delta).coerceIn(0f, maxScroll())
                    invalidate()
                }
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val selected = if (!dragging && !longPressTriggered) {
                    val index = indexAt(event.x, event.y)
                    if (index == pressed && items.getOrNull(index)?.let { isToolEnabled(it.first) } == true) {
                        items.getOrNull(index)?.first
                    } else null
                } else null
                pressed = -1
                dragging = false
                longPressTriggered = false
                invalidate()
                selected?.let(onToolSelected)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                pressed = -1
                dragging = false
                longPressTriggered = false
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

    private fun showToolActions(key: String) {
        val popup = PopupMenu(context, this)
        val alreadyPresent = isToolInToolbar(key)
        val supported = key in TOOLBAR_SUPPORTED_ITEMS
        val action = popup.menu.add(
            when {
                !supported -> "Unavailable on toolbar"
                alreadyPresent -> "Already on toolbar"
                else -> "Add to Toolbar"
            },
        )
        action.isEnabled = supported && !alreadyPresent
        popup.setOnMenuItemClickListener {
            if (supported && !alreadyPresent) onAddToToolbar(key)
            true
        }
        popup.show()
    }

    private fun indexAt(x: Float, y: Float): Int {
        val gap = 5f * density
        val cardWidth = ((width - gap * 3f) / 2f).coerceAtLeast(1f)
        val cardHeight = 44f * density
        val contentY = y + scrollOffset
        val column = ((x - gap) / (cardWidth + gap)).toInt()
        val row = ((contentY - gap) / (cardHeight + gap)).toInt()
        if (column !in 0..1 || row < 0) return -1
        val cardLeft = gap + column * (cardWidth + gap)
        val cardTop = gap + row * (cardHeight + gap)
        if (x < cardLeft || x > cardLeft + cardWidth ||
            contentY < cardTop || contentY > cardTop + cardHeight
        ) return -1
        val index = row * 2 + column
        return if (index in items.indices) index else -1
    }

    private fun maxScroll(): Float {
        val gap = 5f * density
        return maxScroll(contentHeight(44f * density, gap))
    }

    private fun maxScroll(contentHeight: Float): Float = (contentHeight - height).coerceAtLeast(0f)

    private fun contentHeight(cardHeight: Float, gap: Float): Float {
        val rows = (items.size + 1) / 2
        return gap * 2f + rows * cardHeight + (rows - 1) * gap
    }

    private companion object {
        val TOOLBAR_SUPPORTED_ITEMS = setOf(
            "settings", "search", "resize", "language", "themes", "emoji", "clipboard", "undo", "redo",
        )
    }
}
