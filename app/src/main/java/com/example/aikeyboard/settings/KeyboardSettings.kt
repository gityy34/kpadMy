package com.example.aikeyboard.settings

import android.content.Context
import android.content.res.Configuration
import com.example.aikeyboard.core.KeyboardMode

/** Geometry persisted for the Resize feature only. Values are density-independent pixels. */
data class ResizeConfiguration(
    val keyboardHeightDp: Int = KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP,
    val topOffsetDp: Int = 0,
    val bottomSpaceDp: Int = 0,
    /** Visual position of the lower resize boundary inside the reserved area. */
    val bottomBoundaryOffsetDp: Int = 0,
) {
    fun clamped(): ResizeConfiguration = copy(
        keyboardHeightDp = keyboardHeightDp.coerceIn(MIN_KEYBOARD_HEIGHT_DP, MAX_KEYBOARD_HEIGHT_DP),
        topOffsetDp = topOffsetDp.coerceIn(0, MAX_MOVE_OFFSET_DP),
        bottomSpaceDp = bottomSpaceDp.coerceIn(0, MAX_BOTTOM_SPACE_DP),
        bottomBoundaryOffsetDp = bottomBoundaryOffsetDp.coerceIn(
            MIN_BOTTOM_BOUNDARY_OFFSET_DP,
            MAX_BOTTOM_BOUNDARY_OFFSET_DP,
        ),
    )

    companion object {
        const val MIN_KEYBOARD_HEIGHT_DP = 260
        const val MAX_KEYBOARD_HEIGHT_DP = 360
        const val MAX_MOVE_OFFSET_DP = 120
        const val MAX_BOTTOM_SPACE_DP = 180
        const val MIN_BOTTOM_BOUNDARY_OFFSET_DP = -96
        const val MAX_BOTTOM_BOUNDARY_OFFSET_DP = 180
    }
}

/** Persistence boundary. The input engine never reads preferences during a key press. */
interface KeyboardSettings {
    fun initialKeyboardMode(): KeyboardMode

    fun personalizedSuggestionsEnabled(): Boolean = true

    fun setPersonalizedSuggestionsEnabled(enabled: Boolean) = Unit

    fun glideTypingEnabled(): Boolean = true

    fun setGlideTypingEnabled(enabled: Boolean) = Unit

    fun fontSizeLevel(): Int = DEFAULT_FONT_SIZE_LEVEL

    fun setFontSizeLevel(level: Int) = Unit

    fun emojiSizeLevel(): Int = DEFAULT_EMOJI_SIZE_LEVEL

    fun setEmojiSizeLevel(level: Int) = Unit

    fun showLanguageSwitchKey(): Boolean = true

    fun setShowLanguageSwitchKey(show: Boolean) = Unit

    fun numberRowEnabled(): Boolean = false

    fun setNumberRowEnabled(enabled: Boolean) = Unit

    fun longPressDelayLevel(): Int = DEFAULT_LONG_PRESS_DELAY_LEVEL

    fun setLongPressDelayLevel(level: Int) = Unit

    /** Backward-compatible storage API for the unified Long Press setting. */
    fun alternateCharactersEnabled(): Boolean = true

    fun setAlternateCharactersEnabled(enabled: Boolean) = Unit

    fun longPressEnabled(): Boolean = alternateCharactersEnabled()

    fun setLongPressEnabled(enabled: Boolean) = setAlternateCharactersEnabled(enabled)

    fun emojiShortcutEnabled(): Boolean = true

    fun setEmojiShortcutEnabled(enabled: Boolean) = Unit

    fun clipboardHistoryEnabled(): Boolean = true

    fun setClipboardHistoryEnabled(enabled: Boolean) = Unit

    fun keyboardHeightDp(): Int = DEFAULT_KEYBOARD_HEIGHT_DP

    fun setKeyboardHeightDp(heightDp: Int) = Unit

    fun resizeConfiguration(orientation: Int = Configuration.ORIENTATION_PORTRAIT): ResizeConfiguration =
        ResizeConfiguration(keyboardHeightDp())

    fun setResizeConfiguration(
        configuration: ResizeConfiguration,
        orientation: Int = Configuration.ORIENTATION_PORTRAIT,
    ) {
        setKeyboardHeightDp(configuration.keyboardHeightDp)
    }

    fun toolbarOrder(): List<String> = DEFAULT_TOOLBAR_ORDER

    fun setToolbarOrder(order: List<String>) = Unit

