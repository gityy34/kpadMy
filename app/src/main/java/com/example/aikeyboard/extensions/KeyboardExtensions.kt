package com.example.aikeyboard.extensions

/**
 * Future feature contracts. Phase 1 intentionally does not instantiate these from the input
 * path. Implementations must run asynchronously and may not delay commitText/delete calls.
 */
interface SuggestionProvider {
    fun requestSuggestions(beforeCursor: CharSequence, callback: (List<String>) -> Unit)
    fun cancelPendingRequests()
}

interface GlideTypingProvider

interface VoiceTypingProvider

interface PersonalLearningStore

interface ThemeProvider

interface AiToolsProvider
