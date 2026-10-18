package com.example.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.example.aikeyboard.core.DefaultKeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardState
import com.example.aikeyboard.core.TypingEngine
import com.example.aikeyboard.core.CompositionController
import com.example.aikeyboard.settings.SharedPreferencesKeyboardSettings
import com.example.aikeyboard.ui.KeyboardView

/** Android IME lifecycle boundary. All text editing is delegated to the core typing engine. */
class KeyboardInputMethodService : InputMethodService() {

    private val layoutProvider = DefaultKeyboardLayoutProvider()
    private lateinit var keyboardState: KeyboardState
    private lateinit var typingEngine: TypingEngine
    private lateinit var compositionController: CompositionController
    private var keyboardView: KeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        val settings = SharedPreferencesKeyboardSettings(this)
        keyboardState = KeyboardState(settings.initialKeyboardMode())
        typingEngine = TypingEngine(keyboardState)
        compositionController = CompositionController()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(
            context = this,
            layoutProvider = layoutProvider,
            state = keyboardState,
            onKeyPressed = { key ->
                // InputConnection is read at dispatch time because editors can change without
                // recreating the IME process.
                typingEngine.handle(key, currentInputConnection)
            },
        )
        keyboardView = view
        return view
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardState.setEditorInfo(attribute)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardState.setEditorInfo(info)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        keyboardView?.invalidate()
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
        compositionController.requestSnapshot(
            inputConnection = currentInputConnection,
            editorInputState = keyboardState.editorInputState,
            activeLanguage = keyboardState.language,
        )
    }

    override fun onFinishInput() {
        keyboardView?.cancelPendingInput()
        keyboardState.setEditorOptions(0)
        compositionController.reset(keyboardState.editorInputState, keyboardState.language)
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onWindowHidden() {
        keyboardView?.cancelPendingInput()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        keyboardView?.cancelPendingInput()
        keyboardView = null
        compositionController.close()
        super.onDestroy()
    }
}