    companion object {
        const val DEFAULT_KEYBOARD_HEIGHT_DP = 270
        const val SIZE_LEVEL_COUNT = 7
        const val DEFAULT_FONT_SIZE_LEVEL = 3
        const val DEFAULT_EMOJI_SIZE_LEVEL = 3
        const val LONG_PRESS_DELAY_LEVEL_COUNT = 7
        const val DEFAULT_LONG_PRESS_DELAY_LEVEL = 2
        val LONG_PRESS_DELAY_MILLIS = longArrayOf(100L, 200L, 300L, 400L, 500L, 600L, 700L)
        val DEFAULT_TOOLBAR_ORDER = listOf("settings", "search", "resize")
        val SUPPORTED_TOOLBAR_ITEMS = listOf(
            "settings", "search", "resize", "language", "themes", "emoji", "clipboard", "undo", "redo",
        )

        // Seven stable levels. Level 3 remains the existing/default keyboard size (100%).
        // The range is intentionally limited so labels remain readable without changing key
        // geometry or touch targets.
        val FONT_SIZE_SCALES = floatArrayOf(0.70f, 0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f)
        val EMOJI_SIZE_SCALES = floatArrayOf(0.78f, 0.86f, 0.94f, 1f, 1.08f, 1.18f, 1.28f)
        const val ACTION_SETTINGS_CHANGED = "com.example.aikeyboard.SETTINGS_CHANGED"

        fun fontScaleForLevel(level: Int): Float =
            FONT_SIZE_SCALES[level.coerceIn(0, FONT_SIZE_SCALES.lastIndex)]

        fun emojiScaleForLevel(level: Int): Float =
            EMOJI_SIZE_SCALES[level.coerceIn(0, EMOJI_SIZE_SCALES.lastIndex)]

        fun longPressDelayForLevel(level: Int): Long =
            LONG_PRESS_DELAY_MILLIS[level.coerceIn(0, LONG_PRESS_DELAY_MILLIS.lastIndex)]
    }
}

class SharedPreferencesKeyboardSettings(context: Context) : KeyboardSettings {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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

    override fun fontSizeLevel(): Int = preferences.getInt(
        KEY_FONT_SIZE_LEVEL,
        KeyboardSettings.DEFAULT_FONT_SIZE_LEVEL,
    ).coerceIn(0, KeyboardSettings.SIZE_LEVEL_COUNT - 1)

    override fun setFontSizeLevel(level: Int) {
        preferences.edit().putInt(
            KEY_FONT_SIZE_LEVEL,
            level.coerceIn(0, KeyboardSettings.SIZE_LEVEL_COUNT - 1),
        ).apply()
    }

    override fun emojiSizeLevel(): Int = preferences.getInt(
        KEY_EMOJI_SIZE_LEVEL,
        KeyboardSettings.DEFAULT_EMOJI_SIZE_LEVEL,
    ).coerceIn(0, KeyboardSettings.SIZE_LEVEL_COUNT - 1)

    override fun setEmojiSizeLevel(level: Int) {
        preferences.edit().putInt(
            KEY_EMOJI_SIZE_LEVEL,
            level.coerceIn(0, KeyboardSettings.SIZE_LEVEL_COUNT - 1),
        ).apply()
    }

    override fun showLanguageSwitchKey(): Boolean =
        preferences.getBoolean(KEY_SHOW_LANGUAGE_SWITCH, true)

