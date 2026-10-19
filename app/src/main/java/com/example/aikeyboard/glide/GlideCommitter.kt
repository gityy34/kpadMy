package com.example.aikeyboard.glide

import android.os.Build
import android.view.inputmethod.InputConnection
import com.example.aikeyboard.core.CompositionSnapshot

/** Replaces the provisional first key and the active word without touching neighboring text. */
object GlideCommitter {
    fun commit(
        inputConnection: InputConnection?,
        gesture: GlideGesture,
        snapshot: CompositionSnapshot?,
        text: String,
    ): Boolean {
        inputConnection ?: return false
        val before = if (snapshot == null || snapshot.hasSelection) {
            gesture.provisionalText.codePointCount(0, gesture.provisionalText.length)
        } else {
            snapshot.currentWordBeforeCursor.codePointCount(0, snapshot.currentWordBeforeCursor.length) +
                gesture.provisionalText.codePointCount(0, gesture.provisionalText.length)
        }
        val after = if (snapshot == null || snapshot.hasSelection) 0 else {
            snapshot.currentWordAfterCursor.codePointCount(0, snapshot.currentWordAfterCursor.length)
        }
        val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inputConnection.deleteSurroundingTextInCodePoints(before, after)
        } else {
            inputConnection.deleteSurroundingText(
                gesture.provisionalText.length + (snapshot?.currentWordBeforeCursor?.length ?: 0),
                snapshot?.currentWordAfterCursor?.length ?: 0,
            )
        }
        return deleted && inputConnection.commitText(text, 1)
    }

    fun removeProvisional(inputConnection: InputConnection?, provisionalText: String): Boolean {
        inputConnection ?: return false
        val count = provisionalText.codePointCount(0, provisionalText.length)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inputConnection.deleteSurroundingTextInCodePoints(count, 0)
        } else {
            inputConnection.deleteSurroundingText(provisionalText.length, 0)
        }
    }
}
