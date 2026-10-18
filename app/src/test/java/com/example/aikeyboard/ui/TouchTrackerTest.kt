package com.example.aikeyboard.ui

import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchTrackerTest {
    @Test
    fun onlyTheActivePointerOwnsTheGesture() {
        val tracker = TouchTracker()
        val key = KeySpec.character("a", "A")

        tracker.start(pointerId = 7, key = key)

        assertTrue(tracker.owns(7))
        assertFalse(tracker.owns(8))
        assertSame(key, tracker.pressedKey)
    }

    @Test
    fun cancellingReleasesPointerAndKeyTogether() {
        val tracker = TouchTracker()
        tracker.start(pointerId = 2, key = KeySpec.action(KeyType.BACKSPACE, "⌫"))

        tracker.cancel()

        assertFalse(tracker.owns(2))
        assertFalse(tracker.owns(TouchTracker.INVALID_POINTER_ID))
        assertTrue(tracker.pressedKey == null)
    }
}
