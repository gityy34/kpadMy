package com.example.aikeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import com.example.aikeyboard.R
import com.example.aikeyboard.learning.PersonalVocabularyStore

/** Settings boundary. Search uses a small in-memory index and never touches the input hot path. */
class SettingsActivity : Activity() {
    private lateinit var settings: SharedPreferencesKeyboardSettings
    private lateinit var resultContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SharedPreferencesKeyboardSettings(this)
        val focusSearch = intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            isFocusableInTouchMode = true
        }
        val title = TextView(this).apply {
            text = getString(R.string.personal_learning_title)
            textSize = 22f
            setPadding(24, 24, 24, 12)
        }
        val search = EditText(this).apply {
            hint = getString(R.string.search_settings)
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(24, 8, 24, 8)
        }
        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 16, 8)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSearchResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val toggle = Switch(this).apply {
            text = getString(R.string.personalized_suggestions)
            isChecked = settings.personalizedSuggestionsEnabled()
            setPadding(24, 12, 24, 12)
            setOnCheckedChangeListener { _, enabled ->
                settings.setPersonalizedSuggestionsEnabled(enabled)
            }
        }
        val glideToggle = Switch(this).apply {
            text = getString(R.string.glide_typing)
            isChecked = settings.glideTypingEnabled()
            setPadding(24, 12, 24, 12)
            setOnCheckedChangeListener { _, enabled -> settings.setGlideTypingEnabled(enabled) }
        }
        val resetToolbar = Button(this).apply {
            text = getString(R.string.reset_toolbar_layout)
            setOnClickListener {
                settings.setToolbarOrder(KeyboardSettings.DEFAULT_TOOLBAR_ORDER)
                Toast.makeText(this@SettingsActivity, R.string.toolbar_layout_reset, Toast.LENGTH_SHORT).show()
            }
        }
        val clearButton = Button(this).apply {
            text = getString(R.string.clear_personal_data)
            setOnClickListener { confirmClear() }
        }

        root.addView(title)
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(resultContainer)
        root.addView(toggle)
        root.addView(glideToggle)
        root.addView(resetToolbar)
        root.addView(clearButton)
        setContentView(ScrollView(this).apply { addView(root) })

        if (focusSearch) {
            search.requestFocus()
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        } else {
            root.requestFocus()
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    private fun renderSearchResults(query: String) {
        resultContainer.removeAllViews()
        if (query.isBlank()) return
        val normalized = query.trim().lowercase()
        SETTINGS_INDEX.filter { it.title.lowercase().contains(normalized) || it.path.lowercase().contains(normalized) }
            .distinctBy { it.title }
            .forEach { entry ->
                resultContainer.addView(Button(this).apply {
                    text = "${entry.title}\n${entry.path}"
                    setOnClickListener {
                        Toast.makeText(this@SettingsActivity, entry.title, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        if (resultContainer.childCount == 0) {
            resultContainer.addView(TextView(this).apply { text = getString(R.string.no_settings_found) })
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_personal_data))
            .setMessage(getString(R.string.clear_personal_data_confirmation))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> PersonalVocabularyStore.clearPersisted(this) }
            .show()
    }

    private data class SettingsIndexEntry(val title: String, val path: String)

    companion object {
        const val EXTRA_FOCUS_SEARCH = "focus_settings_search"
        private val SETTINGS_INDEX = listOf(
            SettingsIndexEntry("Personalized suggestions", "Typing > Personalization"),
            SettingsIndexEntry("Clear learned words / personal typing data", "Typing > Personalization"),
            SettingsIndexEntry("Glide typing", "Typing > Gestures"),
            SettingsIndexEntry("Resize Keyboard", "Keyboard > Layout > Resize"),
            SettingsIndexEntry("Toolbar Layout", "Keyboard > Toolbar"),
            SettingsIndexEntry("Keyboard Height", "Keyboard > Layout > Resize"),
            SettingsIndexEntry("Language", "Keyboard > Languages"),
            SettingsIndexEntry("Prediction", "Typing > Suggestions"),
            SettingsIndexEntry("Accessibility", "Keyboard > Accessibility"),
        )
    }
}
