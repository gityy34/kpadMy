package com.example.aikeyboard.core

enum class KeyboardMode {
    LETTERS,
    PRIMARY_SYMBOLS,
    EXTENDED_SYMBOLS,
    NUMERIC_PAD,

    /** Legacy name retained for settings/tests; it resolves to the primary symbols layout. */
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
        mode = KeyboardMode.LETTERS
        shiftState = ShiftState.OFF
        lastShiftUptime = Long.MIN_VALUE
    }

    fun setEditorInfo(info: android.view.inputmethod.EditorInfo) {
        editorInputState = EditorInputState.from(info)
        imeOptions = info.imeOptions
        mode = when (editorInputState.fieldType) {
            EditorFieldType.NUMBER,
            EditorFieldType.PHONE,
            EditorFieldType.PIN -> KeyboardMode.NUMERIC_PAD
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

    /** Legacy toggle retained for callers that only know the old two-state API. */
    fun toggleMode() {
        mode = if (mode == KeyboardMode.LETTERS) {
            KeyboardMode.SYMBOLS
        } else {
            KeyboardMode.LETTERS
        }
        resetShift()
    }

    /** Routes a visible mode key without changing the active alphabet language. */
    fun handleModeSwitch(label: String) {
        mode = when (label) {
            "?123", "۱۲۳" -> KeyboardMode.PRIMARY_SYMBOLS
            "ABC", "حروف" -> KeyboardMode.LETTERS
            "More", "More symbols", "»" -> KeyboardMode.EXTENDED_SYMBOLS
            "←", "Prev", "Previous" -> KeyboardMode.PRIMARY_SYMBOLS
            "1234" -> KeyboardMode.NUMERIC_PAD
            "Symbols", "SYM" -> KeyboardMode.PRIMARY_SYMBOLS
            else -> if (mode == KeyboardMode.LETTERS) {
                KeyboardMode.PRIMARY_SYMBOLS
            } else {
                KeyboardMode.LETTERS
            }
        }
        resetShift()
    }

    fun returnToLetters() {
        mode = KeyboardMode.LETTERS
        resetShift()
    }

    fun returnToPrimarySymbols() {
        mode = KeyboardMode.PRIMARY_SYMBOLS
        resetShift()
    }

    fun toggleLanguage() {
        language = when (language) {
            KeyboardLanguage.ENGLISH -> KeyboardLanguage.URDU
            KeyboardLanguage.URDU -> KeyboardLanguage.ROMAN_URDU
            KeyboardLanguage.ROMAN_URDU -> KeyboardLanguage.ENGLISH
        }
        mode = KeyboardMode.LETTERS
        resetShift()
    }

    /** Changes language without recreating the IME or touching the current editor text. */
    fun setLanguage(value: KeyboardLanguage) {
        language = value
        mode = KeyboardMode.LETTERS
        resetShift()
    }

    private fun resetShift() {
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
