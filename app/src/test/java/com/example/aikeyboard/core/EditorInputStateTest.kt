package com.example.aikeyboard.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorInputStateTest {
    @Test
    fun commonEditorKindsAreClassifiedWithoutTouchPathWork() {
        assertEquals(
            EditorFieldType.EMAIL,
            EditorInputState.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0).fieldType,
        )
        assertEquals(
            EditorFieldType.URL,
            EditorInputState.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 0).fieldType,
        )
        assertEquals(
            EditorFieldType.PIN,
            EditorInputState.from(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0).fieldType,
        )
    }

    @Test
    fun passwordAndNoLearningFieldsAreSensitive() {
        val password = EditorInputState.from(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            0,
        )
        val noLearning = EditorInputState.from(
            InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )

        assertTrue(password.isSensitive)
        assertTrue(noLearning.isSensitive)
        assertFalse(EditorInputState.from(InputType.TYPE_CLASS_TEXT, 0).isSensitive)
    }

    @Test
    fun structuredContactFieldsSuppressSuggestionsWithoutBeingPasswords() {
        val email = EditorInputState.from(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            0,
        )
        val phone = EditorInputState.from(InputType.TYPE_CLASS_PHONE, 0)
        val number = EditorInputState.from(InputType.TYPE_CLASS_NUMBER, 0)

        assertFalse(email.isSensitive)
        assertTrue(email.suppressSuggestions)
        assertTrue(phone.suppressSuggestions)
        assertTrue(number.suppressSuggestions)
    }
}
