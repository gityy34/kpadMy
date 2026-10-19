package com.example.aikeyboard.core

import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingEngineTest {
    @Test
    fun rapidMixedInputIsCommittedInOrderWithoutInternalFiltering() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        val engine = TypingEngine(state)
        val events = mutableListOf<String>()
        val connection = recordingConnection(events)
        val letters = DefaultKeyboardLayoutProvider().layoutFor(KeyboardMode.LETTERS).rows[0].keys

        repeat(20) {
            for (key in letters) {
                assertTrue(engine.handle(key, connection))
            }
            assertTrue(engine.handle(KeySpec.space(), connection))
        }

        val expected = buildString {
            repeat(20) {
                append("qwertyuiop")
                append(' ')
            }
        }
        assertEquals(expected, events.joinToString(separator = ""))
        assertEquals(expected.length, events.size)
    }

    @Test
    fun oneShotShiftAffectsOnlyTheNextCommittedCharacter() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        val engine = TypingEngine(state)
        val events = mutableListOf<String>()
        val connection = recordingConnection(events)
        val key = KeySpec.character("a", "A")

        state.pressShift(now = 100L)
        assertTrue(engine.handle(key, connection))
        assertTrue(engine.handle(key, connection))

        assertEquals(listOf("A", "a"), events)
    }

    @Test
    fun languageSwitchDoesNotTouchTheInputConnection() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        val engine = TypingEngine(state)

        assertTrue(engine.handle(KeySpec.languageSwitch("اردو"), null))

        assertEquals(KeyboardLanguage.URDU, state.language)
    }

    @Test
    fun failedActionInSingleLineFieldDoesNotCommitNewline() {
        val state = KeyboardState(KeyboardMode.LETTERS)
        state.setEditorInfo(EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
        })
        val engine = TypingEngine(state)
        val calls = mutableListOf<String>()
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "performEditorAction" -> false
                "sendKeyEvent" -> {
                    calls += "key:${(args?.get(0) as android.view.KeyEvent).action}"
                    true
                }
                "commitText" -> {
                    calls += "commit:${args?.get(0)}"
                    true
                }
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            }
        } as InputConnection

        assertTrue(engine.handle(KeySpec.action(KeyType.ENTER, "enter"), connection))
        assertEquals(2, calls.count { it.startsWith("key:") })
        assertTrue(calls.none { it.startsWith("commit:") })
    }

    private fun recordingConnection(events: MutableList<String>): InputConnection =
        Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            if (method.name == "commitText") {
                events += args?.get(0)?.toString().orEmpty()
            }
            if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        } as InputConnection
}
