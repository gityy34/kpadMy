package com.example.aikeyboard.settings

import android.content.Context
import com.example.aikeyboard.core.KeyboardMode

/** Persistence boundary. The input engine never reads preferences during a key press. */
interface KeyboardSettings {
    fun initialKeyboardMode(): KeyboardMode

    fun personalizedSuggestionsEnabled(): Boolean = true

    fun setPersonalizedSuggestionsEnabled(enabled: Boolean) = Unit

    fun glideTypingEnabled(): Boolean = true

    fun setGlideTypingEnabled(enabled: Boolean) = Unit
}

class SharedPreferencesKeyboardSettings(context: Context) : KeyboardSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun initialKeyboardMode(): KeyboardMode =
        if (preferences.getBoolean(KEY_START_IN_SYMBOLS, false)) {
            KeyboardMode.SYMBOLS
        } else {
            KeyboardMode.LETTERS
        }

    override fun personalizedSuggestionsEnabled(): Boolean =
        preferences.getBoolean(KEY_PERSONALIZED_SUGGESTIONS, true)

    override fun setPersonalizedSuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PERSONALIZED_SUGGESTIONS, enabled).apply()
    }

    override fun glideTypingEnabled(): Boolean =
        preferences.getBoolean(KEY_GLIDE_TYPING, true)

    override fun setGlideTypingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_GLIDE_TYPING, enabled).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "keyboard_settings"
        private const val KEY_START_IN_SYMBOLS = "start_in_symbols"
        private const val KEY_PERSONALIZED_SUGGESTIONS = "personalized_suggestions"
        private const val KEY_GLIDE_TYPING = "glide_typing"
    }
}
