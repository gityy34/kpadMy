package com.example.aikeyboard.core

class KeyboardRow(val keys: Array<KeySpec>)

class KeyboardLayout(val rows: Array<KeyboardRow>)

interface KeyboardLayoutProvider {
    fun layoutFor(language: KeyboardLanguage, mode: KeyboardMode): KeyboardLayout

    fun layoutFor(mode: KeyboardMode): KeyboardLayout =
        layoutFor(KeyboardLanguage.ENGLISH, mode)
}

/**
 * Phase 1 layouts are fixed and allocated once. Later language packs can implement the same
 * provider without changing the input engine or IME lifecycle.
 */
class DefaultKeyboardLayoutProvider : KeyboardLayoutProvider {
    private val englishLetters = KeyboardLayout(
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

    private val urduLetters = KeyboardLayout(
        rows = arrayOf(
            row("قوعرتیاےہپ"),
            row("سڈدذزژشصضط"),
            row("ظغفکگلنںبم"),
            row("ٹثجچحخءھئؤ"),
            KeyboardRow(
                arrayOf(
                    KeySpec.action(KeyType.MODE_SWITCH, "۱۲۳", 1.5f),
                    KeySpec.punctuation("،"),
                    KeySpec.space(),
                    KeySpec.punctuation("۔"),
                    KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                ),
            ),
        ),
    )

    private val englishSymbols = symbolsLayout("ABC", ",", ".", "_\"':;!?/")
    private val urduSymbols = symbolsLayout("حروف", "،", "۔", "_\"':;!؟/")

    override fun layoutFor(language: KeyboardLanguage, mode: KeyboardMode): KeyboardLayout =
        when {
            mode == KeyboardMode.SYMBOLS && language == KeyboardLanguage.URDU -> urduSymbols
            mode == KeyboardMode.SYMBOLS -> englishSymbols
            language == KeyboardLanguage.URDU -> urduLetters
            else -> englishLetters
        }

    private fun row(text: String): KeyboardRow = KeyboardRow(keys(text))

    private fun symbolsLayout(
        modeLabel: String,
        comma: String,
        period: String,
        punctuationRow: String,
    ): KeyboardLayout = KeyboardLayout(
        rows = arrayOf(
            row("1234567890"),
            row("@#$%&*-+()"),
            row(punctuationRow),
            KeyboardRow(
                arrayOf(
                    KeySpec.action(KeyType.MODE_SWITCH, modeLabel, 1.35f),
                    KeySpec.punctuation(comma),
                    KeySpec.space(3.5f),
                    KeySpec.punctuation(period),
                    KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                    KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                ),
            ),
        ),
    )

    private fun keys(text: String): Array<KeySpec> = Array(text.length) { index ->
        val character = text[index].toString()
        if (character[0].isLetter()) {
            KeySpec.character(
                lowercase = character,
                uppercase = character.uppercase(),
                alternateTexts = secondaryCharacters(character),
            )
        } else {
            KeySpec.punctuation(character)
        }
    }

    /**
     * The mapping follows familiar mobile-keyboard punctuation placement while keeping each
     * alternate as real committed text. Urdu letters expose useful Arabic diacritics; Roman Urdu
     * reuses the English mapping through the existing Latin layout.
     */
    private fun secondaryCharacters(character: String): List<String> = when (character.lowercase()) {
        "q" -> listOf("%")
        "w" -> listOf("\\")
        "e" -> listOf("|")
        "r" -> listOf("=")
        "t" -> listOf("[")
        "y" -> listOf("]")
        "u" -> listOf("<")
        "i" -> listOf(">")
        "o" -> listOf("{")
        "p" -> listOf("}")
        "a" -> listOf("@")
        "s" -> listOf("#")
        "d" -> listOf("$")
        "f" -> listOf("-")
        "g" -> listOf("&")
        "h" -> listOf("_")
        "j" -> listOf("+")
        "k" -> listOf("(")
        "l" -> listOf(")")
        "z" -> listOf("*")
        "x" -> listOf("\"")
        "c" -> listOf("'")
        "v" -> listOf(":")
        "b" -> listOf(";")
        "n" -> listOf("!")
        "m" -> listOf("?")
        "ق", "و", "ع", "ر", "ت", "ی", "ا", "ے", "ہ", "پ",
        "س", "ڈ", "د", "ذ", "ز", "ژ", "ش", "ص", "ض", "ط",
        "ظ", "غ", "ف", "ک", "گ", "ل", "ن", "ں", "ب", "م",
        "ٹ", "ث", "ج", "چ", "ح", "خ", "ء", "ھ", "ئ", "ؤ" ->
            listOf("َ", "ِ", "ُ", "ّ", "ْ")
        else -> emptyList()
    }
}
