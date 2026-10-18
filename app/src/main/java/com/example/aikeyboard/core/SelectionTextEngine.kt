package com.example.aikeyboard.core

import android.view.inputmethod.InputConnection
import java.util.Locale

enum class SelectionCapitalization {
    LOWERCASE,
    TITLE_CASE,
    UPPERCASE,
}

/** Selection-only transformations for future toolbar actions; it never touches surrounding text. */
class SelectionTextEngine {
    fun capitalizeSelection(
        inputConnection: InputConnection,
        mode: SelectionCapitalization,
    ): Boolean {
        val selected = inputConnection.getSelectedText(0) ?: return false
        if (selected.isEmpty()) return false

        val text = selected.toString()
        val replacement = when (mode) {
            SelectionCapitalization.LOWERCASE -> text.lowercase(Locale.ROOT)
            SelectionCapitalization.UPPERCASE -> text.uppercase(Locale.ROOT)
            SelectionCapitalization.TITLE_CASE -> titleCaseWords(text)
        }
        return inputConnection.commitText(replacement, 1)
    }

    private fun titleCaseWords(text: String): String {
        val output = StringBuilder(text.length)
        var capitalizeNext = true
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val value = String(Character.toChars(codePoint))
            if (Character.isLetter(codePoint)) {
                output.append(if (capitalizeNext) value.uppercase(Locale.ROOT) else value)
                capitalizeNext = false
            } else {
                output.append(value)
                if (!Character.isLetterOrDigit(codePoint) && codePoint != '\''.code && codePoint != '_'.code) {
                    capitalizeNext = true
                }
            }
            index += Character.charCount(codePoint)
        }
        return output.toString()
    }
}
