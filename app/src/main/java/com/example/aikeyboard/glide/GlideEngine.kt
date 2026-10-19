package com.example.aikeyboard.glide

import com.example.aikeyboard.core.KeyType
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.learning.EmptyPersonalVocabulary
import com.example.aikeyboard.learning.PersonalVocabularySource
import com.example.aikeyboard.learning.PersonalWord
import com.example.aikeyboard.prediction.LocalLexicon
import com.example.aikeyboard.prediction.PredictionLanguage
import com.example.aikeyboard.prediction.RomanUrdu
import com.example.aikeyboard.prediction.SuggestionCandidate
import com.example.aikeyboard.prediction.SuggestionKind
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Release-time, on-device gesture matcher. ACTION_MOVE only collects points; dictionary work
 * happens after ACTION_UP on the GlideController executor.
 */
class GlideEngine(
    private val lexicon: LocalLexicon = LocalLexicon(),
    private val personalVocabulary: PersonalVocabularySource = EmptyPersonalVocabulary,
) {

    fun recognize(
        gesture: GlideGesture,
        geometry: List<GlideKeyGeometry>,
    ): GlideRecognition {
        val extracted = extractPath(gesture.path, geometry)
        if (extracted.length < MIN_PATH_LENGTH) {
            return GlideRecognition(emptyList(), 0, extracted)
        }

        val languageOrder = if (containsUrdu(extracted)) {
            listOf(PredictionLanguage.URDU)
        } else if (gesture.activeLanguage == KeyboardLanguage.URDU ||
            gesture.activeLanguage == KeyboardLanguage.ROMAN_URDU
        ) {
            listOf(PredictionLanguage.ROMAN_URDU, PredictionLanguage.ENGLISH)
        } else {
            listOf(PredictionLanguage.ENGLISH, PredictionLanguage.ROMAN_URDU)
        }

        val candidates = ArrayList<SuggestionCandidate>(12)
        for ((languageRank, language) in languageOrder.withIndex()) {
            for (entry in lexicon.entries(language)) {
                val match = bestMatch(extracted, language, lexicon.forms(entry)) ?: continue
                if (!isPlausible(match.distance, extracted.length, entry.text.length)) continue
                candidates += SuggestionCandidate(
                    text = entry.text,
                    language = language,
                    score = geometricScore(match.distance, extracted, entry.text) +
                        entry.frequency / 20 + (languageOrder.size - languageRank) * LANGUAGE_SCORE,
                    kind = SuggestionKind.COMPLETION,
                )
            }
            addPersonalCandidates(candidates, extracted, language, languageRank, languageOrder.size)
        }

        val unique = deduplicate(candidates)
        val confidence = unique.firstOrNull()?.let { candidate ->
            val distance = bestDistance(extracted, candidate.text, candidate.language)
            ((1f - distance.toFloat() / max(extracted.length, candidate.text.length)) * 100f)
                .toInt().coerceIn(0, 100)
        } ?: 0
        return GlideRecognition(unique, confidence, extracted)
    }

    private fun extractPath(
        path: List<GlidePoint>,
        geometry: List<GlideKeyGeometry>,
    ): String {
        val output = StringBuilder(16)
        var previousKey: GlideKeyGeometry? = null
        for (point in path) {
            val current = nearestKey(point, geometry) ?: continue
            if (current === previousKey) continue
            val key = current.key
            if (key.type != KeyType.CHARACTER || key.outputText.isEmpty()) continue
            if (key.outputText.any { it.isLetterOrDigit() }) {
                output.append(key.outputText.lowercase(Locale.ROOT))
                previousKey = current
            }
        }
        return output.toString()
    }

    private fun nearestKey(point: GlidePoint, geometry: List<GlideKeyGeometry>): GlideKeyGeometry? {
        var best: GlideKeyGeometry? = null
        var bestDistance = Float.MAX_VALUE
        for (candidate in geometry) {
            if (candidate.key.type != KeyType.CHARACTER || candidate.key.outputText.isEmpty()) continue
            val inside = point.x >= candidate.left && point.x <= candidate.right &&
                point.y >= candidate.top && point.y <= candidate.bottom
            val distance = if (inside) 0f else hypot(
                point.x - candidate.centerX,
                point.y - candidate.centerY,
            )
            val tolerance = minOf(candidate.right - candidate.left, candidate.bottom - candidate.top) * 0.72f
            if (distance <= tolerance && distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    private fun addPersonalCandidates(
        output: MutableList<SuggestionCandidate>,
        extracted: String,
        language: PredictionLanguage,
        languageRank: Int,
        languageCount: Int,
    ) {
        for (word in personalVocabulary.snapshot()) {
            if (word.language != language) continue
            val distance = bestDistance(extracted, word.displayText, language)
            if (!isPlausible(distance, extracted.length, word.displayText.length)) continue
            output += SuggestionCandidate(
                text = word.displayText,
                language = language,
                score = geometricScore(distance, extracted, word.displayText) +
                    personalScore(word) + (languageCount - languageRank) * LANGUAGE_SCORE,
                kind = SuggestionKind.COMPLETION,
                isPersonal = true,
            )
        }
    }

    private fun bestMatch(
        extracted: String,
        language: PredictionLanguage,
        forms: Set<String>,
    ): Match? = forms.map { form ->
        Match(editDistance(formFor(extracted, language), formFor(form, language)), form)
    }.minByOrNull { it.distance }

    private fun bestDistance(
        extracted: String,
        word: String,
        language: PredictionLanguage,
    ): Int = editDistance(formFor(extracted, language), formFor(word, language))

    private fun geometricScore(distance: Int, extracted: String, word: String): Int =
        10_000 - distance * DISTANCE_PENALTY -
            abs(extracted.length - word.length) * LENGTH_PENALTY +
            repeatedLetterBonus(extracted, word)

    private fun repeatedLetterBonus(extracted: String, word: String): Int {
        val collapsed = buildString(word.length) {
            var previous = '\u0000'
            for (character in word.lowercase(Locale.ROOT)) {
                if (character != previous) append(character)
                previous = character
            }
        }
        return if (collapsed == extracted.lowercase(Locale.ROOT)) REPEATED_LETTER_BONUS else 0
    }

    private fun personalScore(word: PersonalWord): Int =
        word.confidence * 30 + word.frequency.coerceAtMost(100) * 8

    private fun isPlausible(distance: Int, pathLength: Int, wordLength: Int): Boolean =
        distance <= max(MAX_EDIT_DISTANCE, max(pathLength, wordLength) / 3)

    private fun deduplicate(candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
        val best = LinkedHashMap<String, SuggestionCandidate>()
        candidates.forEach { candidate ->
            val key = candidate.text.lowercase(Locale.ROOT)
            val current = best[key]
            if (current == null || candidate.score > current.score) best[key] = candidate
        }
        return best.values.sortedWith(
            compareByDescending<SuggestionCandidate> { it.score }.thenBy { it.text },
        ).take(MAX_CANDIDATES)
    }

    private fun formFor(value: String, language: PredictionLanguage): String = when (language) {
        PredictionLanguage.ROMAN_URDU -> RomanUrdu.normalize(value)
        else -> value.lowercase(Locale.ROOT)
    }

    private fun editDistance(left: String, right: String): Int {
        if (left == right) return 0
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun containsUrdu(value: String): Boolean = value.any { character ->
        character.code in 0x0600..0x06FF || character.code in 0x0750..0x077F ||
            character.code in 0x08A0..0x08FF
    }

    private data class Match(val distance: Int, val form: String)

    private companion object {
        const val MIN_PATH_LENGTH = 2
        const val MAX_EDIT_DISTANCE = 2
        const val MAX_CANDIDATES = 4
        const val DISTANCE_PENALTY = 1_600
        const val LENGTH_PENALTY = 180
        const val REPEATED_LETTER_BONUS = 1_800
        const val LANGUAGE_SCORE = 350
    }
}
