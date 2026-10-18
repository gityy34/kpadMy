package com.example.aikeyboard.learning

import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.prediction.PredictionLanguage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalLearningControllerTest {
    @Test
    fun repeatedIntentionalUseRaisesConfidenceWithoutBlockingCaller() {
        val learned = CountDownLatch(3)
        val repository = FakeRepository(learned)
        val controller = PersonalLearningController(repository)
        val editor = EditorInputState.from(0, 0)

        repeat(3) {
            controller.recordWord("UzairTestWord", PredictionLanguage.ENGLISH, editor)
        }

        assertTrue(learned.await(2, TimeUnit.SECONDS))
        val word = repository.snapshot().single()
        assertEquals("UzairTestWord", word.displayText)
        assertEquals(3, word.frequency)
        assertTrue(word.confidence > 1)
        controller.close()
    }

    @Test
    fun sensitiveInputIsNeverQueuedAndRomanSpellingIsPreserved() {
        val repository = FakeRepository(CountDownLatch(1))
        val controller = PersonalLearningController(repository)
        val sensitive = EditorInputState.from(0, 0).copy(isSensitive = true)
        controller.recordWord("secretword", PredictionLanguage.ENGLISH, sensitive)
        controller.recordWord("mjhy", PredictionLanguage.ROMAN_URDU, EditorInputState.from(0, 0))

        Thread.sleep(100)
        assertTrue(repository.snapshot().none { it.displayText == "secretword" })
        assertTrue(repository.snapshot().any { it.displayText == "mjhy" })
        controller.close()
    }

    private class FakeRepository(private val learned: CountDownLatch) : PersonalVocabularyRepository {
        private val values = LinkedHashMap<String, PersonalWord>()

        @Synchronized
        override fun snapshot(): List<PersonalWord> = values.values.toList()

        @Synchronized
        override fun learn(
            word: String,
            language: PredictionLanguage,
            contextKey: String?,
            explicitlyAccepted: Boolean,
            nowMillis: Long,
        ) {
            val key = "$language|${PersonalLearningPolicy.normalize(word, language)}"
            val previous = values[key]
            values[key] = if (previous == null) {
                PersonalWord(key.substringAfter('|'), word, language, 1, 1, nowMillis, nowMillis, contextKey)
            } else {
                previous.copy(
                    displayText = word,
                    frequency = previous.frequency + 1,
                    confidence = previous.confidence + 1,
                    lastUsedMillis = nowMillis,
                )
            }
            learned.countDown()
        }

        override fun forget(word: PersonalWord) = Unit
        override fun clear() = Unit
        override fun close() = Unit
    }
}
