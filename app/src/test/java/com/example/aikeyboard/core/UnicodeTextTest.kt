package com.example.aikeyboard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UnicodeTextTest {
    @Test
    fun urduCombiningMarkStaysWithItsBaseCharacter() {
        val text = "ب\u0654"

        val span = UnicodeText.previousGrapheme(text)

        assertEquals(0, span.startUtf16)
        assertEquals(2, span.utf16Length)
        assertEquals(2, span.codePointCount)
    }

    @Test
    fun surrogatePairAndJoinedEmojiAreDeletedAsOneGrapheme() {
        val emoji = "👩‍💻"
        val span = UnicodeText.previousGrapheme(emoji)

        assertEquals(0, span.startUtf16)
        assertEquals(emoji.length, span.utf16Length)
        assertEquals(3, span.codePointCount)
    }

    @Test
    fun regionalIndicatorFlagRemainsTogether() {
        val flag = "🇵🇰"

        val span = UnicodeText.previousGrapheme(flag)

        assertEquals(flag.length, span.utf16Length)
        assertEquals(2, span.codePointCount)
    }
}
