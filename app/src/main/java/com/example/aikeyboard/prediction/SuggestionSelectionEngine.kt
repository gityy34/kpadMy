package com.example.aikeyboard.prediction

import android.os.Build
import android.view.inputmethod.InputConnection
import com.example.aikeyboard.core.CompositionSnapshot

/** Applies an explicitly tapped candidate without touching text outside the active word. */
object SuggestionSelectionEngine {
    fun apply(
        inputConnection: InputConnection?,
        candidate: SuggestionCandidate,
        snapshot: CompositionSnapshot,
    ): Boolean {
        inputConnection ?: return false
        if (snapshot.hasSelection || snapshot.currentWord.isEmpty()) {
            return inputConnection.commitText(candidate.text, 1)
        }
        if (snapshot.currentWord == candidate.text) return true

        val codePointCount = snapshot.currentWord.codePointCount(0, snapshot.currentWord.length)
        val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inputConnection.deleteSurroundingTextInCodePoints(codePointCount, 0)
        } else {
            inputConnection.deleteSurroundingText(snapshot.currentWord.length, 0)
        }
        return deleted && inputConnection.commitText(candidate.text, 1)
    }
}
