package com.example.aikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.example.aikeyboard.core.DefaultKeyboardLayoutProvider
import com.example.aikeyboard.core.KeyboardState
import com.example.aikeyboard.core.TypingEngine
import com.example.aikeyboard.settings.SharedPreferencesKeyboardSettings
import com.example.aikeyboard.ui.KeyboardView

/** Android IME lifecycle boundary. All text editing is delegated to the core typing engine. */
class KeyboardInputMethodService : InputMethodService() {

    private val layoutProvider = DefaultKeyboardLayoutProvider()
    private lateinit var keyboardState: KeyboardState
    private lateinit var typingEngine: TypingEngine
    private var keyboardView: KeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        val settings = SharedPreferencesKeyboardSettings(this)
        keyboardState = KeyboardState(settings.initialKeyboardMode())
        typingEngine = TypingEngine(keyboardState)
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
                keyboardView?.invalidate()
            },
        )
        keyboardView = view
        return view
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardState.setEditorOptions(attribute.imeOptions)
        keyboardView?.invalidate()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardState.setEditorOptions(info.imeOptions)
        keyboardView?.invalidate()
    }

    override fun onFinishInput() {
        keyboardView?.cancelPendingInput()
        keyboardState.setEditorOptions(0)
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
        super.onDestroy()
    }
}
