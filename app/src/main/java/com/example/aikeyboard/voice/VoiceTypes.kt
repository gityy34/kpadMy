package com.example.aikeyboard.voice

enum class SpeechLanguage(val localeTag: String, val label: String) {
    ENGLISH("en-US", "English"),
    URDU("ur-PK", "Urdu"),
    // Auto mode must not silently force the recognizer to English.
    AUTO("", "Auto"),
}

enum class TranslationTarget(val label: String) {
    NONE("No Translation"),
    ENGLISH("English"),
    URDU("Urdu"),
}

enum class VoiceSessionState { IDLE, STARTING, LISTENING, PAUSED, PROCESSING, RESULT_READY, ERROR }

sealed class VoiceEvent {
    data class State(val state: VoiceSessionState, val message: String = "") : VoiceEvent()
    data class PartialText(val text: String) : VoiceEvent()
    data class Transcript(val text: String) : VoiceEvent()
    data class Translation(val text: String, val target: TranslationTarget) : VoiceEvent()
}

interface SpeechRecognitionProvider {
    fun start(language: SpeechLanguage)
    fun pause()
    fun stop()
    fun cancel()
    fun close()
}

interface TranslationProvider {
    fun translate(text: String, target: TranslationTarget, callback: (Result<String>) -> Unit)
    fun close()
}
