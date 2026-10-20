package com.example.aikeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionDisplayTest {
    @Test
    fun keepsWordsUpToEightGraphemesComplete() {
        listOf("h", "how", "banana", "computer").forEach { word ->
            assertEquals(word, SuggestionDisplay.displayText(word))
        }
    }

    @Test
    fun shortensNineOrMoreGraphemesToThreeDotThree() {
        assertEquals("Pom..ate", SuggestionDisplay.displayText("Pomegranate"))
        assertEquals("Wat..lon", SuggestionDisplay.displayText("Watermelon"))
        assertEquals("Int..nal", SuggestionDisplay.displayText("International"))
        assertEquals("Dev..ent", SuggestionDisplay.displayText("Development"))
    }

    @Test
    fun preservesFullTextForEachIndependentCandidateRule() {
        val words = listOf("Pomegranate", "Watermelon", "Strawberry")
        val displayed = words.map(SuggestionDisplay::displayText)
        assertEquals(listOf("Pom..ate", "Wat..lon", "Str..rry"), displayed)
        assertEquals("Pomegranate", words[0])
        assertEquals("123..789", SuggestionDisplay.displayText("123456789"))
    }
}
