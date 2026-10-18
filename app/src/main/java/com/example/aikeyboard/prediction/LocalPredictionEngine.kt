package com.example.aikeyboard.prediction

import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.KeyboardLanguage
import java.util.Locale
import kotlin.math.max

/** Local, bounded prediction implementation. It has no network, database, or user-learning path. */
class LocalPredictionEngine(
    private val lexicon: LocalLexicon = LocalLexicon(),
) : PredictionEngine {

    override fun predict(snapshot: CompositionSnapshot): List<SuggestionCandidate> {
        if (snapshot.editorInputState.isSensitive) return emptyList()

        val before = snapshot.textBeforeCursor
        val currentWord = snapshot.currentWord
        val context = contextWords(before.removeSuffix(snapshot.currentWordBeforeCursor))

        val candidates = if (currentWord.isNotEmpty()) {
            wordCandidates(snapshot, currentWord, context)
        } else if (endsSentence(before)) {
            emptyList()
        } else {
            nextWordCandidates(snapshot, context)
        }

        return deduplicateAndRank(candidates)
    }

    private fun wordCandidates(
        snapshot: CompositionSnapshot,
        query: String,
        context: List<String>,
    ): List<SuggestionCandidate> {
        val result = ArrayList<SuggestionCandidate>(12)
        val queryLanguage = languageOf(query, snapshot.activeLanguage)
        result += SuggestionCandidate(
            text = query,
            language = queryLanguage,
            score = TYPED_SCORE,
            kind = SuggestionKind.TYPED,
        )

        val preferred = preferredLanguages(snapshot, query)
        for ((languageRank, language) in preferred.withIndex()) {
            val normalizedQuery = formFor(language, query)
            val prefixEntries = lexicon.prefixEntries(language, query)
            val entries = if (prefixEntries.isNotEmpty()) {
                prefixEntries
            } else {
                lexicon.entries(language)
            }

            for (entry in entries) {
                val matchingForm = lexicon.forms(entry).minByOrNull {
                    editDistance(normalizedQuery, formFor(language, it), MAX_EDIT_DISTANCE + 1)
                } ?: continue
                val normalizedForm = formFor(language, matchingForm)
                val isPrefix = normalizedForm.startsWith(normalizedQuery)
                val distance = if (isPrefix) 0 else {
                    editDistance(normalizedQuery, normalizedForm, MAX_EDIT_DISTANCE + 1)
                }
                if (!isPrefix && distance > MAX_EDIT_DISTANCE) continue

                val kind = if (isPrefix) SuggestionKind.COMPLETION else SuggestionKind.CORRECTION
                val prefixScore = if (isPrefix) normalizedQuery.length * PREFIX_CHARACTER_SCORE else 0
                val distanceScore = if (isPrefix) 0 else (MAX_EDIT_DISTANCE - distance) * EDIT_DISTANCE_SCORE
                val languageScore = (preferred.size - languageRank) * LANGUAGE_SCORE
                val contextScore = if (context.isNotEmpty() &&
                    lexicon.nextWords(context).any { sameWord(it, entry.text, language) }
                ) CONTEXT_SCORE else 0
                result += SuggestionCandidate(
                    text = entry.text,
                    language = language,
                    score = frequencyScore(entry.frequency) + prefixScore + distanceScore +
                        languageScore + contextScore,
                    kind = kind,
                )
            }
        }
        return result
    }

    private fun nextWordCandidates(
        snapshot: CompositionSnapshot,
        context: List<String>,
    ): List<SuggestionCandidate> {
        val result = ArrayList<SuggestionCandidate>(8)
        val preferred = preferredLanguages(snapshot, "")
        val nextWords = lexicon.nextWords(context)
        val fallback = if (nextWords.isNotEmpty()) nextWords else {
            preferred.flatMap { language ->
                lexicon.entries(language)
                    .sortedByDescending { it.frequency }
                    .take(3)
                    .map { it.text }
            }
        }

        for (word in fallback) {
            val language = languageOf(word, snapshot.activeLanguage)
            val entry = lexicon.entries(language).firstOrNull {
                sameWord(it.text, word, language) || lexicon.forms(it).any { form -> sameWord(form, word, language) }
            }
            val frequency = entry?.frequency ?: 1
            val languageScore = preferred.indexOf(language).let { rank ->
                if (rank < 0) 0 else (preferred.size - rank) * LANGUAGE_SCORE
            }
            result += SuggestionCandidate(
                text = word,
                language = language,
                score = NEXT_WORD_SCORE + frequencyScore(frequency) + languageScore,
                kind = SuggestionKind.NEXT_WORD,
            )
        }
        return result
    }

    private fun preferredLanguages(
        snapshot: CompositionSnapshot,
        query: String,
    ): List<PredictionLanguage> {
        val queryLanguage = if (query.isEmpty()) null else languageOf(query, snapshot.activeLanguage)
        return when {
            queryLanguage == PredictionLanguage.URDU -> listOf(
                PredictionLanguage.URDU,
                PredictionLanguage.ROMAN_URDU,
                PredictionLanguage.ENGLISH,
            )

            queryLanguage == PredictionLanguage.ROMAN_URDU -> listOf(
                PredictionLanguage.ROMAN_URDU,
                PredictionLanguage.ENGLISH,
                PredictionLanguage.URDU,
            )

            snapshot.activeLanguage == KeyboardLanguage.URDU -> listOf(
                PredictionLanguage.URDU,
                PredictionLanguage.ROMAN_URDU,
                PredictionLanguage.ENGLISH,
            )

            else -> listOf(
                PredictionLanguage.ENGLISH,
                PredictionLanguage.ROMAN_URDU,
                PredictionLanguage.URDU,
            )
        }
    }

    private fun languageOf(text: String, activeLanguage: KeyboardLanguage): PredictionLanguage {
        if (text.any { isUrduCodePoint(it.code) }) return PredictionLanguage.URDU
        if (activeLanguage == KeyboardLanguage.URDU && text.any { it.isLetter() }) {
            return PredictionLanguage.ROMAN_URDU
        }
        return PredictionLanguage.ENGLISH
    }

    private fun contextWords(text: String): List<String> {
        val words = ArrayList<String>(4)
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isWordCodePoint(codePoint)) {
                current.appendCodePoint(codePoint)
            } else {
                if (current.isNotEmpty()) {
                    words += current.toString()
                    current.setLength(0)
                }
                if (isSentencePunctuation(codePoint)) words.clear()
            }
            index += Character.charCount(codePoint)
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.takeLast(MAX_CONTEXT_WORDS)
    }

    private fun deduplicateAndRank(
        candidates: List<SuggestionCandidate>,
    ): List<SuggestionCandidate> {
        val best = LinkedHashMap<String, SuggestionCandidate>()
        for (candidate in candidates) {
            // Keep meaningful Roman Urdu spelling variants (for example kyaa and kya) visible;
            // deduplicate only the same rendered candidate, not distinct user-facing spellings.
            val key = candidate.text.lowercase(Locale.ROOT)
            val existing = best[key]
            if (existing == null || candidate.score > existing.score ||
                candidate.kind == SuggestionKind.TYPED
            ) {
                best[key] = candidate
            }
        }
        return best.values.sortedWith(
            compareByDescending<SuggestionCandidate> { it.kind == SuggestionKind.TYPED }
                .thenByDescending { it.score }
                .thenBy { it.text },
        ).take(MAX_SUGGESTIONS)
    }

    private fun sameWord(left: String, right: String, language: PredictionLanguage): Boolean =
        formFor(language, left) == formFor(language, right)

    private fun formFor(language: PredictionLanguage, value: String): String = when (language) {
        PredictionLanguage.ROMAN_URDU -> RomanUrdu.normalize(value)
        else -> value.lowercase(Locale.ROOT)
    }

    private fun frequencyScore(frequency: Int): Int = max(1, frequency / 100)

    private fun editDistance(left: String, right: String, limit: Int): Int {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > limit) return limit
        var previousPrevious = IntArray(right.length + 1) { it }
        var previous = previousPrevious.copyOf()
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                val insertion = current[j] + 1
                val deletion = previous[j + 1] + 1
                var value = minOf(substitution, insertion, deletion)
                if (i > 0 && j > 0 && left[i] == right[j - 1] && left[i - 1] == right[j]) {
                    value = minOf(value, previousPrevious[j - 1] + 1)
                }
                current[j + 1] = value
            }
            previousPrevious = previous
            previous = current
        }
        return previous[right.length]
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == '_'.code || codePoint == '\''.code
    }

    private fun isUrduCodePoint(codePoint: Int): Boolean =
        codePoint in 0x0600..0x06FF || codePoint in 0x0750..0x077F ||
            codePoint in 0x08A0..0x08FF || codePoint in 0xFB50..0xFDFF ||
            codePoint in 0xFE70..0xFEFF

    private fun isSentencePunctuation(codePoint: Int): Boolean =
        codePoint == '.'.code || codePoint == '!'.code || codePoint == '?'.code ||
            codePoint == '؟'.code || codePoint == '۔'.code || codePoint == '\n'.code

    private fun endsSentence(text: String): Boolean {
        val last = text.trimEnd().lastOrNull()?.code ?: return false
        return isSentencePunctuation(last)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
        const val MAX_CONTEXT_WORDS = 4
        const val MAX_EDIT_DISTANCE = 2
        const val TYPED_SCORE = 100_000
        const val NEXT_WORD_SCORE = 70_000
        const val PREFIX_CHARACTER_SCORE = 500
        const val EDIT_DISTANCE_SCORE = 1_200
        const val LANGUAGE_SCORE = 400
        const val CONTEXT_SCORE = 1_000
    }
}
