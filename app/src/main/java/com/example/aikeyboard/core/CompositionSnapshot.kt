package com.example.aikeyboard.core

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

data class CompositionSnapshot(
    val textBeforeCursor: String,
    val textAfterCursor: String,
    val selectedText: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
    val currentWord: String,
    val activeLanguage: KeyboardLanguage,
    val editorInputState: EditorInputState,
    val generation: Long,
    /** Word fragments on either side of the cursor; defaults preserve older snapshot callers. */
    val currentWordBeforeCursor: String = currentWord,
    val currentWordAfterCursor: String = "",
) {
    val hasSelection: Boolean
        get() = selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd

    companion object {
        fun empty(
            editorInputState: EditorInputState,
            activeLanguage: KeyboardLanguage,
            generation: Long,
        ): CompositionSnapshot = CompositionSnapshot(
            textBeforeCursor = "",
            textAfterCursor = "",
            selectedText = null,
            selectionStart = -1,
            selectionEnd = -1,
            currentWord = "",
            activeLanguage = activeLanguage,
            editorInputState = editorInputState,
            generation = generation,
            currentWordBeforeCursor = "",
            currentWordAfterCursor = "",
        )
    }
}

/**
 * Deferred, bounded composition reader. It is deliberately not called by TypingEngine.handle;
 * stale reads are cancelled or discarded so they can never delay a normal key commit.
 */
class CompositionController(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyboardComposition").apply { isDaemon = true }
    },
) : Closeable {
    private val generationCounter = AtomicLong(0L)

    @Volatile
    private var latestSnapshot = CompositionSnapshot.empty(
        editorInputState = DEFAULT_EDITOR_INPUT_STATE,
        activeLanguage = KeyboardLanguage.ENGLISH,
        generation = 0L,
    )

    @Volatile
    private var pendingRequest: Future<*>? = null

    @Synchronized
    fun reset(editorInputState: EditorInputState, activeLanguage: KeyboardLanguage) {
        val generation = generationCounter.incrementAndGet()
        pendingRequest?.cancel(false)
        pendingRequest = null
        latestSnapshot = CompositionSnapshot.empty(editorInputState, activeLanguage, generation)
    }

    @Synchronized
    fun updateSelection(selectionStart: Int, selectionEnd: Int) {
        latestSnapshot = latestSnapshot.copy(
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
    }

    @Synchronized
    fun requestSnapshot(
        inputConnection: InputConnection?,
        editorInputState: EditorInputState,
        activeLanguage: KeyboardLanguage,
        onSnapshot: ((CompositionSnapshot) -> Unit)? = null,
    ) {
        val generation = generationCounter.incrementAndGet()
        val previous = latestSnapshot
        val selectionStart = previous.selectionStart
        val selectionEnd = previous.selectionEnd
        pendingRequest?.cancel(false)

        if (inputConnection == null || editorInputState.isSensitive) {
            latestSnapshot = CompositionSnapshot.empty(editorInputState, activeLanguage, generation).copy(
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
            )
            pendingRequest = null
            return
        }

        pendingRequest = executor.submit {
            if (generationCounter.get() != generation) return@submit

            val before = inputConnection.getTextBeforeCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty()
            val after = inputConnection.getTextAfterCursor(MAX_CONTEXT_UTF16, 0)?.toString().orEmpty()
            val selected = inputConnection.getSelectedText(0)?.toString()
            val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
            val actualSelectionStart = extracted?.selectionStart
                ?.takeIf { it >= 0 } ?: selectionStart
            val actualSelectionEnd = extracted?.selectionEnd
                ?.takeIf { it >= 0 } ?: selectionEnd
            if (generationCounter.get() != generation) return@submit

            val snapshot = CompositionSnapshot(
                textBeforeCursor = before,
                textAfterCursor = after,
                selectedText = selected,
                selectionStart = actualSelectionStart,
                selectionEnd = actualSelectionEnd,
                currentWord = currentWord(before) + nextWord(after),
                activeLanguage = activeLanguage,
                editorInputState = editorInputState,
                generation = generation,
                currentWordBeforeCursor = currentWord(before),
                currentWordAfterCursor = nextWord(after),
            )
            latestSnapshot = snapshot
            onSnapshot?.invoke(snapshot)
        }
    }

    fun snapshot(): CompositionSnapshot = latestSnapshot

    @Synchronized
    override fun close() {
        generationCounter.incrementAndGet()
        pendingRequest?.cancel(true)
        pendingRequest = null
        executor.shutdownNow()
    }

    private fun currentWord(textBeforeCursor: String): String {
        var index = textBeforeCursor.length
        while (index > 0) {
            val start = previousCodePointStart(textBeforeCursor, index)
            val codePoint = textBeforeCursor.codePointAt(start)
            if (!isWordCodePoint(codePoint)) break
            index = start
        }
        return textBeforeCursor.substring(index)
    }

    private fun nextWord(textAfterCursor: String): String {
        var index = 0
        while (index < textAfterCursor.length) {
            val codePoint = textAfterCursor.codePointAt(index)
            if (!isWordCodePoint(codePoint)) break
            index += Character.charCount(codePoint)
        }
        return textAfterCursor.substring(0, index)
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == '_'.code ||
            codePoint == '\''.code
    }

    private fun previousCodePointStart(text: String, index: Int): Int {
        val candidate = index - 1
        return if (candidate > 0 && Character.isLowSurrogate(text[candidate]) &&
            Character.isHighSurrogate(text[candidate - 1])
        ) {
            candidate - 1
        } else {
            candidate
        }
    }

    companion object {
        private const val MAX_CONTEXT_UTF16 = 96
        private val DEFAULT_EDITOR_INPUT_STATE = EditorInputState(
            inputType = 0,
            imeOptions = 0,
            fieldType = EditorFieldType.TEXT,
            isSensitive = false,
            noPersonalizedLearning = false,
        )
    }
}
