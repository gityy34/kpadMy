package com.example.aikeyboard.prediction

import java.util.Locale

/** Generic normalization and maintainable spelling families for Roman Urdu. */
object RomanUrdu {
    private val families = listOf(
        setOf("kya", "kia", "kyaa"),
        setOf("hai", "hy", "he"),
        setOf("mein", "main", "me"),
        setOf("kaise", "kese"),
        setOf("mujhe", "mjhy"),
        setOf("acha", "accha"),
        setOf("nahi", "nai", "nhi"),
        setOf("karna", "krna"),
        setOf("theek", "thik"),
    )

    private val canonicalByVariant = buildMap {
        families.forEach { family ->
            val canonical = family.first()
            family.forEach { put(it, canonical) }
        }
    }

    fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '\'' }
        canonicalByVariant[lower]?.let { return it }
        return lower
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("ai", "e")
            .replace("ou", "u")
            .collapseRepeatedConsonants()
    }

    fun skeleton(value: String): String = normalize(value)
        .filterNot { it in "aeiou" }
        .replace('y', 'i')

    private fun String.collapseRepeatedConsonants(): String {
        val output = StringBuilder(length)
        var previous = '\u0000'
        for (character in this) {
            if (character == previous && character !in "aeiou") continue
            output.append(character)
            previous = character
        }
        return output.toString()
    }
}
