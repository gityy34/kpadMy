package com.example.aikeyboard.theme

import android.content.Context
import android.content.Intent

/** Single local repository for built-in, custom, selected, and previewable themes. */
class ThemeStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun activeTheme(): KeyboardTheme = themeById(preferences.getString(KEY_ACTIVE, KeyboardTheme.SOLID_WHITE.id))

    fun builtInThemes(): List<KeyboardTheme> = KeyboardTheme.BUILT_INS

    fun customThemes(): List<KeyboardTheme> = decode(preferences.getString(KEY_CUSTOM, null))

    fun allThemes(): List<KeyboardTheme> =
        (builtInThemes() + customThemes()).distinctBy { it.id }

    fun themesFor(category: ThemeCategory): List<KeyboardTheme> =
        allThemes().filter { it.category == category }

    fun apply(theme: KeyboardTheme) {
        val stored = if (theme.isBuiltIn || theme.id in builtInThemes().map { it.id }) {
            theme
        } else {
            saveCustom(theme)
        }
        preferences.edit().putString(KEY_ACTIVE, stored.id).apply()
        broadcastThemeChanged()
    }

    fun saveCustom(theme: KeyboardTheme): KeyboardTheme {
        val safeId = if (theme.id.startsWith("custom_")) {
            theme.id
        } else {
            "custom_" + theme.name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "theme" }
        }
        val safe = theme.copy(
            id = safeId,
            category = if (theme.hasImage) ThemeCategory.LANDSCAPES else ThemeCategory.MY_THEMES,
            isBuiltIn = false,
            isDeletable = true,
        )
        val updated = customThemes().filterNot { it.id == safe.id } + safe
        preferences.edit().putString(KEY_CUSTOM, encode(updated)).apply()
        return safe
    }

    fun deleteCustom(id: String) {
        if (id in builtInThemes().map { it.id }) return
        val remaining = customThemes().filterNot { it.id == id }
        preferences.edit().putString(KEY_CUSTOM, encode(remaining)).apply()
        if (preferences.getString(KEY_ACTIVE, null) == id) {
            preferences.edit().putString(KEY_ACTIVE, KeyboardTheme.SOLID_WHITE.id).apply()
            broadcastThemeChanged()
        }
    }

    fun themeById(id: String?): KeyboardTheme =
        builtInThemes().firstOrNull { it.id == id }
            ?: customThemes().firstOrNull { it.id == id }
            ?: KeyboardTheme.SOLID_WHITE

    private fun broadcastThemeChanged() {
        appContext.sendBroadcast(Intent(ACTION_THEME_CHANGED).setPackage(appContext.packageName))
    }

    private fun encode(themes: List<KeyboardTheme>): String = themes.joinToString("||") { theme ->
        listOf(
            theme.id, theme.name, theme.backgroundColor, theme.keyColor, theme.specialKeyColor,
            theme.pressedKeyColor, theme.shiftColor, theme.textColor, theme.accentColor,
            theme.borderColor, theme.keyStyle.name, theme.gradientStart ?: "", theme.gradientEnd ?: "",
            theme.backgroundImageUri ?: "", theme.category.name, theme.isBuiltIn, theme.isDeletable,
        ).joinToString("|") { it.toString().replace("|", "") }
    }

    private fun decode(value: String?): List<KeyboardTheme> = value.orEmpty()
        .split("||")
        .filter { it.isNotBlank() }
        .mapNotNull { encoded ->
            val fields = encoded.split('|')
            if (fields.size < 14) return@mapNotNull null
            runCatching {
                val category = fields.getOrNull(14)
                    ?.toEnumOrNull<ThemeCategory>()
                    ?: if (fields.getOrNull(13).orEmpty().isNotBlank()) {
                        ThemeCategory.LANDSCAPES
                    } else {
                        ThemeCategory.MY_THEMES
                    }
                KeyboardTheme(
                    id = fields[0],
                    name = fields[1],
                    backgroundColor = fields[2].toInt(),
                    keyColor = fields[3].toInt(),
                    specialKeyColor = fields[4].toInt(),
                    pressedKeyColor = fields[5].toInt(),
                    shiftColor = fields[6].toInt(),
                    textColor = fields[7].toInt(),
                    accentColor = fields[8].toInt(),
                    borderColor = fields[9].toInt(),
                    keyStyle = KeyStyle.valueOf(fields[10]),
                    gradientStart = fields[11].toIntOrNull(),
                    gradientEnd = fields[12].toIntOrNull(),
                    backgroundImageUri = fields[13].ifBlank { null },
                    category = category,
                    isBuiltIn = fields.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
                    isDeletable = fields.getOrNull(16)?.toBooleanStrictOrNull() ?: true,
                )
            }.getOrNull()
        }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()

    companion object {
        const val ACTION_THEME_CHANGED = "com.example.aikeyboard.THEME_CHANGED"
        private const val PREFERENCES = "keyboard_themes"
        private const val KEY_ACTIVE = "active_theme"
        private const val KEY_CUSTOM = "custom_themes"
    }
}
