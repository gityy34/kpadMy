package com.example.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import com.example.aikeyboard.core.CompositionController
import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.DefaultKeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardState
import com.example.aikeyboard.core.TypingEngine
import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyType
import com.example.aikeyboard.learning.LearningCommitReason
import com.example.aikeyboard.learning.PersonalLearningController
import com.example.aikeyboard.learning.PersonalVocabularyStore
import com.example.aikeyboard.prediction.LocalPredictionEngine
import com.example.aikeyboard.prediction.PredictionController
import com.example.aikeyboard.prediction.SuggestionCandidate
import com.example.aikeyboard.prediction.SuggestionSelectionEngine
import com.example.aikeyboard.settings.SharedPreferencesKeyboardSettings
import com.example.aikeyboard.ui.KeyboardView
import com.example.aikeyboard.ui.SuggestionStripView

/** Android IME lifecycle boundary. All text editing is delegated to the core typing engine. */
class KeyboardInputMethodService : InputMethodService() {

    private val layoutProvider = DefaultKeyboardLayoutProvider()
    private lateinit var keyboardState: KeyboardState
    private lateinit var typingEngine: TypingEngine
    private lateinit var compositionController: CompositionController
    private lateinit var predictionController: PredictionController
    private lateinit var learningController: PersonalLearningController
    private lateinit var keyboardSettings: SharedPreferencesKeyboardSettings
    private var keyboardView: KeyboardView? = null
    private var suggestionStripView: SuggestionStripView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var latestCompositionSnapshot: CompositionSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        keyboardSettings = SharedPreferencesKeyboardSettings(this)
        keyboardState = KeyboardState(keyboardSettings.initialKeyboardMode())
        typingEngine = TypingEngine(keyboardState)
        compositionController = CompositionController()
        val personalVocabulary = PersonalVocabularyStore(
            context = this,
            enabled = { keyboardSettings.personalizedSuggestionsEnabled() },
        )
        learningController = PersonalLearningController(
            store = personalVocabulary,
            enabled = { keyboardSettings.personalizedSuggestionsEnabled() },
        )
        predictionController = PredictionController(
            LocalPredictionEngine(personalVocabulary = personalVocabulary),
        )
    }

    override fun onCreateInputView(): View {
        val strip = SuggestionStripView(
            this,
            onCandidateSelected = ::onSuggestionSelected,
            onCandidateLongPressed = ::onSuggestionLongPressed,
        )
        suggestionStripView = strip

        val view = KeyboardView(
            context = this,
            layoutProvider = layoutProvider,
            state = keyboardState,
            onKeyPressed = { key ->
                // InputConnection is read at dispatch time because editors can change without
                // recreating the IME process.
                val committed = typingEngine.handle(key, currentInputConnection)
                if (committed) recordLearningBoundary(key)
                // This only schedules a bounded background read after the synchronous commit.
                // It never sits in front of the key-to-InputConnection path.
                requestCompositionSnapshot()
            },
        )
        keyboardView = view

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(strip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SuggestionStripViewHeightDp),
            ))
            addView(view, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardState.setEditorInfo(attribute)
        latestCompositionSnapshot = null
        clearSuggestions()
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardState.setEditorInfo(info)
        latestCompositionSnapshot = null
        clearSuggestions()
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        latestCompositionSnapshot = null
        keyboardView?.invalidate()
        requestCompositionSnapshot()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        compositionController.updateSelection(newSelStart, newSelEnd)
        requestCompositionSnapshot()
    }

    override fun onFinishInput() {
        keyboardView?.cancelPendingInput()
        clearSuggestions()
        keyboardState.setEditorOptions(0)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onWindowHidden() {
        keyboardView?.cancelPendingInput()
        clearSuggestions()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        keyboardView?.cancelPendingInput()
        keyboardView = null
        suggestionStripView = null
        predictionController.close()
        learningController.close()
        compositionController.close()
        super.onDestroy()
    }

    private fun requestCompositionSnapshot() {
        if (keyboardState.editorInputState.isSensitive) {
            clearSuggestions()
            return
        }
        compositionController.requestSnapshot(
            inputConnection = currentInputConnection,
            editorInputState = keyboardState.editorInputState,
            activeLanguage = keyboardState.language,
        ) { snapshot ->
            latestCompositionSnapshot = snapshot
            predictionController.request(snapshot) { result ->
                mainHandler.post {
                    val current = latestCompositionSnapshot
                    if (result.requestId != predictionController.latestRequestId() ||
                        current?.generation != result.snapshot.generation
                    ) return@post
                    suggestionStripView?.setCandidates(result.candidates)
                }
            }
        }
    }

    private fun onSuggestionSelected(candidate: SuggestionCandidate) {
        val snapshot = latestCompositionSnapshot ?: return
        learningController.recordAcceptedSuggestion(candidate, snapshot)
        predictionController.clear()
        suggestionStripView?.clearCandidates()
        SuggestionSelectionEngine.apply(currentInputConnection, candidate, snapshot)
        requestCompositionSnapshot()
    }

    private fun onSuggestionLongPressed(candidate: SuggestionCandidate) {
        if (!candidate.isPersonal) return
        val word = learningControllerWord(candidate)
        learningController.forget(word)
        predictionController.clear()
        suggestionStripView?.clearCandidates()
        requestCompositionSnapshot()
    }

    private fun learningControllerWord(candidate: SuggestionCandidate) =
        com.example.aikeyboard.learning.PersonalWord(
            normalizedWord = com.example.aikeyboard.learning.PersonalLearningPolicy
                .normalize(candidate.text, candidate.language),
            displayText = candidate.text,
            language = candidate.language,
            frequency = 0,
            confidence = 0,
            firstSeenMillis = 0L,
            lastUsedMillis = 0L,
        )

    private fun recordLearningBoundary(key: KeySpec) {
        val reason = when {
            key.type == KeyType.SPACE -> LearningCommitReason.SPACE
            key.type == KeyType.ENTER -> LearningCommitReason.ENTER
            key.type == KeyType.CHARACTER && isBoundaryText(key.outputText) ->
                LearningCommitReason.PUNCTUATION
            else -> null
        } ?: return
        latestCompositionSnapshot?.let { learningController.recordSnapshot(it, reason) }
    }

    private fun isBoundaryText(text: String): Boolean {
        if (text.isEmpty()) return false
        val codePoint = text.codePointAt(0)
        return !Character.isLetterOrDigit(codePoint) &&
            codePoint != '_'.code && codePoint != '\''.code
    }

    private fun clearSuggestions() {
        latestCompositionSnapshot = null
        predictionController.clear()
        suggestionStripView?.clearCandidates()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SuggestionStripViewHeightDp = 56
    }
}
