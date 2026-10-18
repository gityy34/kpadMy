package com.example.aikeyboard.ui

import com.example.aikeyboard.core.KeySpec

/**
 * Allocation-free ownership for one active keyboard gesture.
 *
 * Android can deliver additional pointers while a key is held. The first pointer owns the
 * gesture until it is released or cancelled; extra pointers are consumed without dispatching
 * another key. Keeping this state separate makes the touch policy deterministic and testable.
 */
internal class TouchTracker {
    var activePointerId: Int = INVALID_POINTER_ID
        private set

    var pressedKey: KeySpec? = null
        private set

    fun start(pointerId: Int, key: KeySpec?) {
        activePointerId = pointerId
        pressedKey = key
    }

    fun owns(pointerId: Int): Boolean =
        pointerId != INVALID_POINTER_ID && activePointerId == pointerId

    fun cancel() {
        activePointerId = INVALID_POINTER_ID
        pressedKey = null
    }

    companion object {
        const val INVALID_POINTER_ID = -1
    }
}
