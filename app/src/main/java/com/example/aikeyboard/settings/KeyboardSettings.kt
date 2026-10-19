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

    fun keyboardHeightDp(): Int = DEFAULT_KEYBOARD_HEIGHT_DP

    fun setKeyboardHeightDp(heightDp: Int) = Unit

    fun toolbarOrder(): List<String> = DEFAULT_TOOLBAR_ORDER

    fun setToolbarOrder(order: List<String>) = Unit

    companion object {
        const val DEFAULT_KEYBOARD_HEIGHT_DP = 270
        val DEFAULT_TOOLBAR_ORDER = listOf("settings", "search", "resize")
    }
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

    override fun keyboardHeightDp(): Int = preferences.getInt(
        KEY_KEYBOARD_HEIGHT_DP,
        KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP,
    ).coerceIn(MIN_KEYBOARD_HEIGHT_DP, MAX_KEYBOARD_HEIGHT_DP)

    override fun setKeyboardHeightDp(heightDp: Int) {
        preferences.edit().putInt(
            KEY_KEYBOARD_HEIGHT_DP,
            heightDp.coerceIn(MIN_KEYBOARD_HEIGHT_DP, MAX_KEYBOARD_HEIGHT_DP),
        ).apply()
    }

    override fun toolbarOrder(): List<String> {
        val stored = preferences.getString(KEY_TOOLBAR_ORDER, null)
            ?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        return (stored + KeyboardSettings.DEFAULT_TOOLBAR_ORDER).distinct()
            .filter { it in KeyboardSettings.DEFAULT_TOOLBAR_ORDER }
    }

    override fun setToolbarOrder(order: List<String>) {
        val valid = order.filter { it in KeyboardSettings.DEFAULT_TOOLBAR_ORDER }.distinct()
        preferences.edit().putString(KEY_TOOLBAR_ORDER, valid.joinToString(",")).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "keyboard_settings"
        private const val KEY_START_IN_SYMBOLS = "start_in_symbols"
        private const val KEY_PERSONALIZED_SUGGESTIONS = "personalized_suggestions"
        private const val KEY_GLIDE_TYPING = "glide_typing"
        private const val KEY_KEYBOARD_HEIGHT_DP = "keyboard_height_dp"
        private const val KEY_TOOLBAR_ORDER = "toolbar_order"
        private const val MIN_KEYBOARD_HEIGHT_DP = 260
        private const val MAX_KEYBOARD_HEIGHT_DP = 360
    }
}
