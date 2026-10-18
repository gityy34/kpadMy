package com.example.aikeyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStateTest {
    @Test
    fun shiftIsOneShotAndConsumedAfterACharacter() {
        val state = KeyboardState(KeyboardMode.LETTERS)

        state.pressShift(now = 100L)

        assertTrue(state.shiftActive())
        state.consumeShift()
        assertFalse(state.shiftActive())
    }

    @Test
    fun rapidSecondShiftEnablesCapsLockAndThirdDisablesIt() {
        val state = KeyboardState(KeyboardMode.LETTERS)

        state.pressShift(now = 100L)
        state.pressShift(now = 300L)
        assertEquals(ShiftState.LOCKED, state.shiftState)

        state.pressShift(now = 400L)
        assertEquals(ShiftState.OFF, state.shiftState)
    }

    @Test
    fun modeSwitchClearsOneShotShift() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        state.pressShift(now = 100L)

        state.toggleMode()

        assertEquals(KeyboardMode.SYMBOLS, state.mode)
        assertEquals(ShiftState.OFF, state.shiftState)
    }
}
