package com.example.aikeyboard.learning

import com.example.aikeyboard.core.EditorFieldType
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.prediction.PredictionLanguage
import com.example.aikeyboard.prediction.RomanUrdu

/** Privacy and quality gate used before any personal word reaches local storage. */
object PersonalLearningPolicy {

    fun canLearn(editor: EditorInputState): Boolean {
        if (editor.isSensitive || editor.noPersonalizedLearning) return false
        return editor.fieldType !in setOf(
            EditorFieldType.EMAIL,
            EditorFieldType.URL,
            EditorFieldType.PHONE,
            EditorFieldType.NUMBER,
            EditorFieldType.PASSWORD,
            EditorFieldType.PIN,
        )
    }

    fun isUsefulWord(word: String): Boolean {
        val value = word.trim()
        if (value.length < 2 || value.length > MAX_WORD_LENGTH) return false
        if (value.any(Char::isWhitespace)) return false
        if (value.contains("@") || value.contains("://") || value.contains("www.", true)) {
            return false
        }
        val codePoints = value.codePoints().toArray()
        if (codePoints.isEmpty() || codePoints.none { Character.isLetter(it) }) return false
        val digits = codePoints.count { Character.isDigit(it) }
        if (digits == codePoints.size || (digits >= 4 && digits * 2 >= codePoints.size)) {
            return false
        }
        // Avoid learning a punctuation-heavy or random fragment while allowing names and Urdu.
        val letters = codePoints.count { Character.isLetter(it) }
        val punctuation = codePoints.size - letters - digits
        return letters >= 2 && punctuation <= 2
    }

    fun languageFor(word: String, activeLanguage: KeyboardLanguage): PredictionLanguage {
        if (word.any { isUrduCodePoint(it.code) }) return PredictionLanguage.URDU
        if (activeLanguage == KeyboardLanguage.URDU) return PredictionLanguage.ROMAN_URDU
        return PredictionLanguage.ENGLISH
    }

    fun normalize(word: String, language: PredictionLanguage): String = when (language) {
        PredictionLanguage.ROMAN_URDU -> RomanUrdu.normalize(word)
        else -> word.lowercase(java.util.Locale.ROOT)
    }

    private fun isUrduCodePoint(codePoint: Int): Boolean =
        codePoint in 0x0600..0x06FF || codePoint in 0x0750..0x077F ||
            codePoint in 0x08A0..0x08FF || codePoint in 0xFB50..0xFDFF ||
            codePoint in 0xFE70..0xFEFF

    private const val MAX_WORD_LENGTH = 48
}
