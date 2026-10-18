package com.example.aikeyboard.prediction

import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionControllerTest {
    @Test
    fun staleRapidTypingResultsCannotReachTheCallback() {
        val engine = PredictionEngine { snapshot ->
            when (snapshot.currentWord) {
                "t" -> Thread.sleep(180)
                "th" -> Thread.sleep(120)
            }
            listOf(SuggestionCandidate(
                text = snapshot.currentWord,
                language = PredictionLanguage.ENGLISH,
                score = 1,
                kind = SuggestionKind.TYPED,
            ))
        }
        val controller = PredictionController(engine, Executors.newFixedThreadPool(3))
        val results = Collections.synchronizedList(mutableListOf<String>())
        val completed = CountDownLatch(1)

        controller.request(snapshot("t")) { result -> results += result.snapshot.currentWord }
        controller.request(snapshot("th")) { result -> results += result.snapshot.currentWord }
        controller.request(snapshot("thi")) { result -> results += result.snapshot.currentWord }
        controller.request(snapshot("this")) {
            results += it.snapshot.currentWord
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        Thread.sleep(250)
        assertEquals(listOf("this"), results.toList())
        controller.close()
    }

    private fun snapshot(word: String) = CompositionSnapshot(
        textBeforeCursor = word,
        textAfterCursor = "",
        selectedText = null,
        selectionStart = word.length,
        selectionEnd = word.length,
        currentWord = word,
        activeLanguage = KeyboardLanguage.ENGLISH,
        editorInputState = EditorInputState.from(0, 0),
        generation = word.length.toLong(),
    )
}
