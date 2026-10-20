package com.example.aikeyboard.voice

enum class SpeechLanguage(val localeTag: String, val label: String) {
    ENGLISH("en-US", "English"),
    URDU("ur-PK", "Urdu"),
    // Empty locale lets the recognizer/provider choose instead of silently forcing English.
    AUTO("", "Auto"),
}

enum class TranslationTarget(val label: String) {
    NONE("No Translation"),
    ENGLISH("English"),
    URDU("Urdu"),
}

enum class VoiceSessionState { IDLE, STARTING, LISTENING, PAUSED, PROCESSING, RESULT_READY, ERROR }

sealed class VoiceEvent(open val sessionId: Long = 0L) {
    data class State(
        val state: VoiceSessionState,
        val message: String = "",
        override val sessionId: Long = 0L,
    ) : VoiceEvent(sessionId)

    data class PartialText(
        val text: String,
        override val sessionId: Long = 0L,
    ) : VoiceEvent(sessionId)

    data class Transcript(
        val text: String,
        override val sessionId: Long = 0L,
    ) : VoiceEvent(sessionId)

    data class Translation(
        val text: String,
        val target: TranslationTarget,
        override val sessionId: Long = 0L,
    ) : VoiceEvent(sessionId)
}

interface SpeechRecognitionProvider {
    fun start(language: SpeechLanguage)

    /** Session-aware start. Legacy providers may keep implementing the one-argument method. */
    fun start(language: SpeechLanguage, sessionId: Long) {
        start(language)
    }

    fun pause()
    fun stop()
    fun cancel()
    fun close()
}

interface TranslationProvider {
    fun translate(text: String, target: TranslationTarget, callback: (Result<String>) -> Unit)
    fun close()
}
