package com.example.aikeyboard.prediction

import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPredictionEngineTest {
    private val engine = LocalPredictionEngine()
    private val editor = EditorInputState.from(0, 0)

    @Test
    fun englishPrefixAndCorrectionsKeepTypedTextFirst() {
        val hel = engine.predict(snapshot("hel", "hel", KeyboardLanguage.ENGLISH))
        assertTrue(hel.first().text == "hel")
        assertTrue(hel.map { it.text }.containsAll(listOf("hello", "help", "helmet")))

        val helo = engine.predict(snapshot("helo", "helo", KeyboardLanguage.ENGLISH))
        assertTrue(helo.first().text == "helo")
        assertTrue(helo.map { it.text }.contains("hello"))

        val teh = engine.predict(snapshot("teh", "teh", KeyboardLanguage.ENGLISH))
        assertTrue(teh.map { it.text }.contains("the"))
    }

    @Test
    fun urduAndMixedContextReturnUnicodeCandidates() {
        val urdu = engine.predict(snapshot("آپ", "آپ", KeyboardLanguage.URDU))
        assertTrue(urdu.first().text == "آپ")
        assertTrue(urdu.map { it.text }.contains("آپ"))

        val afterUrdu = engine.predict(snapshot("آپ کیسے ", "", KeyboardLanguage.URDU))
        assertTrue(afterUrdu.map { it.text }.contains("ہیں"))

        val mixed = engine.predict(snapshot("Meeting kal ", "", KeyboardLanguage.ENGLISH))
        assertTrue(mixed.map { it.text }.contains("hai"))
    }

    @Test
    fun romanUrduVariantsAreRankedWithoutDuplicates() {
        val kya = engine.predict(snapshot("kyaa", "kyaa", KeyboardLanguage.URDU))
        assertTrue(kya.first().text == "kyaa")
        assertTrue(kya.map { it.text }.contains("kya"))

        val mujhe = engine.predict(snapshot("mjhy", "mjhy", KeyboardLanguage.URDU))
        assertTrue(mujhe.map { it.text }.contains("mujhe"))

        val keys = kya.map { it.text.lowercase() }
        assertTrue(keys.size == keys.toSet().size)
    }

    @Test
    fun sensitiveEditorsNeverProduceCandidates() {
        val sensitive = editor.copy(isSensitive = true)
        val result = engine.predict(snapshot("hello", "hello", KeyboardLanguage.ENGLISH, sensitive))
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
