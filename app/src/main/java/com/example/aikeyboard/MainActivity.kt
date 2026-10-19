package com.example.aikeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.aikeyboard.settings.SettingsActivity

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
        val settingsButton = Button(this).apply {
            text = getString(R.string.open_keyboard_settings)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(message)
            addView(settingsButton)
        })
    }
}
