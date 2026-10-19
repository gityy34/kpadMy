package com.example.aikeyboard.core

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.KeyEvent

/**
 * Synchronous, allocation-light input engine. This is intentionally unaware of the keyboard
 * View so normal text insertion cannot be delayed by drawing, suggestions, or network work.
 */
class TypingEngine(private val state: KeyboardState) {

    fun handle(key: KeySpec, inputConnection: InputConnection?): Boolean {
        when (key.type) {
            KeyType.SHIFT -> {
                state.pressShift(android.os.SystemClock.uptimeMillis())
                return true
            }

            KeyType.MODE_SWITCH -> {
                state.toggleMode()
                return true
            }

            KeyType.LANGUAGE_SWITCH -> {
                state.toggleLanguage()
                return true
            }
        }

        if (inputConnection == null) return false

        return when (key.type) {
            KeyType.CHARACTER -> {
                val text = key.textFor(state.shiftActive())
                val normalized = if (isTightPunctuation(text)) {
                    removeSpaceBeforePunctuation(inputConnection)
                } else {
                    true
                }
                val committed = normalized && inputConnection.commitText(text, 1)
                if (committed) state.consumeShift()
                committed
            }

            KeyType.SPACE -> {
                val committed = inputConnection.commitText(" ", 1)
                if (committed) state.consumeShift()
                committed
            }

            KeyType.BACKSPACE -> UnicodeSafeDeletion.deletePrevious(inputConnection)
            KeyType.ENTER -> commitEnter(inputConnection)
            else -> false
        }
    }

    private fun commitEnter(inputConnection: InputConnection): Boolean {
        val action = state.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (inputConnection.performEditorAction(action)) return true
        }

        // Do not inject a newline when an action field declines its editor action.
        // Forwarding ENTER preserves the host editor's own fallback behavior.
        if (!state.editorInputState.isMultiline) {
            val down = inputConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
            val up = inputConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
            return down || up
        }
        return inputConnection.commitText("\n", 1)
    }

    private fun removeSpaceBeforePunctuation(inputConnection: InputConnection): Boolean {
        val before = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (before.isEmpty()) return true
        val codePoint = before.codePointBefore(before.length)
        if (!Character.isWhitespace(codePoint) || codePoint == '\n'.code || codePoint == '\r'.code) {
            return true
        }
        val count = Character.charCount(codePoint)
        return inputConnection.deleteSurroundingText(count, 0)
    }

    private fun isTightPunctuation(text: String): Boolean {
        if (text.isEmpty()) return false
        return when (text.codePointAt(0)) {
            '.'.code, ','.code, '?'.code, '!'.code, ':'.code, ';'.code,
            '؟'.code, '۔'.code, '،'.code, ')'.code, ']'.code, '}'.code -> true
            else -> false
        }
    }
}
