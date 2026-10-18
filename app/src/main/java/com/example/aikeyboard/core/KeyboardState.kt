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
    var mode: KeyboardMode = initialMode
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var imeOptions: Int = 0
        private set

    private var lastShiftUptime: Long = Long.MIN_VALUE

    fun setEditorOptions(options: Int) {
        imeOptions = options
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

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 350L
    }
}
