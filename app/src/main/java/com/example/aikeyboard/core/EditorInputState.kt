package com.example.aikeyboard.core

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class EditorFieldType {
    TEXT,
    MULTILINE_TEXT,
    SEARCH,
    EMAIL,
    URL,
    PHONE,
    NUMBER,
    PASSWORD,
    PIN,
}

/** Immutable editor metadata captured at an IME lifecycle boundary, never during key dispatch. */
data class EditorInputState(
    val inputType: Int,
    val imeOptions: Int,
    val fieldType: EditorFieldType,
    val isSensitive: Boolean,
    val noPersonalizedLearning: Boolean,
) {
    val isMultiline: Boolean
        get() = fieldType == EditorFieldType.MULTILINE_TEXT

    /** Prediction is disabled for fields where suggestions could expose sensitive input. */
    val suppressSuggestions: Boolean
        get() = isSensitive || fieldType in setOf(
            EditorFieldType.EMAIL,
            EditorFieldType.URL,
            EditorFieldType.PHONE,
            EditorFieldType.NUMBER,
        )

    companion object {
        fun from(info: EditorInfo): EditorInputState = from(info.inputType, info.imeOptions)

        fun from(inputType: Int, imeOptions: Int): EditorInputState {
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val noPersonalizedLearning =
                imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

            val fieldType = when {
                inputClass == InputType.TYPE_CLASS_PHONE -> EditorFieldType.PHONE
                inputClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> EditorFieldType.PIN
                inputClass == InputType.TYPE_CLASS_NUMBER -> EditorFieldType.NUMBER
                inputClass == InputType.TYPE_CLASS_TEXT &&
                    variation in PASSWORD_VARIATIONS -> EditorFieldType.PASSWORD
                inputClass == InputType.TYPE_CLASS_TEXT &&
                    variation in EMAIL_VARIATIONS -> EditorFieldType.EMAIL
                inputClass == InputType.TYPE_CLASS_TEXT &&
                    variation == InputType.TYPE_TEXT_VARIATION_URI -> EditorFieldType.URL
                imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH ->
                    EditorFieldType.SEARCH
                inputClass == InputType.TYPE_CLASS_TEXT &&
                    inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 ->
                    EditorFieldType.MULTILINE_TEXT
                else -> EditorFieldType.TEXT
            }

            return EditorInputState(
                inputType = inputType,
                imeOptions = imeOptions,
                fieldType = fieldType,
                isSensitive = noPersonalizedLearning || fieldType == EditorFieldType.PASSWORD ||
                    fieldType == EditorFieldType.PIN,
                noPersonalizedLearning = noPersonalizedLearning,
            )
        }

        private val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )

        private val EMAIL_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        )
    }
}
