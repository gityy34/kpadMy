package com.example.aikeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeSettingsTest {
    @Test
    fun fontScaleHasSevenExactLevelsWith100PercentAtCenter() {
        val expected = floatArrayOf(0.70f, 0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f)

        assertEquals(expected.size, KeyboardSettings.SIZE_LEVEL_COUNT)
        expected.forEachIndexed { index, scale ->
            assertEquals(scale, KeyboardSettings.fontScaleForLevel(index), 0.0001f)
        }
        assertEquals(1.00f, KeyboardSettings.fontScaleForLevel(3), 0.0001f)
    }

    @Test
    fun fontScaleClampsInvalidLevelsSafely() {
        assertEquals(0.70f, KeyboardSettings.fontScaleForLevel(-1), 0.0001f)
        assertEquals(1.30f, KeyboardSettings.fontScaleForLevel(99), 0.0001f)
    }
}
