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
    private var rootView: FixedImeLayout? = null
    private var contentMode = ContentMode.KEYBOARD
    private var resizePanel: ResizePanelView? = null
    private var toolsPanel: ToolsPanelView? = null
    private var voiceController: VoiceController? = null
    private var safeAreaView: View? = null
    private var safeAreaHeightDp = DEFAULT_SAFE_AREA_HEIGHT_DP
    private var navigationInsetDp = 0
    private var heightBeforeResizeDp = KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP
    private var nextWordPredictionRequested = false
    private var backspaceSelection: BackspaceSelection? = null
    private var internalEditDepth = 0
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
            onTools = ::onToolsPressed,
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
                val connection = currentInputConnection
                val beforeEdit = if (isTextEditKey(key)) editHistory.capture(connection) else null
                val committed = withInternalEdit {
                    val result = typingEngine.handle(key, connection)
                    if (result) editHistory.record(beforeEdit, editHistory.capture(connection))
                    result
                }
                if (committed) {
                    recordLearningBoundary(key)
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
        )
        keyboardView = view
        view.applyTheme(activeTheme)

        val container = FrameLayout(this)
        contentContainer = container
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
                    post { updateImeHeight(keyboardSettings.keyboardHeightDp()) }
                }
                insets
            }
            addView(toolbar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TOOLBAR_HEIGHT_DP)
            ))
            addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(keyboardSettings.keyboardHeightDp())
            ))
            // The safe area is a real, fixed row. Keyboard and glide geometry never extend into it.
            addView(View(this@KeyboardInputMethodService).apply {
                safeAreaView = this
                setBackgroundColor(activeTheme.backgroundColor)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(safeAreaHeightDp)
            ))
        }
        updateImeHeight(keyboardSettings.keyboardHeightDp())
        setKeyboardContent()
        return rootView!!
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        refreshTheme()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        keyboardState.setEditorInfo(attribute)
        editHistory.clear()
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
        editHistory.clear()
        latestCompositionSnapshot = null
        nextWordPredictionRequested = false
        clearSuggestions()
        toolbarView?.showToolbarActions()
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
        if (internalEditDepth == 0) editHistory.onSelectionChanged(newSelStart, newSelEnd)
        compositionController.updateSelection(newSelStart, newSelEnd)
        requestCompositionSnapshot()
    }

    override fun onFinishInput() {
        keyboardView?.cancelPendingInput()
        toolbarView?.cancelPendingInput()
        if (contentMode != ContentMode.KEYBOARD) setKeyboardContent()
        clearSuggestions()
        editHistory.clear()
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
        editHistory.clear()
        runCatching { unregisterReceiver(themeReceiver) }
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
        if (next != cursor) connection.setSelection(next, next)
    }

    private fun onBackspaceSwipeStarted(): Boolean {
        val connection = currentInputConnection ?: return false
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false

        val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 0
        }, 0) ?: return false
        val cursor = extracted.selectionStart
        if (cursor < 0 || extracted.selectionEnd != cursor) return false

        backspaceSelection = BackspaceSelection(anchor = cursor, start = cursor)
        return true
    }

    private fun onBackspaceSwipeMoved() {
        val selection = backspaceSelection ?: return
        val connection = currentInputConnection ?: return

        // Temporarily collapse the preview selection so getTextBeforeCursor() is anchored at
        // the left edge. The selection is restored immediately after finding the prior word.
        if (!connection.setSelection(selection.start, selection.start)) return
        val before = connection.getTextBeforeCursor(MAX_BACKSPACE_WORD_CONTEXT, 0)?.toString()
            ?: return
        val relativeStart = previousWordStart(before)
        val distance = before.length - relativeStart
        if (distance <= 0) return

        val newStart = (selection.start - distance).coerceAtLeast(0)
        if (newStart >= selection.start) return
        if (connection.setSelection(newStart, selection.anchor)) {
            selection.start = newStart
        }
    }

    private fun onBackspaceSwipeFinished() {
        val selection = backspaceSelection ?: return
        val connection = currentInputConnection
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
        currentInputConnection?.setSelection(selection.anchor, selection.anchor)
        backspaceSelection = null
        requestCompositionSnapshot()
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

    private fun performUndo() {
        val undone = withInternalEdit { editHistory.undo(currentInputConnection) }
        if (undone) {
            toolsPanel?.invalidate()
            latestCompositionSnapshot = null
        }
    }

    private fun performRedo() {
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
        contentMode = ContentMode.KEYBOARD
        resizePanel = null
        toolsPanel = null
        refreshTheme()
        toolbarView?.showToolbarActions()
        toolbarView?.setClosePanelMode(false)
        val container = contentContainer ?: return
        container.removeAllViews()
        keyboardView?.let { view ->
            val heightDp = keyboardSettings.keyboardHeightDp()
            updateImeHeight(heightDp)
            view.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
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
                val beforeEdit = editHistory.capture(connection)
                emojiUsageStore.record(emoji)
                withInternalEdit { connection?.commitText(emoji, 1) }
                editHistory.record(beforeEdit, editHistory.capture(connection))
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
        heightBeforeResizeDp = keyboardSettings.keyboardHeightDp()
        var resizeHeightDp = heightBeforeResizeDp
        val container = contentContainer ?: return
        container.removeAllViews()
        keyboardView?.let { view ->
            updateKeyboardHeight(view, resizeHeightDp)
            updateImeHeight(resizeHeightDp)
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
                updateImeHeight(value)
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
                updateImeHeight(resizeHeightDp)
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

    private fun updateImeHeight(keyboardHeightDp: Int) {
        updateSafeAreaHeight(keyboardHeightDp)
        val container = contentContainer ?: return
        (container.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.height = dp(keyboardHeightDp)
            it.weight = 0f
            container.layoutParams = it
        }
        val root = rootView ?: return
        val totalHeight = dp(TOOLBAR_HEIGHT_DP + keyboardHeightDp + safeAreaHeightDp)
        root.setFixedHeightPx(totalHeight)
        root.layoutParams = (root.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            totalHeight,
        )).apply { height = totalHeight }
        root.requestLayout()
    }

    private fun updateSafeAreaHeight(keyboardHeightDp: Int) {
        // The approved reference distance is the TOTAL space from the visible bottom edge of
        // the bottom row to the end of the IME. The keyboard view can contain unused room when
        // its saved resize height is larger than the compact visual key block, so subtract that
        // room before applying the system-safe area. This prevents double-counting insets.
        val visualBlockDp = keyboardView?.visualKeyBlockHeightDp(keyboardHeightDp)
            ?: keyboardHeightDp
        val unusedKeyboardRoomDp = (keyboardHeightDp - visualBlockDp).coerceAtLeast(0)
        safeAreaHeightDp = (
            TOTAL_BOTTOM_REGION_DP - navigationInsetDp - unusedKeyboardRoomDp
        ).coerceAtLeast(MIN_SAFE_AREA_HEIGHT_DP)
        val safe = safeAreaView ?: return
        safe.layoutParams = (safe.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(safeAreaHeightDp)
        )).apply { height = dp(safeAreaHeightDp) }
        safe.requestLayout()
    }

    private fun refreshTheme() {
        if (!::themeStore.isInitialized) return
        activeTheme = themeStore.activeTheme()
        toolbarView?.applyTheme(activeTheme)
        keyboardView?.applyTheme(activeTheme)
        safeAreaView?.setBackgroundColor(activeTheme.backgroundColor)
    }

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
    }
}
