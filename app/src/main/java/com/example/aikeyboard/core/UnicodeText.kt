package com.example.aikeyboard.core

import android.os.Build
import android.view.inputmethod.InputConnection

data class GraphemeSpan(
    val startUtf16: Int,
    val endUtf16: Int,
    val codePointCount: Int,
) {
    val utf16Length: Int
        get() = endUtf16 - startUtf16
}

/** Small, bounded Unicode boundary helper for backspace; it never scans an entire document. */
object UnicodeText {
    fun previousGrapheme(text: CharSequence): GraphemeSpan {
        if (text.isEmpty()) return GraphemeSpan(0, 0, 0)

        var start = previousCodePointStart(text, text.length)
        var current = codePointAt(text, start)
        var codePointCount = 1

        while (isExtend(current) && start > 0) {
            start = previousCodePointStart(text, start)
            current = codePointAt(text, start)
            codePointCount++
        }

        var regionalIndicatorCount = if (isRegionalIndicator(current)) 1 else 0
        while (start > 0) {
            val previousStart = previousCodePointStart(text, start)
            val previous = codePointAt(text, previousStart)

            when {
                current == '\n'.code && previous == '\r'.code -> {
                    start = previousStart
                    codePointCount++
                    break
                }

                previous == ZERO_WIDTH_JOINER -> {
                    start = previousStart
                    codePointCount++
                    if (start == 0) break

                    start = previousCodePointStart(text, start)
                    current = codePointAt(text, start)
                    codePointCount++
                    while (isExtend(current) && start > 0) {
                        start = previousCodePointStart(text, start)
                        current = codePointAt(text, start)
                        codePointCount++
                    }
                    regionalIndicatorCount = if (isRegionalIndicator(current)) 1 else 0
                }

                isRegionalIndicator(current) && isRegionalIndicator(previous) &&
                    regionalIndicatorCount % 2 == 1 -> {
                    start = previousStart
                    current = previous
                    regionalIndicatorCount++
                    codePointCount++
                }

                else -> break
            }
        }

        return GraphemeSpan(start, text.length, codePointCount)
    }

    private fun previousCodePointStart(text: CharSequence, index: Int): Int {
        val candidate = index - 1
        if (candidate > 0 && Character.isLowSurrogate(text[candidate]) &&
            Character.isHighSurrogate(text[candidate - 1])
        ) {
            return candidate - 1
        }
        return candidate
    }

    private fun codePointAt(text: CharSequence, index: Int): Int =
        Character.codePointAt(text, index)

    private fun isExtend(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK ||
            type == Character.COMBINING_SPACING_MARK ||
            type == Character.ENCLOSING_MARK ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint in 0xE0100..0xE01EF ||
            codePoint in 0x1F3FB..0x1F3FF
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    private const val ZERO_WIDTH_JOINER = 0x200D
}

/** InputConnection boundary that deletes a selected range or one complete Unicode grapheme. */
object UnicodeSafeDeletion {
    private const val MAX_CONTEXT_UTF16 = 96

    fun deletePrevious(inputConnection: InputConnection): Boolean {
        val selected = inputConnection.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            return inputConnection.commitText("", 1)
        }

        val before = inputConnection.getTextBeforeCursor(MAX_CONTEXT_UTF16, 0)
        val span = if (before != null && before.isNotEmpty()) {
            UnicodeText.previousGrapheme(before)
        } else {
            GraphemeSpan(0, 1, 1)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inputConnection.deleteSurroundingTextInCodePoints(span.codePointCount, 0)
        } else {
            inputConnection.deleteSurroundingText(span.utf16Length, 0)
        }
    }
}
