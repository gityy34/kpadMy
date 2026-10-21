package com.example.aikeyboard.core

enum class KeyboardMode {
    LETTERS,
    SYMBOLS,
}

enum class ShiftState {
    OFF,
    ONCE,
    LOCKED,
}

/** Mutable state owned by the IME. It contains no UI or persistence dependencies. */
class KeyboardState(initialMode: KeyboardMode) {
    var language: KeyboardLanguage = KeyboardLanguage.ENGLISH
        private set

    var mode: KeyboardMode = initialMode
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var imeOptions: Int = 0
        private set

    var editorInputState: EditorInputState = DEFAULT_EDITOR_INPUT_STATE
        private set

    private var lastShiftUptime: Long = Long.MIN_VALUE

    fun setEditorOptions(options: Int) {
        imeOptions = options
        editorInputState = DEFAULT_EDITOR_INPUT_STATE.copy(imeOptions = options)
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    fun setEditorInfo(info: android.view.inputmethod.EditorInfo) {
        editorInputState = EditorInputState.from(info)
        imeOptions = info.imeOptions
        mode = when (editorInputState.fieldType) {
            EditorFieldType.NUMBER,
            EditorFieldType.PHONE,
            EditorFieldType.PIN -> KeyboardMode.SYMBOLS
            else -> KeyboardMode.LETTERS
        }
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    fun shiftActive(): Boolean = shiftState != ShiftState.OFF

    fun pressShift(now: Long) {
        shiftState = when {
            shiftState == ShiftState.LOCKED -> ShiftState.OFF
            shiftState == ShiftState.ONCE && now - lastShiftUptime <= DOUBLE_TAP_TIMEOUT_MS ->
                ShiftState.LOCKED
            else -> ShiftState.ONCE
        }
        lastShiftUptime = now
    }

    fun consumeShift() {
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
        }
    }

    fun toggleMode() {
        mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    fun toggleLanguage() {
        language = when (language) {
            KeyboardLanguage.ENGLISH -> KeyboardLanguage.URDU
            KeyboardLanguage.URDU -> KeyboardLanguage.ROMAN_URDU
            KeyboardLanguage.ROMAN_URDU -> KeyboardLanguage.ENGLISH
        }
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    /** Changes language without recreating the IME or touching the current editor text. */
    fun setLanguage(value: KeyboardLanguage) {
        language = value
        mode = KeyboardMode.LETTERS
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    companion object {
        private val DEFAULT_EDITOR_INPUT_STATE = EditorInputState(
            inputType = 0,
            imeOptions = 0,
            fieldType = EditorFieldType.TEXT,
            isSensitive = false,
            noPersonalizedLearning = false,
        )
        private const val DOUBLE_TAP_TIMEOUT_MS = 350L
    }
}
