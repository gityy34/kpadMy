package com.example.aikeyboard.core

class KeyboardRow(val keys: Array<KeySpec>)

class KeyboardLayout(val rows: Array<KeyboardRow>)

interface KeyboardLayoutProvider {
    fun layoutFor(mode: KeyboardMode): KeyboardLayout
}

/**
 * Phase 1 layouts are fixed and allocated once. Later language packs can implement the same
 * provider without changing the input engine or IME lifecycle.
 */
class DefaultKeyboardLayoutProvider : KeyboardLayoutProvider {
    private val letters = KeyboardLayout(
        rows = arrayOf(
            row("qwertyuiop"),
            row("asdfghjkl"),
            KeyboardRow(
                arrayOf(
                    KeySpec.action(KeyType.SHIFT, "⇧", 1.5f),
                    *keys("zxcvbnm"),
                    KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                ),
            ),
            KeyboardRow(
                arrayOf(
                    KeySpec.action(KeyType.MODE_SWITCH, "?123", 1.5f),
                    KeySpec.punctuation(","),
                    KeySpec.space(),
                    KeySpec.punctuation("."),
                    KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                ),
            ),
        ),
    )

    private val symbols = KeyboardLayout(
        rows = arrayOf(
            row("1234567890"),
            row("@#$%&*-+()"),
            row("_\"':;!?/"),
            KeyboardRow(
                arrayOf(
                    KeySpec.action(KeyType.MODE_SWITCH, "ABC", 1.5f),
                    KeySpec.punctuation(","),
                    KeySpec.space(),
                    KeySpec.punctuation("."),
                    KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                    KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                ),
            ),
        ),
    )

    override fun layoutFor(mode: KeyboardMode): KeyboardLayout =
        if (mode == KeyboardMode.LETTERS) letters else symbols

    private fun row(text: String): KeyboardRow = KeyboardRow(keys(text))

    private fun keys(text: String): Array<KeySpec> = Array(text.length) { index ->
        val character = text[index].toString()
        if (character[0].isLetter()) {
            KeySpec.character(character, character.uppercase())
        } else {
            KeySpec.punctuation(character)
        }
    }
}
