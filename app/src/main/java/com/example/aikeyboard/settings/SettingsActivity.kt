package com.example.aikeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.example.aikeyboard.R
import com.example.aikeyboard.learning.PersonalVocabularyStore

/** Small settings boundary for Phase 4 privacy controls. */
class SettingsActivity : Activity() {
    private lateinit var settings: SharedPreferencesKeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SharedPreferencesKeyboardSettings(this)

        val title = TextView(this).apply {
            text = getString(R.string.personal_learning_title)
            textSize = 22f
            setPadding(24, 24, 24, 12)
        }
        val toggle = Switch(this).apply {
            text = getString(R.string.personalized_suggestions)
            isChecked = settings.personalizedSuggestionsEnabled()
            setPadding(24, 12, 24, 12)
            setOnCheckedChangeListener { _, enabled ->
                settings.setPersonalizedSuggestionsEnabled(enabled)
            }
        }
        val clearButton = Button(this).apply {
            text = getString(R.string.clear_personal_data)
            setOnClickListener { confirmClear() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            addView(title)
            addView(toggle)
            addView(clearButton)
        })
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_personal_data))
            .setMessage(getString(R.string.clear_personal_data_confirmation))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PersonalVocabularyStore.clearPersisted(this)
            }
            .show()
    }
}
