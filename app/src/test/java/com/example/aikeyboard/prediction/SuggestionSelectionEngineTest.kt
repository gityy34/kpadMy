package com.example.aikeyboard.prediction

import android.view.inputmethod.InputConnection
import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionSelectionEngineTest {
    @Test
    fun candidateReplacesOnlyTheCurrentWord() {
        val calls = mutableListOf<String>()
        val connection = connection(calls, selected = null)
        val result = SuggestionSelectionEngine.apply(
            connection,
            candidate("hello"),
            snapshot("say helo", "helo", 8, 8),
        )

        assertTrue(result)
        assertEquals(listOf("delete:4:0", "commit:hello"), calls)
    }

    @Test
    fun selectedTextIsReplacedDirectlyWithoutNeighborDeletion() {
        val calls = mutableListOf<String>()
        val connection = connection(calls, selected = "helo")
        val result = SuggestionSelectionEngine.apply(
            connection,
            candidate("hey"),
            snapshot("say helo", "helo", 4, 8, selected = "helo"),
        )

        assertTrue(result)
        assertEquals(listOf("commit:hello"), calls)
    }

    @Test
    fun candidateReplacementCoversWordOnBothSidesOfCursor() {
        val calls = mutableListOf<String>()
        val connection = connection(calls, selected = null)
        val result = SuggestionSelectionEngine.apply(
            connection,
            candidate("hello"),
            snapshot(
                before = "say he",
                word = "hello",
                start = 6,
                end = 6,
                wordBeforeCursor = "he",
                wordAfterCursor = "llo",
            ),
        )

        assertTrue(result)
        assertEquals(listOf("delete:2:3", "commit:hey"), calls)
    }

    private fun connection(calls: MutableList<String>, selected: String?): InputConnection =
        Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getSelectedText" -> selected
                "deleteSurroundingTextInCodePoints" -> {
                    calls += "delete:${args?.get(0)}:${args?.get(1)}"
                    true
                }
                "deleteSurroundingText" -> {
                    calls += "delete:${args?.get(0)}:${args?.get(1)}"
                    true
                }
                "commitText" -> {
                    calls += "commit:${args?.get(0)}"
                    true
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        } as InputConnection

    private fun candidate(text: String) = SuggestionCandidate(
        text = text,
        language = PredictionLanguage.ENGLISH,
        score = 1,
        kind = SuggestionKind.COMPLETION,
    )

    private fun snapshot(
        before: String,
        word: String,
        start: Int,
        end: Int,
        selected: String? = null,
        wordBeforeCursor: String = word,
        wordAfterCursor: String = "",
    ) = CompositionSnapshot(
        textBeforeCursor = before,
        textAfterCursor = " now",
        selectedText = selected,
        selectionStart = start,
        selectionEnd = end,
        currentWord = word,
        activeLanguage = KeyboardLanguage.ENGLISH,
        editorInputState = EditorInputState.from(0, 0),
        generation = 1L,
        currentWordBeforeCursor = wordBeforeCursor,
        currentWordAfterCursor = wordAfterCursor,
    )
}
