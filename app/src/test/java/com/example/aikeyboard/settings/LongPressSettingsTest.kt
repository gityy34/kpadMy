package com.example.aikeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressSettingsTest {
    @Test
    fun delayPresetsExposeTheRequired100To700Milliseconds() {
        val expected = longArrayOf(100L, 200L, 300L, 400L, 500L, 600L, 700L)
        assertEquals(expected.size, KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT)
        expected.forEachIndexed { index, value ->
            assertEquals(value, KeyboardSettings.longPressDelayForLevel(index))
        }
    }

    @Test
    fun delayLevelsClampAtBothEnds() {
        assertEquals(100L, KeyboardSettings.longPressDelayForLevel(-1))
        assertEquals(700L, KeyboardSettings.longPressDelayForLevel(99))
    }
}
