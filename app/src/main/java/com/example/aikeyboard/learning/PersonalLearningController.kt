package com.example.aikeyboard.learning

import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.EditorInputState
import com.example.aikeyboard.prediction.PredictionLanguage
import com.example.aikeyboard.prediction.SuggestionCandidate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class LearningCommitReason {
    SPACE,
    PUNCTUATION,
    ENTER,
    ACCEPTED_SUGGESTION,
}

/** Schedules learning only after a meaningful commit; it never runs on ACTION_DOWN/ACTION_UP. */
class PersonalLearningController(
    private val store: PersonalVocabularyRepository,
    private val enabled: () -> Boolean = { true },
) : AutoCloseable {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "personal-learning").apply { isDaemon = true }
    }

    fun recordSnapshot(snapshot: CompositionSnapshot, reason: LearningCommitReason) {
        if (!enabled() || !PersonalLearningPolicy.canLearn(snapshot.editorInputState)) return
        val word = snapshot.currentWord.trim()
        if (!PersonalLearningPolicy.isUsefulWord(word)) return
        val language = PersonalLearningPolicy.languageFor(word, snapshot.activeLanguage)
        val context = contextKey(snapshot.textBeforeCursor, snapshot.currentWordBeforeCursor)
        recordWord(
            word = word,
            language = language,
            editor = snapshot.editorInputState,
            contextKey = context,
            explicitlyAccepted = reason == LearningCommitReason.ACCEPTED_SUGGESTION,
        )
    }

    fun recordAcceptedSuggestion(candidate: SuggestionCandidate, snapshot: CompositionSnapshot) {
        if (!enabled() || !PersonalLearningPolicy.canLearn(snapshot.editorInputState)) return
        recordWord(
            word = candidate.text,
            language = candidate.language,
            editor = snapshot.editorInputState,
            contextKey = contextKey(snapshot.textBeforeCursor, snapshot.currentWordBeforeCursor),
            explicitlyAccepted = true,
        )
    }

    fun recordWord(
        word: String,
        language: PredictionLanguage,
        editor: EditorInputState,
        contextKey: String? = null,
        explicitlyAccepted: Boolean = false,
    ) {
        if (!enabled() || !PersonalLearningPolicy.canLearn(editor)) return
        executor.execute {
            if (enabled() && PersonalLearningPolicy.isUsefulWord(word)) {
                store.learn(word, language, contextKey, explicitlyAccepted)
            }
        }
    }

    fun forget(candidate: PersonalWord) {
        executor.execute { store.forget(candidate) }
    }

    fun clear() {
        executor.execute { store.clear() }
    }

    override fun close() {
        executor.shutdownNow()
        store.close()
    }

    private fun contextKey(beforeCursor: String, currentWord: String): String? {
        val prefix = if (currentWord.isEmpty()) beforeCursor else beforeCursor.removeSuffix(currentWord)
        val tokens = prefix.split(Regex("[^\\p{L}\\p{M}\\p{Nd}_']+"))
            .filter { it.isNotBlank() }
            .takeLast(MAX_CONTEXT_WORDS)
        return tokens.joinToString(" ") { it.lowercase() }.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val MAX_CONTEXT_WORDS = 2
    }
}
