package com.example.aikeyboard.core

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.ArrayDeque

/**
 * A small, editor-safe history for edits initiated by this keyboard.
 *
 * The history stores only the bounded text state around the cursor and the changed span. It does
 * not keep full documents, and it refuses to apply an operation when the editor no longer looks
 * like the state produced by that operation. This prevents an Undo tap from corrupting text that
 * was changed externally by the host application.
 */
class KeyboardEditHistory(private val maxEntries: Int = 50) {
    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var boundConnection: InputConnection? = null
    private var expectedSelection: Selection? = null
    private var sessionKey: String? = null

    fun beginSession(key: String) {
        if (sessionKey != key) {
            clear()
            sessionKey = key
        }
    }

    fun bind(inputConnection: InputConnection?) {
        if (boundConnection !== inputConnection) {
            boundConnection = inputConnection
        }
    }

    fun capture(inputConnection: InputConnection?): EditorTextSnapshot? {
        if (inputConnection == null) return null
        bind(inputConnection)
        val extracted = runCatching {
            inputConnection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 0 }, 0)
        }.getOrNull()
        val start = extracted?.selectionStart ?: -1
        val end = extracted?.selectionEnd ?: -1
        if (start < 0 || end < 0) return null
        return EditorTextSnapshot(
            beforeCursor = inputConnection.getTextBeforeCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty(),
            afterCursor = inputConnection.getTextAfterCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty(),
            selectedText = inputConnection.getSelectedText(0)?.toString(),
            selectionStart = start,
            selectionEnd = end,
        )
    }

    fun record(before: EditorTextSnapshot?, after: EditorTextSnapshot?) {
        if (before == null || after == null) return
        val operation = EditOperation.from(before, after) ?: return
        undoStack.addLast(operation)
        while (undoStack.size > maxEntries) undoStack.removeFirst()
        redoStack.clear()
        expectedSelection = Selection(after.selectionStart, after.selectionEnd)
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun onSelectionChanged(start: Int, end: Int) {
        val expected = expectedSelection
        if (expected != null && expected.start == start && expected.end == end) {
            expectedSelection = null
        } else if (undoStack.isNotEmpty() || redoStack.isNotEmpty()) {
            clear()
        }
    }

    fun undo(inputConnection: InputConnection?): Boolean {
        bind(inputConnection)
        val operation = undoStack.peekLast() ?: return false
        if (!operation.undo(inputConnection)) return false
        undoStack.removeLast()
        redoStack.addLast(operation)
        expectedSelection = Selection(operation.before.selectionStart, operation.before.selectionEnd)
        return true
    }

    fun redo(inputConnection: InputConnection?): Boolean {
        bind(inputConnection)
        val operation = redoStack.peekLast() ?: return false
        if (!operation.redo(inputConnection)) return false
        redoStack.removeLast()
        undoStack.addLast(operation)
        while (undoStack.size > maxEntries) undoStack.removeFirst()
        expectedSelection = Selection(operation.after.selectionStart, operation.after.selectionEnd)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        expectedSelection = null
    }

    private data class Selection(val start: Int, val end: Int)

    data class EditorTextSnapshot(
        val beforeCursor: String,
        val afterCursor: String,
        val selectedText: String?,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        val hasSelection: Boolean
            get() = selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd
    }

    private data class EditOperation(
        val before: EditorTextSnapshot,
        val after: EditorTextSnapshot,
        val start: Int,
        val oldText: String,
        val newText: String,
    ) {
        fun undo(inputConnection: InputConnection?): Boolean {
            inputConnection ?: return false
            val current = snapshotMatches(inputConnection, after) ?: return false
            if (current.selectionStart != after.selectionStart || current.selectionEnd != after.selectionEnd) {
                return false
            }
            val replaceStart = if (after.hasSelection) {
                minOf(after.selectionStart, after.selectionEnd)
            } else {
                (after.selectionStart - newText.length).coerceAtLeast(0)
            }
            val replaceEnd = if (after.hasSelection) {
                maxOf(after.selectionStart, after.selectionEnd)
            } else {
                after.selectionStart
            }
            if (!inputConnection.setSelection(replaceStart, replaceEnd)) return false
            return inputConnection.commitText(oldText, 1)
        }

        fun redo(inputConnection: InputConnection?): Boolean {
            inputConnection ?: return false
            val current = snapshotMatches(inputConnection, before) ?: return false
            if (current.selectionStart != before.selectionStart || current.selectionEnd != before.selectionEnd) {
                return false
            }
            val replaceStart = if (before.hasSelection) {
                minOf(before.selectionStart, before.selectionEnd)
            } else {
                (before.selectionStart - oldText.length).coerceAtLeast(0)
            }
            val replaceEnd = if (before.hasSelection) {
                maxOf(before.selectionStart, before.selectionEnd)
            } else {
                before.selectionStart
            }
            if (!inputConnection.setSelection(replaceStart, replaceEnd)) return false
            return inputConnection.commitText(newText, 1)
        }

        private fun snapshotMatches(
            inputConnection: InputConnection,
            expected: EditorTextSnapshot,
        ): EditorTextSnapshot? {
            val current = captureSnapshot(inputConnection) ?: return null
            return if (current.beforeCursor == expected.beforeCursor &&
                current.afterCursor == expected.afterCursor &&
                current.selectedText == expected.selectedText
            ) current else null
        }

        companion object {
            fun from(before: EditorTextSnapshot, after: EditorTextSnapshot): EditOperation? {
                if (before.selectionStart < 0 || after.selectionStart < 0) return null
                val prefix = commonPrefix(before.beforeCursor, after.beforeCursor)
                val suffix = commonSuffix(before.afterCursor, after.afterCursor)
                val beforeAfterChanged = before.afterCursor.substring(0, before.afterCursor.length - suffix)
                val afterAfterChanged = after.afterCursor.substring(0, after.afterCursor.length - suffix)
                // Keyboard edits are anchored before the cursor. A change on the other side is
                // ambiguous and is deliberately excluded from the safe history.
                if (beforeAfterChanged.isNotEmpty() || afterAfterChanged.isNotEmpty()) return null

                val removedBefore = before.beforeCursor.substring(prefix)
                val insertedBefore = after.beforeCursor.substring(prefix)
                val oldText = if (before.hasSelection) {
                    before.selectedText ?: return null
                } else {
                    removedBefore
                }
                val newText = insertedBefore
                if (oldText.isEmpty() && newText.isEmpty()) return null
                val start = if (before.hasSelection) {
                    minOf(before.selectionStart, before.selectionEnd)
                } else {
                    (before.selectionStart - oldText.length).coerceAtLeast(0)
                }
                return EditOperation(before, after, start, oldText, newText)
            }

            private fun commonPrefix(first: String, second: String): Int {
                val limit = minOf(first.length, second.length)
                var index = 0
                while (index < limit && first[index] == second[index]) index++
                return index
            }

            private fun commonSuffix(first: String, second: String): Int {
                val limit = minOf(first.length, second.length)
                var count = 0
                while (count < limit && first[first.length - count - 1] == second[second.length - count - 1]) {
                    count++
                }
                return count
            }

            private fun captureSnapshot(inputConnection: InputConnection): EditorTextSnapshot? {
                val extracted = runCatching {
                    inputConnection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 0 }, 0)
                }.getOrNull() ?: return null
                if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) return null
                return EditorTextSnapshot(
                    beforeCursor = inputConnection.getTextBeforeCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty(),
                    afterCursor = inputConnection.getTextAfterCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty(),
                    selectedText = inputConnection.getSelectedText(0)?.toString(),
                    selectionStart = extracted.selectionStart,
                    selectionEnd = extracted.selectionEnd,
                )
            }
        }
    }

    private companion object {
        const val MAX_CONTEXT_UTF16 = 512
    }
}
