package com.example.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.EditText
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
import com.example.aikeyboard.glide.GlideCommitter
import com.example.aikeyboard.glide.GlideController
import com.example.aikeyboard.glide.GlideEngine
import com.example.aikeyboard.glide.GlideGesture
import com.example.aikeyboard.prediction.LocalPredictionEngine
import com.example.aikeyboard.prediction.PredictionController
import com.example.aikeyboard.prediction.SuggestionCandidate
import com.example.aikeyboard.prediction.SuggestionSelectionEngine
import com.example.aikeyboard.settings.SharedPreferencesKeyboardSettings
import com.example.aikeyboard.settings.KeyboardSettings
import com.example.aikeyboard.settings.SettingsActivity
import com.example.aikeyboard.theme.KeyboardTheme
import com.example.aikeyboard.theme.ThemeActivity
import com.example.aikeyboard.theme.ThemeStore
import com.example.aikeyboard.emoji.EmojiPanelView
import com.example.aikeyboard.emoji.EmojiUsageStore
import com.example.aikeyboard.voice.SpeechLanguage
import com.example.aikeyboard.voice.TranslationTarget
import com.example.aikeyboard.voice.VoiceController
import com.example.aikeyboard.voice.VoicePanelView
import com.example.aikeyboard.voice.VoiceEvent
import com.example.aikeyboard.voice.VoicePermissionActivity
import com.example.aikeyboard.ui.KeyboardToolbarView
import com.example.aikeyboard.ui.KeyboardView
import com.example.aikeyboard.ui.ResizePanelView
import com.example.aikeyboard.ui.ToolsPanelView

/** Android IME lifecycle boundary. All text editing is delegated to the core typing engine. */
class KeyboardInputMethodService : InputMethodService() {

