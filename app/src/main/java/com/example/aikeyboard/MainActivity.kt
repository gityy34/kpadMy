package com.example.aikeyboard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Small launcher screen; all typing work is owned by the IME service. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = getString(R.string.keyboard_setup_message)
            textSize = 18f
            setTextColor(0xFF202124.toInt())
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        setContentView(message)
    }
}
