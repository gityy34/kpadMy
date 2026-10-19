package com.example.aikeyboard.learning

import com.example.aikeyboard.core.EditorFieldType
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalLearningPolicyTest {
    private val normal = EditorInputState.from(0, 0)

    @Test
    fun acceptsUsefulEnglishUrduAndRomanWords() {
        assertTrue(PersonalLearningPolicy.canLearn(normal))
        assertTrue(PersonalLearningPolicy.isUsefulWord("UzairTestWord"))
        assertTrue(PersonalLearningPolicy.isUsefulWord("آپ"))
        assertTrue(PersonalLearningPolicy.isUsefulWord("mjhy"))
        assertTrue(
            PersonalLearningPolicy.languageFor("آپ", KeyboardLanguage.URDU) ==
                com.example.aikeyboard.prediction.PredictionLanguage.URDU,
        )
    }

    @Test
    fun blocksSensitiveAndNonVocabularyValues() {
        assertFalse(PersonalLearningPolicy.canLearn(normal.copy(isSensitive = true)))
        assertFalse(PersonalLearningPolicy.canLearn(normal.copy(fieldType = EditorFieldType.EMAIL)))
        assertFalse(PersonalLearningPolicy.canLearn(normal.copy(fieldType = EditorFieldType.URL)))
        assertFalse(PersonalLearningPolicy.isUsefulWord("a"))
        assertFalse(PersonalLearningPolicy.isUsefulWord("1234567890"))
        assertFalse(PersonalLearningPolicy.isUsefulWord("user@example.com"))
        assertFalse(PersonalLearningPolicy.isUsefulWord("https://example.com/token"))
    }
}
