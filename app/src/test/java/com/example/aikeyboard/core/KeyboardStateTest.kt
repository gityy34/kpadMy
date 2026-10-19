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

    @Test
    fun languageSwitchIsImmediateAndClearsShiftWithoutRecreatingState() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        state.pressShift(now = 100L)

        state.toggleLanguage()

        assertEquals(KeyboardLanguage.URDU, state.language)
        assertEquals(ShiftState.OFF, state.shiftState)
        state.toggleLanguage()
        assertEquals(KeyboardLanguage.ENGLISH, state.language)
    }

    @Test
    fun explicitLanguageSelectionSupportsRomanUrduWithoutRecreatingState() {
        val state = KeyboardState(KeyboardMode.SYMBOLS)
        state.pressShift(now = 100L)

        state.setLanguage(KeyboardLanguage.ROMAN_URDU)

        assertEquals(KeyboardLanguage.ROMAN_URDU, state.language)
        assertEquals(KeyboardMode.LETTERS, state.mode)
        assertFalse(state.shiftActive())
    }
}
