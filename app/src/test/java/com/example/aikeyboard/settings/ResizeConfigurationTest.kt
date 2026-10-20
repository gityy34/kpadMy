package com.example.aikeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeConfigurationTest {
    @Test
    fun configurationClampsToSafeResizeLimits() {
        val value = ResizeConfiguration(
            keyboardHeightDp = Int.MAX_VALUE,
            topOffsetDp = Int.MAX_VALUE,
            bottomSpaceDp = Int.MAX_VALUE,
            bottomBoundaryOffsetDp = Int.MAX_VALUE,
        ).clamped()

        assertEquals(ResizeConfiguration.MAX_KEYBOARD_HEIGHT_DP, value.keyboardHeightDp)
        assertEquals(ResizeConfiguration.MAX_MOVE_OFFSET_DP, value.topOffsetDp)
        assertEquals(ResizeConfiguration.MAX_BOTTOM_SPACE_DP, value.bottomSpaceDp)
        assertEquals(
            ResizeConfiguration.MAX_BOTTOM_BOUNDARY_OFFSET_DP,
            value.bottomBoundaryOffsetDp,
        )
    }

    @Test
    fun topAndBottomGeometryRemainIndependent() {
        val value = ResizeConfiguration(
            keyboardHeightDp = 300,
            topOffsetDp = 24,
            bottomSpaceDp = 48,
        ).clamped()

        assertEquals(300, value.keyboardHeightDp)
        assertEquals(24, value.topOffsetDp)
        assertEquals(48, value.bottomSpaceDp)
    }

    @Test
    fun legacyDefaultKeepsApprovedKeyboardHeight() {
        assertEquals(
            KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP,
            ResizeConfiguration().keyboardHeightDp,
        )
    }

    @Test
    fun bottomBoundaryIsIndependentFromKeyboardHeightAndSafeSpace() {
        val value = ResizeConfiguration(
            keyboardHeightDp = 300,
            bottomSpaceDp = 48,
            bottomBoundaryOffsetDp = 36,
        ).clamped()

        assertEquals(300, value.keyboardHeightDp)
        assertEquals(48, value.bottomSpaceDp)
        assertEquals(36, value.bottomBoundaryOffsetDp)
    }

}
