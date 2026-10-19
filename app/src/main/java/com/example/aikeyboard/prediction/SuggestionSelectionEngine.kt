package com.example.aikeyboard.prediction

import android.os.Build
import android.view.inputmethod.InputConnection
import com.example.aikeyboard.core.CompositionSnapshot

/** Applies an explicitly tapped candidate without touching text outside the active word. */
object SuggestionSelectionEngine {
    data class Result(
        val committed: Boolean,
        val autoSpaced: Boolean,
        val nextWordReady: Boolean,
    )

    fun apply(
        inputConnection: InputConnection?,
        candidate: SuggestionCandidate,
        snapshot: CompositionSnapshot,
    ): Boolean {
        return applyInternal(inputConnection, candidate, snapshot, addAutoSpace = false).committed
    }

    /** Replaces the active word and appends one safe word separator when appropriate. */
    fun applyWithAutoSpace(
        inputConnection: InputConnection?,
        candidate: SuggestionCandidate,
        snapshot: CompositionSnapshot,
    ): Result = applyInternal(inputConnection, candidate, snapshot, addAutoSpace = true)

    private fun applyInternal(
        inputConnection: InputConnection?,
        candidate: SuggestionCandidate,
        snapshot: CompositionSnapshot,
        addAutoSpace: Boolean,
    ): Result {
        inputConnection ?: return Result(false, false, false)
        var replaced = false
        if (snapshot.hasSelection || snapshot.currentWord.isEmpty()) {
            replaced = inputConnection.commitText(candidate.text, 1)
        } else if (snapshot.currentWord == candidate.text) {
            replaced = true
        } else {
            val beforeCodePointCount = snapshot.currentWordBeforeCursor
                .codePointCount(0, snapshot.currentWordBeforeCursor.length)
            val afterCodePointCount = snapshot.currentWordAfterCursor
                .codePointCount(0, snapshot.currentWordAfterCursor.length)
            val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                inputConnection.deleteSurroundingTextInCodePoints(beforeCodePointCount, afterCodePointCount)
            } else {
                inputConnection.deleteSurroundingText(
                    snapshot.currentWordBeforeCursor.length,
                    snapshot.currentWordAfterCursor.length,
                )
            }
            replaced = deleted && inputConnection.commitText(candidate.text, 1)
        }
        if (!replaced || !addAutoSpace || !needsAutoSpace(candidate.text, inputConnection)) {
            return Result(replaced, false, replaced && isWordCandidate(candidate.text))
        }
        val spaced = inputConnection.commitText(" ", 1)
        return Result(spaced, spaced, spaced)
    }

    private fun needsAutoSpace(candidate: String, inputConnection: InputConnection): Boolean {
        if (candidate.isEmpty()) return false
        val lastCodePoint = candidate.codePointBefore(candidate.length)
        if (!isWordCodePoint(lastCodePoint)) return false

        val after = inputConnection.getTextAfterCursor(2, 0)?.toString().orEmpty()
        if (after.isEmpty()) return true
        val nextCodePoint = after.codePointAt(0)
        if (Character.isWhitespace(nextCodePoint)) return false
        if (isPunctuation(nextCodePoint)) return false
        // Inserting into the middle of an existing word must not split it with an automatic
        // separator. The user can still type a space explicitly when desired.
        return !isWordCodePoint(nextCodePoint)
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == '_'.code || codePoint == '\''.code ||
            codePoint == 0x200C || codePoint == 0x200D
    }

    private fun isWordCandidate(text: String): Boolean =
        text.isNotEmpty() && isWordCodePoint(text.codePointBefore(text.length))

    private fun isPunctuation(codePoint: Int): Boolean = codePoint == '.'.code ||
        codePoint == ','.code || codePoint == '?'.code || codePoint == '!'.code ||
        codePoint == ':'.code || codePoint == ';'.code || codePoint == '؟'.code ||
        codePoint == '۔'.code || codePoint == '،'.code || codePoint == ')'.code ||
        codePoint == ']'.code || codePoint == '}'.code
}
