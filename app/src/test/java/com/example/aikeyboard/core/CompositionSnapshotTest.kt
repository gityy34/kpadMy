package com.example.aikeyboard.core

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionSnapshotTest {
    @Test
    fun snapshotExposesWordContextSelectionAndLanguage() {
        val controller = CompositionController()
        val completed = CountDownLatch(1)
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTextBeforeCursor" -> "Hello کیا"
                "getTextAfterCursor" -> " حال ہے"
                "getSelectedText" -> null
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            }
        } as InputConnection

        val editorState = EditorInputState.from(0, 0)
        controller.reset(editorState, KeyboardLanguage.URDU)
        controller.updateSelection(8, 8)
        controller.requestSnapshot(connection, editorState, KeyboardLanguage.URDU) {
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        val snapshot = controller.snapshot()
        assertEquals("کیا", snapshot.currentWord)
        assertEquals(" حال ہے", snapshot.textAfterCursor)
        assertEquals(KeyboardLanguage.URDU, snapshot.activeLanguage)
        assertEquals(8, snapshot.selectionStart)
        controller.close()
    }
}
