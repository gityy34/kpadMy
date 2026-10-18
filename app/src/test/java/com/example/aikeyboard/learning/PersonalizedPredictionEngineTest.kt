package com.example.aikeyboard.learning

import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.prediction.LocalPredictionEngine
import com.example.aikeyboard.prediction.PredictionLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedPredictionEngineTest {
    private val editor = EditorInputState.from(0, 0)

    @Test
    fun personalWordCompetesForRelevantPrefixAndKeepsTypedForm() {
        val source = PersonalVocabularySource {
            listOf(
                PersonalWord(
                    normalizedWord = "uzairtestword",
                    displayText = "UzairTestWord",
                    language = PredictionLanguage.ENGLISH,
                    frequency = 8,
                    confidence = 8,
                    firstSeenMillis = 1L,
                    lastUsedMillis = System.currentTimeMillis(),
                ),
            )
        }
        val engine = LocalPredictionEngine(personalVocabulary = source)
        val result = engine.predict(snapshot("UzairT", "UzairT", KeyboardLanguage.ENGLISH))

        assertTrue(result.first().text == "UzairT")
        assertTrue(result.any { it.text == "UzairTestWord" && it.isPersonal })
    }

    @Test
    fun personalWordsAreSuppressedForSensitiveEditors() {
        val source = PersonalVocabularySource {
            listOf(
                PersonalWord("secretword", "secretword", PredictionLanguage.ENGLISH, 10, 10, 1L, 1L),
            )
        }
        val sensitive = editor.copy(isSensitive = true)
        val result = LocalPredictionEngine(personalVocabulary = source)
            .predict(snapshot("secret", "secret", KeyboardLanguage.ENGLISH, sensitive))
        assertTrue(result.isEmpty())
    }

    private fun snapshot(
        before: String,
        word: String,
        language: KeyboardLanguage,
        editorState: EditorInputState = editor,
    ) = CompositionSnapshot(
        textBeforeCursor = before,
        textAfterCursor = "",
        selectedText = null,
        selectionStart = before.length,
        selectionEnd = before.length,
        currentWord = word,
        activeLanguage = language,
        editorInputState = editorState,
        generation = 1L,
    )
}
