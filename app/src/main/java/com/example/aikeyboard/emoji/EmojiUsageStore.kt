package com.example.aikeyboard.emoji

import android.content.Context

class EmojiUsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("emoji_usage", Context.MODE_PRIVATE)

    fun recent(): List<String> = read(KEY_RECENT)

    fun frequent(): List<String> = read(KEY_FREQUENT).sortedByDescending { prefs.getInt("count_$it", 0) }

    fun record(emoji: String) {
        val recent = (listOf(emoji) + recent().filterNot { it == emoji }).take(MAX_RECENT)
        prefs.edit()
            .putString(KEY_RECENT, recent.joinToString("\u0001"))
            .putInt("count_$emoji", prefs.getInt("count_$emoji", 0) + 1)
            .putString(KEY_FREQUENT, (frequent() + emoji).distinct().take(MAX_FREQUENT).joinToString("\u0001"))
            .apply()
    }

    private fun read(key: String): List<String> = prefs.getString(key, null).orEmpty()
        .split('\u0001').filter { it.isNotBlank() }

    companion object {
        private const val KEY_RECENT = "recent"
        private const val KEY_FREQUENT = "frequent"
        private const val MAX_RECENT = 32
        private const val MAX_FREQUENT = 64
    }
}
