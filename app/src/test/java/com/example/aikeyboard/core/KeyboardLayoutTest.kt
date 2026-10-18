package com.example.aikeyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    private val provider = DefaultKeyboardLayoutProvider()

    @Test
    fun lettersLayoutContainsAllCoreActionKeys() {
        val keys = provider.layoutFor(KeyboardMode.LETTERS).rows.flatMap { it.keys.asIterable() }
        val types = keys.map { it.type }

        assertTrue(types.contains(KeyType.SHIFT))
        assertTrue(types.contains(KeyType.BACKSPACE))
        assertTrue(types.contains(KeyType.SPACE))
        assertTrue(types.contains(KeyType.ENTER))
    }

    @Test
    fun layoutsReuseStableKeyObjectsAcrossLookups() {
        val first = provider.layoutFor(KeyboardMode.LETTERS)
        val second = provider.layoutFor(KeyboardMode.LETTERS)

        assertTrue(first === second)
        assertTrue(first.rows[0].keys[0] === second.rows[0].keys[0])
        assertEquals("q", first.rows[0].keys[0].textFor(shifted = false))
        assertEquals("Q", first.rows[0].keys[0].textFor(shifted = true))
    }
}
