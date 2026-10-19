package com.example.aikeyboard.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.CopyOnWriteArrayList

/** Small local theme repository. Theme changes are visual and are broadcast to the active IME. */
class ThemeStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun activeTheme(): KeyboardTheme = themeById(preferences.getString(KEY_ACTIVE, KeyboardTheme.SOLID_WHITE.id))

    fun apply(theme: KeyboardTheme) {
        if (theme.id !in KeyboardTheme.BUILT_INS.map { it.id }) saveCustom(theme)
        preferences.edit().putString(KEY_ACTIVE, theme.id).apply()
        appContext.sendBroadcast(Intent(ACTION_THEME_CHANGED).setPackage(appContext.packageName))
    }

    fun customThemes(): List<KeyboardTheme> = decode(preferences.getString(KEY_CUSTOM, null))

    fun saveCustom(theme: KeyboardTheme): KeyboardTheme {
        val safe = theme.copy(id = if (theme.id.startsWith("custom_")) theme.id else "custom_${theme.name.lowercase().replace(" ", "_")}")
        val updated = (customThemes().filterNot { it.id == safe.id } + safe).takeLast(MAX_CUSTOM_THEMES)
        preferences.edit().putString(KEY_CUSTOM, encode(updated)).apply()
        return safe
    }

    fun deleteCustom(id: String) {
        if (id in KeyboardTheme.BUILT_INS.map { it.id }) return
        preferences.edit().putString(KEY_CUSTOM, encode(customThemes().filterNot { it.id == id })).apply()
        if (preferences.getString(KEY_ACTIVE, null) == id) apply(KeyboardTheme.SOLID_WHITE)
    }

    fun themeById(id: String?): KeyboardTheme =
        KeyboardTheme.BUILT_INS.firstOrNull { it.id == id }
            ?: customThemes().firstOrNull { it.id == id }
            ?: KeyboardTheme.SOLID_WHITE

    private fun encode(themes: List<KeyboardTheme>): String = themes.joinToString("||") { theme ->
        listOf(
            theme.id, theme.name, theme.backgroundColor, theme.keyColor, theme.specialKeyColor,
            theme.pressedKeyColor, theme.shiftColor, theme.textColor, theme.accentColor,
            theme.borderColor, theme.keyStyle.name, theme.gradientStart ?: "", theme.gradientEnd ?: "",
            theme.backgroundImageUri ?: "",
        ).joinToString("|") { it.toString().replace("|", "") }
    }

    private fun decode(value: String?): List<KeyboardTheme> = value.orEmpty().split("||")
        .filter { it.isNotBlank() }
        .mapNotNull { encoded ->
            val fields = encoded.split('|')
            if (fields.size < 14) return@mapNotNull null
            runCatching {
                KeyboardTheme(
                    id = fields[0], name = fields[1], backgroundColor = fields[2].toInt(), keyColor = fields[3].toInt(),
                    specialKeyColor = fields[4].toInt(), pressedKeyColor = fields[5].toInt(), shiftColor = fields[6].toInt(),
                    textColor = fields[7].toInt(), accentColor = fields[8].toInt(), borderColor = fields[9].toInt(),
                    keyStyle = KeyStyle.valueOf(fields[10]), gradientStart = fields[11].toIntOrNull(),
                    gradientEnd = fields[12].toIntOrNull(), backgroundImageUri = fields[13].ifBlank { null },
                )
            }.getOrNull()
        }

    companion object {
        const val ACTION_THEME_CHANGED = "com.example.aikeyboard.THEME_CHANGED"
        private const val PREFERENCES = "keyboard_themes"
        private const val KEY_ACTIVE = "active_theme"
        private const val KEY_CUSTOM = "custom_themes"
        private const val MAX_CUSTOM_THEMES = 12
    }
}
