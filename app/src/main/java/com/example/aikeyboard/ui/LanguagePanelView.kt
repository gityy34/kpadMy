package com.example.aikeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.theme.KeyboardTheme

/** Compact in-IME language picker; it never closes or recreates the IME. */
class LanguagePanelView(
    context: Context,
    selectedLanguage: KeyboardLanguage,
    private val onLanguageSelected: (KeyboardLanguage) -> Unit,
    private val onBack: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
    }
    private var theme = KeyboardTheme.SOLID_WHITE
    private var selected = selectedLanguage
    private var pressed = -1

    private val languages = listOf(
        KeyboardLanguage.ENGLISH to ("English" to "English QWERTY"),
        KeyboardLanguage.URDU to ("اردو" to "Urdu script"),
        KeyboardLanguage.ROMAN_URDU to ("Roman Urdu" to "Roman Urdu on Latin keys"),
    )

    init {
        isClickable = true
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        background.color = value.backgroundColor
        card.color = value.keyColor
        title.color = value.textColor
        label.color = value.textColor
        secondary.color = muted(value.textColor)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.backgroundColor)
        secondary.color = muted(theme.textColor)
        val gap = 10f * density
        val left = 12f * density
        val right = width - left
        val cardHeight = 50f * density
        title.textAlign = Paint.Align.LEFT
        canvas.drawText("Language", left, 23f * density, title)
        languages.forEachIndexed { index, entry ->
            val top = 34f * density + index * (cardHeight + gap)
            card.color = if (index == pressed) theme.pressedKeyColor else theme.keyColor
            canvas.drawRoundRect(left, top, right, top + cardHeight, 10f * density, 10f * density, card)
            label.textAlign = Paint.Align.LEFT
            canvas.drawText(entry.second.first, left + 16f * density, top + 22f * density, label)
            secondary.textAlign = Paint.Align.LEFT
            canvas.drawText(entry.second.second, left + 16f * density, top + 39f * density, secondary)
            label.textAlign = Paint.Align.RIGHT
            label.color = if (entry.first == selected) theme.accentColor else muted(theme.textColor)
            canvas.drawText(if (entry.first == selected) "✓" else "○", right - 16f * density,
                top + 31f * density, label)
            label.color = theme.textColor
        }
        val backTop = height - 46f * density
        title.color = theme.textColor
        title.textAlign = Paint.Align.CENTER
        canvas.drawText("Back to Keyboard", width / 2f, backTop + 26f * density, title)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = languageIndexAt(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = languageIndexAt(event.x, event.y)
                val selectedLanguage = languages.getOrNull(if (index == pressed) index else -1)?.first
                val back = index == BACK_INDEX && pressed == BACK_INDEX
                pressed = -1
                invalidate()
                if (selectedLanguage != null) onLanguageSelected(selectedLanguage)
                else if (back) onBack()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) pressed = -1
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

    private fun languageIndexAt(x: Float, y: Float): Int {
        val left = 12f * density
        val right = width - left
        val gap = 10f * density
        val cardHeight = 50f * density
        languages.forEachIndexed { index, _ ->
            val top = 34f * density + index * (cardHeight + gap)
            if (x in left..right && y in top..(top + cardHeight)) return index
        }
        if (x in left..right && y >= height - 54f * density) return BACK_INDEX
        return -1
    }

    private companion object {
        const val BACK_INDEX = -2
    }

    private fun muted(color: Int): Int = Color.argb(
        175,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
