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
        val DEFAULT_TOOLBAR_ORDER = listOf("settings", "search", "resize")
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
            .filter { it in KeyboardSettings.DEFAULT_TOOLBAR_ORDER }
    }

    override fun setToolbarOrder(order: List<String>) {
        val valid = order.filter { it in KeyboardSettings.DEFAULT_TOOLBAR_ORDER }.distinct()
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
        private const val KEY_KEYBOARD_HEIGHT_DP = "keyboard_height_dp"
        private const val KEY_RESIZE_HEIGHT_DP = "resize_height_dp"
        private const val KEY_RESIZE_TOP_OFFSET_DP = "resize_top_offset_dp"
        private const val KEY_RESIZE_BOTTOM_SPACE_DP = "resize_bottom_space_dp"
        private const val KEY_RESIZE_BOUNDARY_OFFSET_DP = "resize_boundary_offset_dp"
        private const val KEY_TOOLBAR_ORDER = "toolbar_order"
    }
}
