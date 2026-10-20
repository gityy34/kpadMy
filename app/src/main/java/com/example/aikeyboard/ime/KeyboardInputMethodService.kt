package com.example.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import com.example.aikeyboard.core.CompositionController
import com.example.aikeyboard.core.CompositionSnapshot
import com.example.aikeyboard.core.DefaultKeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.core.KeyboardState
import com.example.aikeyboard.core.TypingEngine
import com.example.aikeyboard.core.KeyboardEditHistory
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
import com.example.aikeyboard.settings.ResizeConfiguration
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
import com.example.aikeyboard.ui.FixedImeLayout
import com.example.aikeyboard.ui.LanguagePanelView
import com.example.aikeyboard.ui.ResizePanelView
import com.example.aikeyboard.ui.ToolsPanelView
import kotlin.math.roundToInt

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
    private val editHistory = KeyboardEditHistory()
    private var glideTypingEnabled = true
    private var activeTheme = KeyboardTheme.SOLID_WHITE
    private var keyboardView: KeyboardView? = null
    private var toolbarView: KeyboardToolbarView? = null
    private var contentContainer: FrameLayout? = null
    private var workspaceContainer: FrameLayout? = null
    private var rootView: FixedImeLayout? = null
    private var contentMode = ContentMode.KEYBOARD
    private var resizePanel: ResizePanelView? = null
    private var toolsPanel: ToolsPanelView? = null
    private var voiceController: VoiceController? = null
    private var emojiPanel: EmojiPanelView? = null
    private var safeAreaView: View? = null
    private var safeAreaHeightDp = DEFAULT_SAFE_AREA_HEIGHT_DP
    private var navigationInsetDp = 0
    private var resizeConfigurationBefore = ResizeConfiguration()
    private var resizeConfigurationWorking = ResizeConfiguration()
    private var nextWordPredictionRequested = false
    private var backspaceSelection: BackspaceSelection? = null
    private var internalEditDepth = 0
    private var inputSessionKey: String? = null
    private var inputSessionBaseKey: String? = null
    private var inputSessionGeneration = 0L
    // Local history shadow used by rapid text commits; it avoids InputConnection reads per key.
    private var fastHistorySnapshot: KeyboardEditHistory.EditorTextSnapshot? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTheme()
        }
    }
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            glideTypingEnabled = keyboardSettings.glideTypingEnabled()
            keyboardView?.invalidate()
            emojiPanel?.setEmojiScale(
                KeyboardSettings.emojiScaleForLevel(keyboardSettings.emojiSizeLevel()),
            )
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
        val settingsFilter = IntentFilter(KeyboardSettings.ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, settingsFilter)
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
            onTools = ::onToolsPressed,
            onMic = ::showVoice,
            onToolbarAction = ::onToolbarAction,
            onCandidateSelected = ::onSuggestionSelected,
            onCandidateLongPressed = ::onSuggestionLongPressed,
            onOrderChanged = keyboardSettings::setToolbarOrder,
            fontScale = {
                KeyboardSettings.fontScaleForLevel(keyboardSettings.fontSizeLevel())
            },
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
                val connection = currentInputConnection
                val insertedText = fastInsertText(key)
                val fastCandidate = insertedText?.let { fastHistorySeed() }
                val useFastHistory = insertedText != null &&
                    fastCandidate != null && !fastCandidate.hasSelection
                val useSlowHistory = isTextEditKey(key) && !useFastHistory &&
                    (insertedText == null || fastCandidate?.hasSelection == true)
                if (insertedText == null && isTextEditKey(key)) fastHistorySnapshot = null
                val beforeEdit = when {
                    useFastHistory -> fastCandidate
                    useSlowHistory -> editHistory.capture(connection)
                    else -> null
                }
                val committed = withInternalEdit {
                    val result = typingEngine.handle(key, connection)
                    if (result && useFastHistory && beforeEdit != null && insertedText != null) {
                        val afterEdit = fastHistoryAfter(beforeEdit, insertedText)
                        editHistory.record(beforeEdit, afterEdit)
                        fastHistorySnapshot = afterEdit
                    } else if (result && useSlowHistory) {
                        editHistory.record(beforeEdit, editHistory.capture(connection))
                    }
                    result
                }
                if (committed) {
                    recordLearningBoundary(key)
                    if (key.type == KeyType.LANGUAGE_SWITCH) {
                        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
                        keyboardView?.invalidate()
                    }
                    // Keep the session history through all typing, then end it after Send/Enter.
                    if (key.type == KeyType.ENTER) editHistory.clear()
                }
                updateToolbarForKey(key)
                // This only schedules a bounded background read after the synchronous commit.
                // It never sits in front of the key-to-InputConnection path.
                requestCompositionSnapshot()
                committed
            },
            glideEnabled = { glideTypingEnabled },
            onGlideStarted = {
                preGestureSnapshot = latestCompositionSnapshot
                glideController.cancel()
            },
            onGlideCompleted = ::onGlideCompleted,
            onGlideCancelled = ::onGlideCancelled,
            onSpaceLongPressed = ::onSpaceLongPressed,
            onSpaceCursorStarted = ::onSpaceCursorStarted,
            onSpaceCursorMoved = ::onSpaceCursorMoved,
            onBackspaceSwipeStarted = ::onBackspaceSwipeStarted,
            onBackspaceSwipeMoved = ::onBackspaceSwipeMoved,
            onBackspaceSwipeFinished = ::onBackspaceSwipeFinished,
            onBackspaceSwipeCancelled = ::onBackspaceSwipeCancelled,
            fontScale = {
                KeyboardSettings.fontScaleForLevel(keyboardSettings.fontSizeLevel())
            },
            showLanguageSwitch = { keyboardSettings.showLanguageSwitchKey() },
        )
        keyboardView = view
        view.applyTheme(activeTheme)

        val container = FrameLayout(this)
        contentContainer = container
        val workspace = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
        }
        workspaceContainer = workspace
        rootView = FixedImeLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            setOnApplyWindowInsetsListener { _, insets ->
                val bottomInsetPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                val nextInsetDp = pxToDp(bottomInsetPx)
                if (nextInsetDp != navigationInsetDp) {
                    navigationInsetDp = nextInsetDp
                    post { updateImeHeight(activeResizeConfiguration()) }
                }
                insets
            }
            addView(toolbar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TOOLBAR_HEIGHT_DP)
            ))
            workspace.addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(keyboardSettings.keyboardHeightDp()),
            ))
            workspace.addView(View(this@KeyboardInputMethodService).apply {
                safeAreaView = this
                setBackgroundColor(activeTheme.backgroundColor)
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(safeAreaHeightDp),
            ))
            addView(workspace, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(keyboardSettings.keyboardHeightDp() + safeAreaHeightDp),
            ))
        }
        updateImeHeight(keyboardSettings.resizeConfiguration(currentOrientation()))
        setKeyboardContent()
        return rootView!!
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        refreshTheme()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        keyboardState.setEditorInfo(attribute)
        beginInputSession(attribute, restarting)
        latestCompositionSnapshot = null
        nextWordPredictionRequested = false
        clearSuggestions()
        toolbarView?.showToolbarActions()
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshTheme()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        keyboardState.setEditorInfo(info)
        ensureInputSession(info, restarting)
        latestCompositionSnapshot = null
        nextWordPredictionRequested = false
        clearSuggestions()
        toolbarView?.showToolbarActions()
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        latestCompositionSnapshot = null
        keyboardView?.invalidate()
        requestCompositionSnapshot()
    }

    private fun beginInputSession(info: EditorInfo, restarting: Boolean) {
        val baseKey = inputSessionBaseKey(info)
        if (restarting && inputSessionKey != null && inputSessionBaseKey == baseKey) return

        // A fresh token prevents same-metadata fields from sharing undo history.
        val sessionKey = baseKey + "|session-" + (++inputSessionGeneration)
        editHistory.beginSession(sessionKey)
        inputSessionKey = sessionKey
        inputSessionBaseKey = baseKey
    }

    private fun ensureInputSession(info: EditorInfo, restarting: Boolean) {
        if (inputSessionKey == null || inputSessionBaseKey != inputSessionBaseKey(info)) {
            beginInputSession(info, restarting = true)
        }
    }

    private fun inputSessionBaseKey(info: EditorInfo): String = buildString {
        append(info.packageName ?: "")
        append('|')
        append(info.fieldId)
        append('|')
        append(info.inputType)
        append('|')
        append(info.imeOptions)
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
        if (internalEditDepth == 0 && backspaceSelection == null) {
            fastHistorySnapshot?.let { shadow ->
                if (shadow.selectionStart != newSelStart || shadow.selectionEnd != newSelEnd) {
                    fastHistorySnapshot = null
                }
            }
            editHistory.onSelectionChanged(newSelStart, newSelEnd)
        }
        compositionController.updateSelection(newSelStart, newSelEnd)
        requestCompositionSnapshot()
    }

    override fun onFinishInput() {
        keyboardView?.cancelPendingInput()
        toolbarView?.cancelPendingInput()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        clearSuggestions()
        editHistory.clear()
        fastHistorySnapshot = null
        inputSessionKey = null
        inputSessionBaseKey = null
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
        editHistory.clear()
        fastHistorySnapshot = null
        inputSessionKey = null
        inputSessionBaseKey = null
        super.onWindowHidden()
    }

    override fun onDestroy() {
        keyboardView?.cancelPendingInput()
        toolbarView?.cancelPendingInput()
        keyboardView = null
        toolbarView = null
        contentContainer = null
        workspaceContainer = null
        rootView = null
        resizePanel = null
        voiceController?.close()
        voiceController = null
        editHistory.clear()
        fastHistorySnapshot = null
        inputSessionKey = null
        inputSessionBaseKey = null
        runCatching { unregisterReceiver(themeReceiver) }
        runCatching { unregisterReceiver(settingsReceiver) }
        predictionController.close()
        glideController.close()
        learningController.close()
        compositionController.close()
        super.onDestroy()
    }

    private fun requestCompositionSnapshot() {
        if (contentMode != ContentMode.KEYBOARD) return
        if (keyboardState.editorInputState.isSensitive) {
            clearSuggestions()
            toolbarView?.showToolbarActions()
            return
        }
        compositionController.requestSnapshot(
            inputConnection = currentInputConnection,
            editorInputState = keyboardState.editorInputState,
            activeLanguage = keyboardState.language,
        ) { snapshot ->
            latestCompositionSnapshot = snapshot
            if (contentMode != ContentMode.KEYBOARD) return@requestSnapshot
            if (snapshot.currentWord.isEmpty()) {
                val showNextWord = nextWordPredictionRequested && !endsSentence(snapshot.textBeforeCursor)
                if (!showNextWord) {
                    predictionController.clear()
                    toolbarView?.showToolbarActions()
                    return@requestSnapshot
                }
            }
            toolbarView?.showSuggestions()
            predictionController.request(snapshot) { result ->
                mainHandler.post {
                    val current = latestCompositionSnapshot
                    if (contentMode != ContentMode.KEYBOARD ||
                        toolbarView?.isSuggestionsMode() != true ||
                        result.requestId != predictionController.latestRequestId() ||
                        current?.generation != result.snapshot.generation
                    ) return@post
                    toolbarView?.setCandidates(result.candidates)
                }
            }
        }
    }

    private fun onSuggestionSelected(candidate: SuggestionCandidate) {
        if (contentMode != ContentMode.KEYBOARD) return
        val snapshot = latestCompositionSnapshot ?: return
        val connection = currentInputConnection
        fastHistorySnapshot = null
        val beforeEdit = editHistory.capture(connection)
        learningController.recordAcceptedSuggestion(candidate, snapshot)
        predictionController.clear()
        toolbarView?.clearCandidates()
        val result = withInternalEdit {
            SuggestionSelectionEngine.applyWithAutoSpace(connection, candidate, snapshot)
        }
        if (result.committed) {
            editHistory.record(beforeEdit, editHistory.capture(connection))
            nextWordPredictionRequested = result.nextWordReady
        }
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
                val connection = currentInputConnection
                fastHistorySnapshot = null
                val beforeEdit = editHistory.capture(connection)
                val committed = withInternalEdit {
                    GlideCommitter.commit(connection, gesture, snapshot, candidate.text)
                }
                if (committed) {
                    editHistory.record(beforeEdit, editHistory.capture(connection))
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

    private fun onSpaceLongPressed() {
        // Space is committed immediately for the normal tap path. If the gesture becomes a
        // long press, remove that one just-committed space before opening the system picker.
        val connection = currentInputConnection
        fastHistorySnapshot = null
        val beforeEdit = editHistory.capture(connection)
        withInternalEdit { connection?.deleteSurroundingText(1, 0) }
        editHistory.record(beforeEdit, editHistory.capture(connection))
        nextWordPredictionRequested = false
        requestCompositionSnapshot()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showInputMethodPicker()
    }

    private fun onSpaceCursorStarted(): Boolean {
        // The normal Space tap commits immediately. Once a horizontal swipe is confirmed,
        // remove only that Space so the editor-native cursor movement starts at the original
        // insertion point without changing surrounding text.
        val connection = currentInputConnection
        fastHistorySnapshot = null
        val beforeEdit = editHistory.capture(connection)
        val deleted = withInternalEdit { connection?.deleteSurroundingText(1, 0) == true }
        if (deleted) {
            editHistory.record(beforeEdit, editHistory.capture(connection))
            nextWordPredictionRequested = false
            requestCompositionSnapshot()
        }
        return deleted
    }

    private fun onSpaceCursorMoved(direction: Int) {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 0
        }, 0) ?: return
        val text = extracted.text?.toString() ?: return
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start < 0 || end < 0) return

        // Collapse an existing selection in the travel direction, then move by one Unicode
        // code point. The position is always clamped to the actual editor text boundaries.
        val cursor = if (start != end) {
            if (direction > 0) maxOf(start, end) else minOf(start, end)
        } else {
            start
        }
        val next = if (direction > 0) {
            if (cursor >= text.length) return
            cursor + Character.charCount(text.codePointAt(cursor))
        } else {
            if (cursor <= 0) return
            val candidate = cursor - 1
            if (candidate > 0 && Character.isLowSurrogate(text[candidate]) &&
                Character.isHighSurrogate(text[candidate - 1])
            ) candidate - 1 else candidate
        }.coerceIn(0, text.length)
        if (next != cursor) withInternalEdit { connection.setSelection(next, next) }
    }

    private fun onBackspaceSwipeStarted(): Boolean {
        fastHistorySnapshot = null
        val connection = currentInputConnection ?: return false
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false

        val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 0
        }, 0) ?: return false
        val cursor = extracted.selectionStart
        if (cursor < 0 || extracted.selectionEnd != cursor) return false
        val before = connection.getTextBeforeCursor(MAX_BACKSPACE_WORD_CONTEXT, 0)?.toString()
            ?: return false
        val absoluteBase = cursor - before.length
        backspaceSelection = BackspaceSelection(
            anchor = cursor,
            start = cursor,
            wordBoundaries = backspaceWordBoundaries(before, absoluteBase),
        )
        return true
    }

    private fun onBackspaceSwipeMoved(leftDistancePx: Float) {
        val selection = backspaceSelection ?: return
        val connection = currentInputConnection ?: return
        if (selection.wordBoundaries.isEmpty()) return
        val stepPx = BACKSPACE_SWIPE_STEP_DP * resources.displayMetrics.density
        val requestedWords = if (leftDistancePx < stepPx) 0 else {
            (leftDistancePx / stepPx).toInt().coerceAtLeast(1)
        }
        val newStart = if (requestedWords == 0) selection.anchor else {
            selection.wordBoundaries[(requestedWords - 1)
                .coerceAtMost(selection.wordBoundaries.lastIndex)]
        }
        if (newStart == selection.start) return
        val changed = withInternalEdit { connection.setSelection(newStart, selection.anchor) }
        if (changed) selection.start = newStart
    }

    private fun onBackspaceSwipeFinished() {
        fastHistorySnapshot = null
        val selection = backspaceSelection ?: return
        val connection = currentInputConnection
        fastHistorySnapshot = null
        val beforeEdit = editHistory.capture(connection)
        withInternalEdit {
            if (connection != null && selection.start < selection.anchor) {
                connection.setSelection(selection.start, selection.anchor)
                connection.commitText("", 1)
            }
        }
        editHistory.record(beforeEdit, editHistory.capture(connection))
        backspaceSelection = null
        requestCompositionSnapshot()
    }

    private fun onBackspaceSwipeCancelled() {
        val selection = backspaceSelection ?: return
        currentInputConnection?.let { connection ->
            withInternalEdit { connection.setSelection(selection.anchor, selection.anchor) }
        }
        backspaceSelection = null
        requestCompositionSnapshot()
    }

    private fun backspaceWordBoundaries(text: String, absoluteBase: Int): List<Int> {
        val boundaries = ArrayList<Int>()
        var index = text.length
        while (index > 0) {
            while (index > 0) {
                val codePoint = text.codePointBefore(index)
                if (isWordCodePoint(codePoint)) break
                index -= Character.charCount(codePoint)
            }
            if (index <= 0) break
            while (index > 0) {
                val codePoint = text.codePointBefore(index)
                if (!isWordCodePoint(codePoint)) break
                index -= Character.charCount(codePoint)
            }
            boundaries += absoluteBase + index
        }
        return boundaries
    }

    private fun previousWordStart(text: String): Int {
        var index = text.length
        while (index > 0) {
            val codePoint = text.codePointBefore(index)
            if (isWordCodePoint(codePoint)) break
            index -= Character.charCount(codePoint)
        }
        while (index > 0) {
            val codePoint = text.codePointBefore(index)
            if (!isWordCodePoint(codePoint)) break
            index -= Character.charCount(codePoint)
        }
        return index
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == '_'.code || codePoint == '\''.code ||
            codePoint == 0x200C || codePoint == 0x200D
    }

    private data class BackspaceSelection(
        val anchor: Int,
        var start: Int,
        val wordBoundaries: List<Int>,
    )

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

    private fun isTextEditKey(key: KeySpec): Boolean = when (key.type) {
        KeyType.CHARACTER, KeyType.SPACE, KeyType.BACKSPACE, KeyType.ENTER -> true
        else -> false
    }

    /** Returns text that is safe to shadow locally without reading the editor per key. */
    private fun fastInsertText(key: KeySpec): String? {
        val text = when (key.type) {
            KeyType.SPACE -> " "
            KeyType.CHARACTER -> key.textFor(keyboardState.shiftActive())
            else -> return null
        }
        if (text.isEmpty()) return null
        val codePoint = text.codePointAt(0)
        return when (codePoint) {
            '.'.code, ','.code, '?'.code, '!'.code, ':'.code, ';'.code,
            '؟'.code, '۔'.code, '،'.code, ')'.code, ']'.code, '}'.code -> null
            else -> text
        }
    }

    private fun fastHistorySeed(): KeyboardEditHistory.EditorTextSnapshot? {
        fastHistorySnapshot?.let { return it }
        val composition = latestCompositionSnapshot ?: return null
        if (composition.selectionStart < 0 || composition.selectionEnd < 0) return null
        val seed = KeyboardEditHistory.EditorTextSnapshot(
            beforeCursor = composition.textBeforeCursor,
            afterCursor = composition.textAfterCursor,
            selectedText = composition.selectedText,
            selectionStart = composition.selectionStart,
            selectionEnd = composition.selectionEnd,
        )
        fastHistorySnapshot = seed
        return seed
    }

    private fun fastHistoryAfter(
        before: KeyboardEditHistory.EditorTextSnapshot,
        insertedText: String,
    ): KeyboardEditHistory.EditorTextSnapshot {
        val nextCursor = before.selectionStart + insertedText.length
        return before.copy(
            beforeCursor = boundedHistoryContext(before.beforeCursor + insertedText),
            selectedText = null,
            selectionStart = nextCursor,
            selectionEnd = nextCursor,
        )
    }

    private fun boundedHistoryContext(text: String): String {
        if (text.length <= MAX_HISTORY_CONTEXT_UTF16) return text
        var start = text.length - MAX_HISTORY_CONTEXT_UTF16
        if (start > 0 && Character.isLowSurrogate(text[start]) &&
            Character.isHighSurrogate(text[start - 1])) {
            start++
        }
        return text.substring(start)
    }

    private fun performUndo() {
        fastHistorySnapshot = null
        val undone = withInternalEdit { editHistory.undo(currentInputConnection) }
        if (undone) {
            toolsPanel?.invalidate()
            latestCompositionSnapshot = null
        }
    }

    private fun performRedo() {
        fastHistorySnapshot = null
        val redone = withInternalEdit { editHistory.redo(currentInputConnection) }
        if (redone) {
            toolsPanel?.invalidate()
            latestCompositionSnapshot = null
        }
    }

    private fun <T> withInternalEdit(block: () -> T): T {
        internalEditDepth++
        return try {
            block()
        } finally {
            internalEditDepth--
        }
    }

    private fun endsSentence(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return false
        val codePoint = trimmed.codePointBefore(trimmed.length)
        return codePoint == '.'.code || codePoint == '!'.code || codePoint == '?'.code ||
            codePoint == '؟'.code || codePoint == '۔'.code || codePoint == '\n'.code
    }

    private fun clearSuggestions() {
        latestCompositionSnapshot = null
        nextWordPredictionRequested = false
        predictionController.clear()
        toolbarView?.clearCandidates()
    }

    private fun updateToolbarForKey(key: KeySpec) {
        if (contentMode != ContentMode.KEYBOARD) return
        when (key.type) {
            KeyType.SPACE -> {
                nextWordPredictionRequested = true
                toolbarView?.showSuggestions()
            }
            KeyType.CHARACTER -> {
                nextWordPredictionRequested = false
                if (isBoundaryText(key.outputText)) toolbarView?.showToolbarActions()
                else toolbarView?.showSuggestions()
            }
            KeyType.BACKSPACE -> {
                nextWordPredictionRequested = false
                toolbarView?.showSuggestions()
            }
            KeyType.ENTER -> {
                nextWordPredictionRequested = false
                toolbarView?.showToolbarActions()
            }
            else -> Unit
        }
    }

    private fun setKeyboardContent() {
        voiceController?.close()
        voiceController = null
        emojiPanel = null
        contentMode = ContentMode.KEYBOARD
        resizePanel?.let { workspaceContainer?.removeView(it) }
        resizePanel = null
        toolsPanel = null
        refreshTheme()
        toolbarView?.showToolbarActions()
        toolbarView?.setClosePanelMode(false)
        val container = contentContainer ?: return
        container.removeAllViews()
        val configuration = keyboardSettings.resizeConfiguration(currentOrientation())
        updateImeHeight(configuration)
        keyboardView?.let { view ->
            updateKeyboardGeometry(view, configuration)
            container.addView(view)
        }
        requestCompositionSnapshot()
    }

    private fun showTools() {
        if (contentMode == ContentMode.TOOLS) {
            setKeyboardContent()
            return
        }
        contentMode = ContentMode.TOOLS
        clearSuggestions()
        toolbarView?.showToolbarActions()
        toolbarView?.setClosePanelMode(true)
        contentContainer?.let { container ->
            container.removeAllViews()
            val tools = ToolsPanelView(
                context = this,
                onToolSelected = ::onToolSelected,
                isToolEnabled = { tool ->
                    when (tool) {
                        "undo" -> editHistory.canUndo()
                        "redo" -> editHistory.canRedo()
                        else -> true
                    }
                },
            )
            tools.applyTheme(activeTheme)
            toolsPanel = tools
            container.addView(tools, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun onToolsPressed() {
        if (contentMode == ContentMode.TOOLS || toolbarView?.isClosePanelMode() == true) {
            setKeyboardContent()
        } else {
            showTools()
        }
    }

    private fun onToolSelected(tool: String) {
        when (tool) {
            "settings" -> openSettings(focusSearch = false)
            "search" -> openSettings(focusSearch = true)
            "language" -> showLanguage()
            "resize" -> showResize()
            "themes" -> openThemeActivity()
            "emoji" -> showEmoji()
            "undo" -> performUndo()
            "redo" -> performRedo()
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
        toolbarView?.showToolbarActions()
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
                val connection = currentInputConnection
                fastHistorySnapshot = null
                val beforeEdit = editHistory.capture(connection)
                emojiUsageStore.record(emoji)
                withInternalEdit { connection?.commitText(emoji, 1) }
                editHistory.record(beforeEdit, editHistory.capture(connection))
                requestCompositionSnapshot()
            },
            onBack = ::setKeyboardContent,
        )
        emojiPanel = panel
        panel.setTheme(activeTheme)
        panel.setEmojiScale(
            KeyboardSettings.emojiScaleForLevel(keyboardSettings.emojiSizeLevel()),
        )
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
        toolbarView?.showToolbarActions()
        val container = contentContainer ?: return
        container.removeAllViews()
        var panel: VoicePanelView? = null
        var controller: VoiceController? = null
        val voicePanel = VoicePanelView(
            context = this,
            onStart = { language ->
                controller?.let { activeController ->
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else activeController.start(language)
                }
            },
            onPause = { controller?.pause() },
            onResume = { controller?.resume() },
            onStop = { controller?.stop() },
            onCancel = { controller?.cancel() },
            onTranslate = { target -> controller?.translate(target) },
            onInsert = { text ->
                val connection = currentInputConnection
                fastHistorySnapshot = null
                val beforeEdit = editHistory.capture(connection)
                withInternalEdit { connection?.commitText(text, 1) }
                editHistory.record(beforeEdit, editHistory.capture(connection))
                requestCompositionSnapshot()
            },
            onBack = ::setKeyboardContent,
        )
        panel = voicePanel
        container.addView(voicePanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        controller = VoiceController(this, onEvent = { event ->
            mainHandler.post { panel?.handle(event) }
        })
        voicePanel.applyTheme(activeTheme)
        voiceController = controller
    }

    private fun showLanguage() {
        contentMode = ContentMode.LANGUAGE
        clearSuggestions()
        toolbarView?.showToolbarActions()
        val container = contentContainer ?: return
        container.removeAllViews()
        val panel = LanguagePanelView(
            context = this,
            selectedLanguage = keyboardState.language,
            onLanguageSelected = ::selectLanguage,
            onBack = ::setKeyboardContent,
        )
        panel.applyTheme(activeTheme)
        container.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun selectLanguage(language: KeyboardLanguage) {
        keyboardState.setLanguage(language)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
        setKeyboardContent()
    }

    private fun showResize() {
        contentMode = ContentMode.RESIZE
        clearSuggestions()
        toolbarView?.showToolbarActions()
        resizeConfigurationBefore = keyboardSettings.resizeConfiguration(currentOrientation())
        resizeConfigurationWorking = resizeConfigurationBefore
        var draftConfiguration = resizeConfigurationBefore
        val container = contentContainer ?: return
        val workspace = workspaceContainer ?: return
        container.removeAllViews()
        updateImeHeight(draftConfiguration)
        keyboardView?.let { view ->
            updateKeyboardGeometry(view, draftConfiguration)
            container.addView(view)
        }

        lateinit var panel: ResizePanelView
        panel = ResizePanelView(
            context = this,
            initialConfiguration = draftConfiguration,
            onConfigurationChanged = { value ->
                draftConfiguration = value
                resizeConfigurationWorking = value
                applyResizePreview(value)
            },
            onDone = { value ->
                draftConfiguration = value.clamped()
                resizeConfigurationWorking = draftConfiguration
                keyboardSettings.setResizeConfiguration(
                    draftConfiguration,
                    currentOrientation(),
                )
                // Finish the touch dispatch before replacing the resize overlay. Rebuilding the
                // IME hierarchy synchronously from ACTION_UP can detach the input view.
                mainHandler.post {
                    if (contentMode == ContentMode.RESIZE) setKeyboardContent()
                }
            },
            onCancel = {
                // Cancel is also deferred so a BACK/ACTION_CANCEL event cannot detach the IME
                // while ResizePanelView is still dispatching it.
                mainHandler.post {
                    if (contentMode == ContentMode.RESIZE) setKeyboardContent()
                }
            },
            onReset = {
                draftConfiguration = ResizeConfiguration()
                resizeConfigurationWorking = draftConfiguration
                applyResizePreview(draftConfiguration)
                panel.setConfiguration(draftConfiguration)
            },
            visualKeyBlockHeightDp = { heightDp ->
                keyboardView?.visualKeyBlockHeightDp(heightDp)
                    ?: (heightDp - INTERNAL_BOTTOM_ROOM_DP).coerceAtLeast(1)
            },
            bottomBoundaryRangeDp = ::resizeBoundaryRangeDp,
        )
        panel.applyTheme(activeTheme)
        resizePanel = panel
        // Overlay the complete keyboard workspace, including the safe area. This prevents the
        // lower handle from being clipped at contentContainer's old bottom.
        workspace.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun applyResizePreview(configuration: ResizeConfiguration) {
        val value = configuration.clamped()
        resizeConfigurationWorking = value
        updateImeHeight(value)
        keyboardView?.let { view ->
            updateKeyboardGeometry(view, value)
        }
        resizePanel?.setConfiguration(value)
    }

    private fun updateKeyboardGeometry(
        view: KeyboardView,
        configuration: ResizeConfiguration,
    ) {
        val value = configuration.clamped()
        view.layoutParams = (view.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(value.keyboardHeightDp),
            )).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = dp(value.keyboardHeightDp)
            gravity = Gravity.TOP
            topMargin = 0
            bottomMargin = 0
            leftMargin = 0
            rightMargin = 0
        }
        view.translationY = 0f
        view.requestLayout()
        view.invalidate()
    }

    private fun updateImeHeight(keyboardHeightDp: Int) {
        updateImeHeight(ResizeConfiguration(keyboardHeightDp = keyboardHeightDp))
    }

    private fun updateImeHeight(configuration: ResizeConfiguration) {
        val value = configuration.clamped()
        updateSafeAreaHeight(value.keyboardHeightDp)
        val workspace = workspaceContainer ?: return
        val container = contentContainer ?: return
        val workspaceHeightDp = value.topOffsetDp +
            value.keyboardHeightDp +
            value.bottomSpaceDp +
            safeAreaHeightDp

        (container.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = FrameLayout.LayoutParams.MATCH_PARENT
            it.height = dp(value.topOffsetDp + value.keyboardHeightDp)
            it.topMargin = 0
            container.layoutParams = it
        }

        safeAreaView?.let { safe ->
            safe.layoutParams = (safe.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(safeAreaHeightDp),
                )).apply {
                width = FrameLayout.LayoutParams.MATCH_PARENT
                height = dp(safeAreaHeightDp)
                topMargin = dp(value.topOffsetDp + value.keyboardHeightDp + value.bottomSpaceDp)
            }
            safe.requestLayout()
        }

        workspace.layoutParams = (workspace.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(workspaceHeightDp),
        )).apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            height = dp(workspaceHeightDp)
        }
        workspace.requestLayout()

        val root = rootView ?: return
        val totalHeight = dp(TOOLBAR_HEIGHT_DP + workspaceHeightDp)
        root.setFixedHeightPx(totalHeight)
        root.layoutParams = (root.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            totalHeight,
        )).apply { height = totalHeight }
        root.requestLayout()
    }

    private fun resizeBoundaryRangeDp(configuration: ResizeConfiguration): IntRange {
        val visualBlockDp = keyboardView?.visualKeyBlockHeightDp(configuration.keyboardHeightDp)
            ?: (configuration.keyboardHeightDp - INTERNAL_BOTTOM_ROOM_DP).coerceAtLeast(1)
        val workspaceBottomDp = configuration.topOffsetDp +
            configuration.keyboardHeightDp +
            configuration.bottomSpaceDp +
            safeAreaHeightDp
        val availableDownDp = (
            workspaceBottomDp - configuration.topOffsetDp - visualBlockDp
        ).coerceAtLeast(0).coerceAtMost(
            ResizeConfiguration.MAX_BOTTOM_BOUNDARY_OFFSET_DP,
        )
        val availableUpDp = (
            visualBlockDp - MIN_RESIZE_BOUNDARY_HEIGHT_DP
        ).coerceAtLeast(0).coerceAtMost(
            -ResizeConfiguration.MIN_BOTTOM_BOUNDARY_OFFSET_DP,
        )
        return -availableUpDp..availableDownDp
    }

    private fun updateSafeAreaHeight(keyboardHeightDp: Int) {
        // Keep the total visual distance below the visible key block stable while accounting for
        // the Android navigation inset exactly once. Any unused room inside a taller resize
        // rectangle is subtracted from the custom safe area rather than added again.
        val visualBlockDp = keyboardView?.visualKeyBlockHeightDp(keyboardHeightDp)
            ?: keyboardHeightDp
        val unusedKeyboardRoomDp = (keyboardHeightDp - visualBlockDp).coerceAtLeast(0)
        safeAreaHeightDp = (
            TOTAL_BOTTOM_REGION_DP - navigationInsetDp - unusedKeyboardRoomDp
        ).coerceAtLeast(MIN_SAFE_AREA_HEIGHT_DP)
        val safe = safeAreaView ?: return
        safe.layoutParams = (safe.layoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(safeAreaHeightDp),
        )).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = dp(safeAreaHeightDp)
        }
        safe.requestLayout()
    }

    private fun refreshTheme() {
        if (!::themeStore.isInitialized) return
        activeTheme = themeStore.activeTheme()
        toolbarView?.applyTheme(activeTheme)
        keyboardView?.applyTheme(activeTheme)
        safeAreaView?.setBackgroundColor(activeTheme.backgroundColor)
    }

    private fun activeResizeConfiguration(): ResizeConfiguration =
        if (contentMode == ContentMode.RESIZE) {
            resizeConfigurationWorking
        } else {
            keyboardSettings.resizeConfiguration(currentOrientation())
        }

    private fun currentOrientation(): Int = resources.configuration.orientation

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pxToDp(value: Int): Int =
        (value / resources.displayMetrics.density).roundToInt().coerceAtLeast(0)

    private enum class ContentMode { KEYBOARD, TOOLS, RESIZE, EMOJI, VOICE, LANGUAGE }

    private companion object {
        const val TOOLBAR_HEIGHT_DP = 48
        const val DEFAULT_SAFE_AREA_HEIGHT_DP = 62
        const val TOTAL_BOTTOM_REGION_DP = 62
        const val MIN_SAFE_AREA_HEIGHT_DP = 0
        const val MIN_GLIDE_CONFIDENCE = 48
        const val MAX_BACKSPACE_WORD_CONTEXT = 512
        const val BACKSPACE_SWIPE_STEP_DP = 14f
        const val INTERNAL_BOTTOM_ROOM_DP = 55
        const val MIN_RESIZE_BOUNDARY_HEIGHT_DP = 48
    }
}