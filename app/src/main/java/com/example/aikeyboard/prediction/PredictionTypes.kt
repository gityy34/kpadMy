package com.example.aikeyboard.prediction

import com.example.aikeyboard.core.CompositionSnapshot

enum class PredictionLanguage {
    ENGLISH,
    URDU,
    ROMAN_URDU,
}

enum class SuggestionKind {
    TYPED,
    COMPLETION,
    CORRECTION,
    NEXT_WORD,
}

data class SuggestionCandidate(
    val text: String,
    val language: PredictionLanguage,
    val score: Int,
    val kind: SuggestionKind,
)

data class PredictionResult(
    val requestId: Long,
    val snapshot: CompositionSnapshot,
    val candidates: List<SuggestionCandidate>,
)

fun interface PredictionEngine {
    fun predict(snapshot: CompositionSnapshot): List<SuggestionCandidate>
}