    override fun setShowLanguageSwitchKey(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_LANGUAGE_SWITCH, show).apply()
    }

    override fun numberRowEnabled(): Boolean =
        preferences.getBoolean(KEY_NUMBER_ROW_ENABLED, false)

    override fun setNumberRowEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NUMBER_ROW_ENABLED, enabled).apply()
    }

    override fun longPressDelayLevel(): Int {
        val stored = preferences.getInt(
            KEY_LONG_PRESS_DELAY_LEVEL,
            KeyboardSettings.DEFAULT_LONG_PRESS_DELAY_LEVEL,
        )
        val format = preferences.getInt(KEY_LONG_PRESS_DELAY_FORMAT_VERSION, 0)
        if (format >= LONG_PRESS_DELAY_FORMAT_VERSION ||
            !preferences.contains(KEY_LONG_PRESS_DELAY_LEVEL)
        ) {
            return stored.coerceIn(0, KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT - 1)
        }

        // Preserve the closest meaning for users upgrading from the old 350/500/700/900 ms
        // presets rather than silently changing their selected timing.
        val migrated = when (stored) {
            0 -> 3 // 350 ms -> 400 ms
            1 -> 4 // 500 ms -> 500 ms
            2 -> 6 // 700 ms -> 700 ms
            else -> 6 // 900 ms -> the new maximum of 700 ms
        }
        preferences.edit()
            .putInt(KEY_LONG_PRESS_DELAY_LEVEL, migrated)
            .putInt(KEY_LONG_PRESS_DELAY_FORMAT_VERSION, LONG_PRESS_DELAY_FORMAT_VERSION)
            .apply()
        return migrated
    }

    override fun setLongPressDelayLevel(level: Int) {
        preferences.edit()
            .putInt(
                KEY_LONG_PRESS_DELAY_LEVEL,
                level.coerceIn(0, KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT - 1),
            )
            .putInt(KEY_LONG_PRESS_DELAY_FORMAT_VERSION, LONG_PRESS_DELAY_FORMAT_VERSION)
            .apply()
    }

    override fun alternateCharactersEnabled(): Boolean =
        preferences.getBoolean(KEY_ALTERNATE_CHARACTERS_ENABLED, true)

    override fun emojiShortcutEnabled(): Boolean =
        preferences.getBoolean(KEY_EMOJI_SHORTCUT_ENABLED, true)

    override fun setEmojiShortcutEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EMOJI_SHORTCUT_ENABLED, enabled).apply()
    }

    override fun setAlternateCharactersEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ALTERNATE_CHARACTERS_ENABLED, enabled).apply()
    }

    override fun clipboardHistoryEnabled(): Boolean =
        preferences.getBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, true)

    override fun setClipboardHistoryEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, enabled).apply()
    }

    override fun keyboardHeightDp(): Int =
        resizeConfiguration(currentOrientation()).keyboardHeightDp

    override fun setKeyboardHeightDp(heightDp: Int) {
        val current = resizeConfiguration(currentOrientation())
        setResizeConfiguration(current.copy(keyboardHeightDp = heightDp), currentOrientation())
    }

    override fun resizeConfiguration(orientation: Int): ResizeConfiguration {
        val suffix = orientationSuffix(orientation)
        val legacyHeight = preferences.getInt(
            KEY_KEYBOARD_HEIGHT_DP,
            KeyboardSettings.DEFAULT_KEYBOARD_HEIGHT_DP,
        )
        return ResizeConfiguration(
            keyboardHeightDp = preferences.getInt(KEY_RESIZE_HEIGHT_DP + suffix, legacyHeight),
            topOffsetDp = preferences.getInt(KEY_RESIZE_TOP_OFFSET_DP + suffix, 0),
            bottomSpaceDp = preferences.getInt(KEY_RESIZE_BOTTOM_SPACE_DP + suffix, 0),
            bottomBoundaryOffsetDp = preferences.getInt(
                KEY_RESIZE_BOUNDARY_OFFSET_DP + suffix,
                0,
            ),
        ).clamped()
    }

    override fun setResizeConfiguration(
        configuration: ResizeConfiguration,
        orientation: Int,
    ) {
        val value = configuration.clamped()
        val suffix = orientationSuffix(orientation)
        preferences.edit()
            .putInt(KEY_RESIZE_HEIGHT_DP + suffix, value.keyboardHeightDp)
            .putInt(KEY_RESIZE_TOP_OFFSET_DP + suffix, value.topOffsetDp)
            .putInt(KEY_RESIZE_BOTTOM_SPACE_DP + suffix, value.bottomSpaceDp)
            .putInt(KEY_RESIZE_BOUNDARY_OFFSET_DP + suffix, value.bottomBoundaryOffsetDp)
            .putInt(KEY_KEYBOARD_HEIGHT_DP, value.keyboardHeightDp)
            .apply()
    }

    override fun toolbarOrder(): List<String> {
        val stored = preferences.getString(KEY_TOOLBAR_ORDER, null)
            ?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        return (stored + KeyboardSettings.DEFAULT_TOOLBAR_ORDER).distinct()
            .filter { it in KeyboardSettings.SUPPORTED_TOOLBAR_ITEMS }
    }

    override fun setToolbarOrder(order: List<String>) {
        val valid = order.filter { it in KeyboardSettings.SUPPORTED_TOOLBAR_ITEMS }.distinct()
        preferences.edit().putString(KEY_TOOLBAR_ORDER, valid.joinToString(",")).apply()
    }

    private fun currentOrientation(): Int = appContext.resources.configuration.orientation

    private fun orientationSuffix(orientation: Int): String =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) "_landscape" else "_portrait"

    companion object {
        private const val PREFERENCES_NAME = "keyboard_settings"
        private const val KEY_START_IN_SYMBOLS = "start_in_symbols"
        private const val KEY_PERSONALIZED_SUGGESTIONS = "personalized_suggestions"
        private const val KEY_GLIDE_TYPING = "glide_typing"
        private const val KEY_FONT_SIZE_LEVEL = "font_size_level"
        private const val KEY_EMOJI_SIZE_LEVEL = "emoji_size_level"
        private const val KEY_SHOW_LANGUAGE_SWITCH = "show_language_switch_key"
        private const val KEY_NUMBER_ROW_ENABLED = "number_row_enabled"
        private const val KEY_LONG_PRESS_DELAY_LEVEL = "long_press_delay_level"
        private const val KEY_LONG_PRESS_DELAY_FORMAT_VERSION = "long_press_delay_format_version"
        private const val LONG_PRESS_DELAY_FORMAT_VERSION = 1
        private const val KEY_ALTERNATE_CHARACTERS_ENABLED = "alternate_characters_enabled"
        private const val KEY_EMOJI_SHORTCUT_ENABLED = "emoji_shortcut_enabled"
        private const val KEY_CLIPBOARD_HISTORY_ENABLED = "clipboard_history_enabled"
        private const val KEY_KEYBOARD_HEIGHT_DP = "keyboard_height_dp"
        private const val KEY_RESIZE_HEIGHT_DP = "resize_height_dp"
        private const val KEY_RESIZE_TOP_OFFSET_DP = "resize_top_offset_dp"
        private const val KEY_RESIZE_BOTTOM_SPACE_DP = "resize_bottom_space_dp"
        private const val KEY_RESIZE_BOUNDARY_OFFSET_DP = "resize_boundary_offset_dp"
        private const val KEY_TOOLBAR_ORDER = "toolbar_order"
    }
}
