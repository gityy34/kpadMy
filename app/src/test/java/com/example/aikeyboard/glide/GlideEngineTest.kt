package com.example.aikeyboard.glide

import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.learning.PersonalWord
import com.example.aikeyboard.learning.PersonalVocabularySource
import com.example.aikeyboard.prediction.PredictionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideEngineTest {
    @Test
    fun englishPathRecognizesHelloUsingLiveGeometry() {
        val geometry = geometryFor("helo")
        val path = listOf(0, 1, 2, 2, 3).map { index ->
            val item = geometry[index]
            GlidePoint(item.centerX, item.centerY)
        }
        val result = GlideEngine().recognize(
            GlideGesture(path, KeyboardLanguage.ENGLISH, "h"),
            geometry,
        )

        assertTrue(
            "candidates=${result.candidates}",
            result.candidates.any { it.text == "hello" },
        )
        assertTrue("confidence=${result.confidence}", result.confidence >= 60)
    }

    @Test
    fun personalWordsParticipateWithoutReplacingDictionaryMatching() {
        val personal = PersonalVocabularySource {
            listOf(PersonalWord("android", "AndroidProject", PredictionLanguage.ENGLISH, 20, 8, 1L, System.currentTimeMillis()))
        }
        val geometry = geometryFor("androidproject")
        val path = geometry.map { GlidePoint(it.centerX, it.centerY) }
        val result = GlideEngine(personalVocabulary = personal).recognize(
            GlideGesture(path, KeyboardLanguage.ENGLISH, "a"),
            geometry,
        )
        assertTrue(result.candidates.any { it.text == "AndroidProject" && it.isPersonal })
    }

    @Test
    fun pathBufferRemainsBounded() {
        val buffer = GlidePathBuffer(8)
        repeat(100) { buffer.add(it.toFloat(), it.toFloat()) }
        assertEquals(8, buffer.size)
        assertEquals(99f, buffer.pointAt(7).x)
    }

    private fun geometryFor(keys: String): List<GlideKeyGeometry> = keys.mapIndexed { index, character ->
        GlideKeyGeometry(
            key = KeySpec.character(character.toString(), character.uppercase()),
            left = index * 40f,
            top = 0f,
            right = index * 40f + 40f,
            bottom = 60f,
        )
    }
}
