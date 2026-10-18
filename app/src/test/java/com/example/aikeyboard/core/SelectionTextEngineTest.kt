package com.example.aikeyboard.core

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTextEngineTest {
    @Test
    fun capitalizationReplacesOnlySelectedText() {
        var replacement = ""
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getSelectedText" -> "hello world"
                "commitText" -> {
                    replacement = args?.get(0)?.toString().orEmpty()
                    true
                }
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            }
        } as InputConnection

        assertTrue(SelectionTextEngine().capitalizeSelection(connection, SelectionCapitalization.TITLE_CASE))
        assertEquals("Hello World", replacement)
    }
}
