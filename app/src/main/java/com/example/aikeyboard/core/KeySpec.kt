package com.example.aikeyboard.core

/** Stable action identifiers keep the touch path branchable without string comparisons. */
object KeyType {
    const val CHARACTER = 0
    const val SPACE = 1
    const val BACKSPACE = 2
    const val ENTER = 3
    const val SHIFT = 4
    const val MODE_SWITCH = 5
    const val LANGUAGE_SWITCH = 6
}

/**
 * Immutable description of one key. Instances are created with a layout and reused for
 * every touch; a key press never creates a new key object.
 */
class KeySpec private constructor(
    val type: Int,
    val outputText: String,
    val shiftedOutputText: String?,
    val label: String,
    val shiftedLabel: String?,
    val weight: Float,
) {
    fun textFor(shifted: Boolean): String =
        if (shifted && shiftedOutputText != null) shiftedOutputText else outputText

    fun labelFor(shifted: Boolean): String =
        if (shifted && shiftedLabel != null) shiftedLabel else label

    companion object {
        fun character(lowercase: String, uppercase: String, weight: Float = 1f): KeySpec =
            KeySpec(
                type = KeyType.CHARACTER,
                outputText = lowercase,
                shiftedOutputText = uppercase,
                label = lowercase,
                shiftedLabel = uppercase,
                weight = weight,
            )

        fun punctuation(text: String, weight: Float = 1f): KeySpec =
            KeySpec(
                type = KeyType.CHARACTER,
                outputText = text,
                shiftedOutputText = null,
                label = text,
                shiftedLabel = null,
                weight = weight,
            )

        fun action(type: Int, label: String, weight: Float = 1f): KeySpec =
            KeySpec(
                type = type,
                outputText = "",
                shiftedOutputText = null,
                label = label,
                shiftedLabel = null,
                weight = weight,
            )

        fun languageSwitch(label: String, weight: Float = 1.25f): KeySpec =
            action(KeyType.LANGUAGE_SWITCH, label, weight)

        fun space(weight: Float = 4f): KeySpec =
            KeySpec(
                type = KeyType.SPACE,
                outputText = " ",
                shiftedOutputText = null,
                label = "space",
                shiftedLabel = null,
                weight = weight,
            )
    }
}
