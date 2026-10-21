package com.example.aikeyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.View
import kotlin.math.max

/** Compact, theme-aware keyboard preview shared by catalog cards and the editor. */
class ThemePreviewView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var theme = KeyboardTheme.SOLID_WHITE
    private var backgroundBitmap: Bitmap? = null
    private var imageKey: String? = null

    fun setTheme(value: KeyboardTheme) {
        theme = value
        if (value.backgroundImageUri != imageKey) {
            imageKey = value.backgroundImageUri
            backgroundBitmap = loadImage(value.backgroundImageUri)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val background = RectF(0f, 0f, w, h)
        val image = backgroundBitmap
        if (image != null) {
            paint.shader = null
            canvas.drawBitmap(image, null, background, paint)
            paint.color = Color.argb(70, Color.black, Color.black, Color.black)
            canvas.drawRect(background, paint)
        } else if (theme.isGradient) {
            paint.shader = LinearGradient(
                0f, 0f, w, h,
                theme.gradientStart ?: theme.backgroundColor,
                theme.gradientEnd ?: theme.backgroundColor,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(background, paint)
            paint.shader = null
        } else {
            canvas.drawColor(theme.backgroundColor)
        }

        val horizontal = max(5f, w * 0.018f)
        val top = max(5f, h * 0.045f)
        val toolbarHeight = max(18f, h * 0.13f)
        val keyboardTop = top + toolbarHeight + h * 0.025f
        val rowGap = max(3f, h * 0.018f)
        val rowHeight = (h - keyboardTop - h * 0.06f - rowGap * 4f) / 5f
        if (rowHeight <= 1f) return

        paint.color = withAlpha(theme.keyColor, 220)
        canvas.drawRoundRect(RectF(horizontal, top, w - horizontal, top + toolbarHeight), toolbarHeight / 3f, toolbarHeight / 3f, paint)
        drawToolbar(canvas, horizontal, top, w, toolbarHeight)

        val rows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { rowIndex, letters ->
            val y = keyboardTop + rowIndex * (rowHeight + rowGap)
            val sideInset = if (rowIndex == 1 || rowIndex == 2) horizontal * 1.12f else horizontal
            drawKeys(canvas, letters, sideInset, y, w - sideInset, rowHeight, rowIndex == 0)
        }

        val bottomY = keyboardTop + 4 * (rowHeight + rowGap)
        val bottomWidth = w - horizontal * 2f
        val bottomGap = max(2f, horizontal * 0.45f)
        val widths = floatArrayOf(0.14f, 0.10f, 0.13f, 0.40f, 0.08f, 0.15f)
        var x = horizontal
        widths.forEachIndexed { index, ratio ->
            val keyW = (bottomWidth - bottomGap * 5f) * ratio
            drawKey(canvas, x, bottomY, x + keyW, bottomY + rowHeight, if (index == 3) "English" else when (index) {
                0 -> "?123"
                1 -> "☺"
                2 -> "◎"
                4 -> "."
                else -> "↵"
            }, index >= 4)
            x += keyW + bottomGap
        }
    }

    private fun drawToolbar(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val centerY = top + height / 2f
        val iconColor = theme.textColor
        paint.color = iconColor
        paint.strokeWidth = max(1.5f, height * 0.09f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val gap = width / 6f
        for (index in 1..4) {
            val cx = left + gap * index
            if (index == 4) {
                canvas.drawCircle(cx, centerY, height * 0.19f, paint)
                canvas.drawLine(cx, centerY + height * 0.18f, cx, centerY + height * 0.39f, paint)
                canvas.drawArc(RectF(cx - height * 0.27f, centerY - height * 0.04f, cx + height * 0.27f, centerY + height * 0.40f), 25f, 130f, false, paint)
            } else {
                canvas.drawCircle(cx, centerY, height * 0.16f, paint)
                canvas.drawLine(cx - height * 0.30f, centerY + height * 0.25f, cx + height * 0.30f, centerY + height * 0.25f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawKeys(canvas: Canvas, labels: String, left: Float, top: Float, right: Float, height: Float, numberRow: Boolean) {
        val gap = max(2f, width * 0.008f)
        val keyWidth = (right - left - gap * (labels.length - 1)) / labels.length
        labels.forEachIndexed { index, label ->
            val x = left + index * (keyWidth + gap)
            drawKey(canvas, x, top, x + keyWidth, top + height, label.toString(), numberRow)
        }
    }

    private fun drawKey(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String, special: Boolean) {
        paint.shader = null
        paint.color = withAlpha(if (special) theme.specialKeyColor else theme.keyColor, 235)
        paint.style = Paint.Style.FILL
        val radius = max(2f, (bottom - top) * 0.18f)
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        if (theme.keyStyle == KeyStyle.BORDERED) {
            paint.color = withAlpha(theme.borderColor, 220)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, (bottom - top) * 0.025f)
            canvas.drawRoundRect(RectF(left + paint.strokeWidth / 2f, top + paint.strokeWidth / 2f, right - paint.strokeWidth / 2f, bottom - paint.strokeWidth / 2f), radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = theme.textColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = max(8f, (bottom - top) * if (label == "English") 0.25f else 0.43f)
        canvas.drawText(label, (left + right) / 2f, (top + bottom) / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun loadImage(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(Uri.parse(value)).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }
}