    private val layoutProvider = DefaultKeyboardLayoutProvider()
    private lateinit var keyboardState: KeyboardState
    private lateinit var typingEngine: TypingEngine
    private lateinit var compositionController: CompositionController
    private lateinit var predictionController: PredictionController
    private lateinit var learningController: PersonalLearningController
    private lateinit var keyboardSettings: SharedPreferencesKeyboardSettings
    private lateinit var personalVocabulary: PersonalVocabularyStore
    private lateinit var glideController: GlideController
    private lateinit var themeStore: ThemeStore
    private lateinit var emojiUsageStore: EmojiUsageStore
    private var glideTypingEnabled = true
    private var activeTheme = KeyboardTheme.SOLID_WHITE
    private var keyboardView: KeyboardView? = null
    private var toolbarView: KeyboardToolbarView? = null
    private var contentContainer: FrameLayout? = null
    private var rootView: LinearLayout? = null
    private var contentMode = ContentMode.KEYBOARD
    private var resizePanel: ResizePanelView? = null
    private var voiceController: VoiceController? = null
    private var safeAreaView: View? = null
    private var heightBeforeResizeDp = KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP
    private val mainHandler = Handler(Looper.getMainLooper())
    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTheme()
        }
    }

    @Volatile
    private var latestCompositionSnapshot: CompositionSnapshot? = null
    @Volatile
    private var preGestureSnapshot: CompositionSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        keyboardSettings = SharedPreferencesKeyboardSettings(this)
        themeStore = ThemeStore(this)
        emojiUsageStore = EmojiUsageStore(this)
        activeTheme = themeStore.activeTheme()
        val themeFilter = IntentFilter(ThemeStore.ACTION_THEME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeReceiver, themeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(themeReceiver, themeFilter)
        }
        glideTypingEnabled = keyboardSettings.glideTypingEnabled()
        keyboardState = KeyboardState(keyboardSettings.initialKeyboardMode())
        typingEngine = TypingEngine(keyboardState)
        compositionController = CompositionController()
        personalVocabulary = PersonalVocabularyStore(
            context = this,
            enabled = { keyboardSettings.personalizedSuggestionsEnabled() },
        )
        learningController = PersonalLearningController(
            store = personalVocabulary,
            enabled = { keyboardSettings.personalizedSuggestionsEnabled() },
        )
        glideController = GlideController(
            engine = GlideEngine(personalVocabulary = personalVocabulary),
            geometryProvider = { keyboardView?.currentGlideGeometry().orEmpty() },
        )
        predictionController = PredictionController(
            LocalPredictionEngine(personalVocabulary = personalVocabulary),
        )
    }

    override fun onCreateInputView(): View {
        val toolbar = KeyboardToolbarView(
            context = this,
            initialOrder = keyboardSettings.toolbarOrder(),
            onTools = ::showTools,
            onMic = ::showVoice,
            onToolbarAction = ::onToolbarAction,
            onCandidateSelected = ::onSuggestionSelected,
            onCandidateLongPressed = ::onSuggestionLongPressed,
            onOrderChanged = keyboardSettings::setToolbarOrder,
        )
        toolbarView = toolbar
        toolbar.applyTheme(activeTheme)

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
            glideEnabled = { glideTypingEnabled },
            onGlideStarted = {
                preGestureSnapshot = latestCompositionSnapshot
                glideController.cancel()
            },
            onGlideCompleted = ::onGlideCompleted,
            onGlideCancelled = ::onGlideCancelled,
        )
        keyboardView = view
        view.applyTheme(activeTheme)

        val container = FrameLayout(this)
        contentContainer = container
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TOOLBAR_HEIGHT_DP)
            ))
            addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            // The safe area is a real, fixed row. Keyboard and glide geometry never extend into it.
            addView(View(this@KeyboardInputMethodService).apply {
                safeAreaView = this
                setBackgroundColor(activeTheme.backgroundColor)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(SAFE_AREA_HEIGHT_DP)
            ))
        }
        setKeyboardContent()
        return rootView!!
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        refreshTheme()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        keyboardState.setEditorInfo(attribute)
        latestCompositionSnapshot = null
        clearSuggestions()
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshTheme()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
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
        toolbarView?.cancelPendingInput()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        clearSuggestions()
        keyboardState.setEditorOptions(0)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onWindowHidden() {
        keyboardView?.cancelPendingInput()
        toolbarView?.cancelPendingInput()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        clearSuggestions()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        keyboardView?.cancelPendingInput()
        toolbarView?.cancelPendingInput()
        keyboardView = null
        toolbarView = null
        contentContainer = null
        rootView = null
        resizePanel = null
        voiceController?.close()
        voiceController = null
        runCatching { unregisterReceiver(themeReceiver) }
        predictionController.close()
        glideController.close()
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
                    toolbarView?.setCandidates(result.candidates)
                }
            }
        }
    }

    private fun onSuggestionSelected(candidate: SuggestionCandidate) {
        val snapshot = latestCompositionSnapshot ?: return
        learningController.recordAcceptedSuggestion(candidate, snapshot)
        predictionController.clear()
        toolbarView?.clearCandidates()
        SuggestionSelectionEngine.apply(currentInputConnection, candidate, snapshot)
        requestCompositionSnapshot()
    }

    private fun onGlideCompleted(gesture: GlideGesture) {
        val snapshot = preGestureSnapshot
        glideController.recognize(gesture) { recognition ->
            mainHandler.post {
                val candidate = recognition.candidates.firstOrNull()
                if (candidate == null || recognition.confidence < MIN_GLIDE_CONFIDENCE) {
                    if (recognition.candidates.isNotEmpty()) {
                        toolbarView?.setCandidates(recognition.candidates)
                    }
                    requestCompositionSnapshot()
                    return@post
                }
                if (GlideCommitter.commit(currentInputConnection, gesture, snapshot, candidate.text)) {
                    snapshot?.let {
                        learningController.recordWord(
                            word = candidate.text,
                            language = candidate.language,
                            editor = it.editorInputState,
                            explicitlyAccepted = true,
                        )
                    }
                    toolbarView?.setCandidates(recognition.candidates)
                }
                preGestureSnapshot = null
                requestCompositionSnapshot()
            }
        }
    }

    private fun onGlideCancelled(gesture: GlideGesture) {
        GlideCommitter.removeProvisional(currentInputConnection, gesture.provisionalText)
        preGestureSnapshot = null
    }

    private fun onSuggestionLongPressed(candidate: SuggestionCandidate) {
        if (!candidate.isPersonal) return
        val word = learningControllerWord(candidate)
        learningController.forget(word)
        predictionController.clear()
        toolbarView?.clearCandidates()
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
        toolbarView?.clearCandidates()
    }

    private fun setKeyboardContent() {
        voiceController?.close()
        voiceController = null
        contentMode = ContentMode.KEYBOARD
        resizePanel = null
        refreshTheme()
        toolbarView?.setToolsMode(false)
        val container = contentContainer ?: return
        container.removeAllViews()
        keyboardView?.let { view ->
            view.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(keyboardSettings.keyboardHeightDp()),
                Gravity.BOTTOM,
            )
            container.addView(view)
        }
    }

    private fun showTools() {
        if (contentMode == ContentMode.TOOLS) {
            setKeyboardContent()
            return
        }
        contentMode = ContentMode.TOOLS
        clearSuggestions()
        toolbarView?.setToolsMode(true)
        contentContainer?.let { container ->
            container.removeAllViews()
            val tools = ToolsPanelView(this, ::onToolSelected)
            tools.applyTheme(activeTheme)
            container.addView(tools, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun onToolSelected(tool: String) {
        when (tool) {
            "settings" -> openSettings(focusSearch = false)
            "search" -> openSettings(focusSearch = true)
            "resize" -> showResize()
            "themes" -> openThemeActivity()
            "emoji" -> showEmoji()
            else -> setKeyboardContent()
        }
    }

    private fun onToolbarAction(action: String) {
        when (action) {
            "settings" -> openSettings(focusSearch = false)
            "search" -> openSettings(focusSearch = true)
            "resize" -> showResize()
        }
    }

    private fun openSettings(focusSearch: Boolean) {
        val intent = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SettingsActivity.EXTRA_FOCUS_SEARCH, focusSearch)
        startActivity(intent)
    }

    private fun openThemeActivity() {
        startActivity(Intent(this, ThemeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showEmoji() {
        contentMode = ContentMode.EMOJI
        clearSuggestions()
        toolbarView?.setToolsMode(true)
        val container = contentContainer ?: return
        container.removeAllViews()
        val emojiContainer = FrameLayout(this)
        val search = EditText(this).apply {
            hint = "Search emoji"
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
        }
        val panel = EmojiPanelView(
            context = this,
            usage = emojiUsageStore,
            onEmojiSelected = { emoji ->
                emojiUsageStore.record(emoji)
                currentInputConnection?.commitText(emoji, 1)
                requestCompositionSnapshot()
            },
            onBack = ::setKeyboardContent,
        )
        panel.setTheme(activeTheme)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                panel.setSearchQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        emojiContainer.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ).apply { topMargin = dp(44) })
        emojiContainer.addView(search, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(44), Gravity.TOP,
        ))
        container.addView(emojiContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun showVoice() {
        contentMode = ContentMode.VOICE
        clearSuggestions()
        toolbarView?.setToolsMode(true)
        val container = contentContainer ?: return
        container.removeAllViews()
        var panel: VoicePanelView? = null
        val controller = VoiceController(this, onEvent = { event ->
            mainHandler.post { panel?.handle(event) }
        })
        val voicePanel = VoicePanelView(
            context = this,
            onStart = { language ->
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else controller.start(language)
            },
            onPause = controller::pause,
            onResume = controller::resume,
            onStop = controller::stop,
            onCancel = controller::cancel,
            onTranslate = controller::translate,
            onInsert = { text ->
                currentInputConnection?.commitText(text, 1)
                requestCompositionSnapshot()
            },
            onBack = ::setKeyboardContent,
        )
        voicePanel.applyTheme(activeTheme)
        panel = voicePanel
        voiceController = controller
        container.addView(voicePanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun showResize() {
        contentMode = ContentMode.RESIZE
        clearSuggestions()
        toolbarView?.setToolsMode(true)
        heightBeforeResizeDp = keyboardSettings.keyboardHeightDp()
        var resizeHeightDp = heightBeforeResizeDp
        val container = contentContainer ?: return
        container.removeAllViews()
        keyboardView?.let { view ->
            updateKeyboardHeight(view, resizeHeightDp)
            container.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(resizeHeightDp),
                Gravity.BOTTOM,
            ))
        }
        lateinit var panel: ResizePanelView
        panel = ResizePanelView(
            context = this,
            initialHeightDp = resizeHeightDp,
            onHeightChanged = { value ->
                resizeHeightDp = value
                keyboardView?.let { updateKeyboardHeight(it, value) }
            },
            onDone = {
                keyboardSettings.setKeyboardHeightDp(resizeHeightDp)
                setKeyboardContent()
            },
            onCancel = {
                keyboardView?.let { updateKeyboardHeight(it, heightBeforeResizeDp) }
                setKeyboardContent()
            },
            onReset = {
                resizeHeightDp = KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP
                keyboardView?.let { updateKeyboardHeight(it, resizeHeightDp) }
                panel.setHeightDp(resizeHeightDp)
            },
        )
        resizePanel = panel
        container.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun updateKeyboardHeight(view: KeyboardView, heightDp: Int) {
        view.layoutParams = (view.layoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(heightDp), Gravity.BOTTOM
        )).apply {
            height = dp(heightDp)
            if (this is FrameLayout.LayoutParams) gravity = Gravity.BOTTOM
        }
        view.requestLayout()
        view.invalidate()
    }

    private fun refreshTheme() {
        if (!::themeStore.isInitialized) return
        activeTheme = themeStore.activeTheme()
        toolbarView?.applyTheme(activeTheme)
        keyboardView?.applyTheme(activeTheme)
        safeAreaView?.setBackgroundColor(activeTheme.backgroundColor)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class ContentMode { KEYBOARD, TOOLS, RESIZE, EMOJI, VOICE }

    private companion object {
        const val TOOLBAR_HEIGHT_DP = 56
        const val SAFE_AREA_HEIGHT_DP = 8
        const val MIN_GLIDE_CONFIDENCE = 48
    }
}
