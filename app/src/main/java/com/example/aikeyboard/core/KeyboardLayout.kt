package com.example.aikeyboard.core

class KeyboardRow(val keys: Array<KeySpec>)

class KeyboardLayout(val rows: Array<KeyboardRow>)

interface KeyboardLayoutProvider {
    fun layoutFor(language: KeyboardLanguage, mode: KeyboardMode): KeyboardLayout

    fun layoutFor(mode: KeyboardMode): KeyboardLayout =
        layoutFor(KeyboardLanguage.ENGLISH, mode)
}

/**
 * Stable layouts for the alphabet, primary symbols, extended symbols, and numeric keypad.
 * The IME keeps one active KeyboardState; these layouts only describe the current mode.
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
                    KeySpec.punctuation(",", alternateTexts = listOf("🙂")),
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
                    KeySpec.action(KeyType.MODE_SWITCH, "?123", 1.5f),
                    KeySpec.punctuation("،", alternateTexts = listOf("🙂")),
                    KeySpec.space(),
                    KeySpec.punctuation("۔"),
                    KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                ),
            ),
        ),
    )

    private val englishPrimarySymbols = primarySymbols(",", ".")
    private val urduPrimarySymbols = primarySymbols("،", "۔")
    private val extendedSymbols = extendedSymbols()
    private val numericPad = numericPad()

    override fun layoutFor(language: KeyboardLanguage, mode: KeyboardMode): KeyboardLayout =
        when (mode) {
            KeyboardMode.LETTERS -> if (language == KeyboardLanguage.URDU) urduLetters else englishLetters
            KeyboardMode.PRIMARY_SYMBOLS,
            KeyboardMode.SYMBOLS -> if (language == KeyboardLanguage.URDU) urduPrimarySymbols else englishPrimarySymbols
            KeyboardMode.EXTENDED_SYMBOLS -> extendedSymbols
            KeyboardMode.NUMERIC_PAD -> numericPad
        }

    private fun row(text: String): KeyboardRow = KeyboardRow(keys(text))

    private fun symbolRow(symbols: List<String>): KeyboardRow =
        KeyboardRow(symbols.map { KeySpec.punctuation(it) }.toTypedArray())

    private fun primarySymbols(comma: String, period: String): KeyboardLayout =
        KeyboardLayout(
            rows = arrayOf(
                symbolRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")),
                symbolRow(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")),
                KeyboardRow(
                    arrayOf(
                        KeySpec.action(KeyType.MODE_SWITCH, "=<\\", 1.15f),
                        KeySpec.punctuation("*"),
                        KeySpec.punctuation("\""),
                        KeySpec.punctuation("'"),
                        KeySpec.punctuation(":"),
                        KeySpec.punctuation(";"),
                        KeySpec.punctuation("!"),
                        KeySpec.punctuation("?"),
                        KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                    ),
                ),
                KeyboardRow(
                    arrayOf(
                        KeySpec.action(KeyType.MODE_SWITCH, "ABC", 1.25f),
                        KeySpec.punctuation(comma),
                        KeySpec.action(KeyType.MODE_SWITCH, "1234", 1.15f),
                        KeySpec.space(3.5f),
                        KeySpec.punctuation(period),
                        KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                    ),
                ),
            ),
        )

    private fun extendedSymbols(): KeyboardLayout =
        KeyboardLayout(
            rows = arrayOf(
                symbolRow(listOf("~", "\u0060", "|", "•", "√", "π", "÷", "×", "§", "∆")),
                symbolRow(listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")),
                KeyboardRow(
                    arrayOf(
                        KeySpec.action(KeyType.MODE_SWITCH, "?123", 1.15f),
                        KeySpec.punctuation("%"),
                        KeySpec.punctuation("©"),
                        KeySpec.punctuation("®"),
                        KeySpec.punctuation("™"),
                        KeySpec.punctuation("✓"),
                        KeySpec.punctuation("["),
                        KeySpec.punctuation("]"),
                        KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                    ),
                ),
                KeyboardRow(
                    arrayOf(
                        KeySpec.action(KeyType.MODE_SWITCH, "ABC", 1.25f),
                        KeySpec.punctuation("<"),
                        KeySpec.action(KeyType.MODE_SWITCH, "1234", 1.15f),
                        KeySpec.space(3.5f),
                        KeySpec.punctuation(">"),
                        KeySpec.action(KeyType.ENTER, "↵", 1.5f),
                    ),
                ),
            ),
        )

    private fun numericOperatorRow(
        operator: String,
        digits: String,
        utility: String,
    ): KeyboardRow = KeyboardRow(
        arrayOf(
            KeySpec.punctuation(operator, 0.8f),
            *digits.map { KeySpec.punctuation(it.toString(), 1.15f) }.toTypedArray(),
            KeySpec.punctuation(utility, 0.8f),
        ),
    )

    private fun numericPad(): KeyboardLayout =
        KeyboardLayout(
            rows = arrayOf(
                numericOperatorRow("+", "123", "%"),
                numericOperatorRow("-", "456", "="),
                KeyboardRow(
                    arrayOf(
                        KeySpec.punctuation("*", 0.8f),
                        KeySpec.punctuation("7", 1.15f),
                        KeySpec.punctuation("8", 1.15f),
                        KeySpec.punctuation("9", 1.15f),
                        KeySpec.action(KeyType.BACKSPACE, "⌫", 1.5f),
                    ),
                ),
                KeyboardRow(
                    arrayOf(
                        KeySpec.punctuation("/", 0.8f),
                        KeySpec.action(KeyType.MODE_SWITCH, "ABC", 1.25f),
                        KeySpec.punctuation(","),
                        KeySpec.action(KeyType.MODE_SWITCH, "!?#", 1.15f),
                        KeySpec.punctuation("0"),
                        KeySpec.punctuation("="),
                        KeySpec.punctuation("."),
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
     * Familiar alternate marks are kept in the existing long-press path. Urdu letters expose
     * Arabic diacritics, while Roman Urdu reuses the English mapping through the Latin layout.
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
