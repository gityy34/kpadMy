package com.example.aikeyboard.ui

import android.text.TextUtils
import android.text.TextPaint
import java.text.BreakIterator
import java.util.Locale

/**
 * Keeps candidate rendering separate from candidate data. The full text is never changed; only
 * the value painted inside a bounded cell is shortened or fitted.
 */
internal object SuggestionDisplay {
    data class FittedText(
        val text: String,
        val textSizePx: Float,
        val textScaleX: Float,
    )

    fun displayText(fullText: String): String {
        if (fullText.isEmpty()) return ""
        val clusters = graphemeClusters(fullText)
        if (clusters.size < LONG_WORD_THRESHOLD) return fullText
        return buildString {
            append(clusters.take(EDGE_CLUSTER_COUNT).joinToString(""))
            append("..")
            append(clusters.takeLast(EDGE_CLUSTER_COUNT).joinToString(""))
        }
    }

    fun fit(
        fullText: String,
        availableWidthPx: Float,
        baseTextSizePx: Float,
        scaledDensity: Float,
        textPaint: TextPaint,
    ): FittedText {
        val display = displayText(fullText)
        if (display.isEmpty() || availableWidthPx <= 0f) {
            return FittedText(display, baseTextSizePx, 1f)
        }

        val base = baseTextSizePx.coerceAtLeast(1f)
        val minimum = maxOf(16f * scaledDensity, base * MIN_SIZE_RATIO)
        val sizes = listOf(base, base * 0.92f, base * 0.84f, minimum).distinct()

        textPaint.textScaleX = 1f
        for (size in sizes) {
            textPaint.textSize = size
            if (textPaint.measureText(display) <= availableWidthPx) {
                return FittedText(display, size, 1f)
            }
        }

        // Keep short words complete whenever possible with controlled horizontal compression.
        val measuredAtMinimum = textPaint.measureText(display)
        if (measuredAtMinimum > 0f) {
            val compressed = (availableWidthPx / measuredAtMinimum).coerceIn(MIN_SCALE_X, 1f)
            if (measuredAtMinimum * compressed <= availableWidthPx) {
                return FittedText(display, minimum, compressed)
            }
        }

        // Only an exceptionally narrow cell reaches this display-only fallback. The full
        // candidate is still retained for commit/touch handling.
        textPaint.textSize = minimum
        val clipped = TextUtils.ellipsize(
            display,
            textPaint,
            availableWidthPx,
            TextUtils.TruncateAt.END,
        ).toString()
        return FittedText(clipped, minimum, 1f)
    }

    private fun graphemeClusters(value: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(value)
        val result = ArrayList<String>()
        var start = iterator.first()
        while (true) {
            val end = iterator.next()
            if (end == BreakIterator.DONE) break
            result += value.substring(start, end)
            start = end
        }
        return result
    }

    private const val LONG_WORD_THRESHOLD = 9
    private const val EDGE_CLUSTER_COUNT = 3
    private const val MIN_SIZE_RATIO = 0.72f
    private const val MIN_SCALE_X = 0.78f
}
