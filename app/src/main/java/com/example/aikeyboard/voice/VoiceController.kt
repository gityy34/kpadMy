package com.example.aikeyboard.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

class VoiceController(
    context: Context,
    private val onEvent: (VoiceEvent) -> Unit,
    speechProvider: SpeechRecognitionProvider? = null,
    private val translation: TranslationProvider = LocalTranslationProvider(),
) {
    private val speech: SpeechRecognitionProvider =
        speechProvider ?: AndroidSpeechRecognitionProvider(context, ::handleProviderEvent)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)
    private var activeGeneration = 0L
    private var language = SpeechLanguage.AUTO
    private var state = VoiceSessionState.IDLE
    private var transcript = ""

    fun start(selectedLanguage: SpeechLanguage) {
        activeGeneration = generation.incrementAndGet()
        language = selectedLanguage
        transcript = ""
        state = VoiceSessionState.STARTING
        onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
        speech.start(selectedLanguage, activeGeneration)
    }

    fun pause() {
        if (state != VoiceSessionState.LISTENING) return
        state = VoiceSessionState.PAUSED
        speech.pause()
        onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
    }

    fun resume() {
        if (state != VoiceSessionState.PAUSED) return
        state = VoiceSessionState.STARTING
        speech.start(language, activeGeneration)
        onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
    }

    fun stop() {
        if (state != VoiceSessionState.LISTENING && state != VoiceSessionState.PAUSED) return
        state = VoiceSessionState.PROCESSING
        speech.stop()
        onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
    }

    fun cancel() {
        generation.incrementAndGet()
        activeGeneration = generation.get()
        speech.cancel()
        transcript = ""
        state = VoiceSessionState.IDLE
        onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
    }

    fun translate(target: TranslationTarget) {
        val text = transcript
        if (text.isBlank()) return
        val request = activeGeneration
        translation.translate(text, target) { result ->
            mainHandler.post {
                if (generation.get() != request) return@post
                result.onSuccess { onEvent(VoiceEvent.Translation(it, target, request)) }
                    .onFailure {
                        onEvent(VoiceEvent.State(
                            VoiceSessionState.ERROR,
                            "Translation is unavailable offline",
                            request,
                        ))
                    }
            }
        }
    }

    fun close() {
        generation.incrementAndGet()
        activeGeneration = generation.get()
        speech.close()
        translation.close()
    }

    fun handleProviderEvent(event: VoiceEvent) {
        // AndroidSpeechRecognitionProvider tags every event. sessionId=0 keeps compatibility
        // with existing test/fallback providers that predate the session-aware contract.
        if (event.sessionId != 0L && event.sessionId != activeGeneration) return

        when (event) {
            is VoiceEvent.PartialText -> {
                if (state != VoiceSessionState.IDLE && state != VoiceSessionState.ERROR) {
                    onEvent(event)
                }
            }

            is VoiceEvent.Transcript -> {
                val segment = event.text.trim()
                if (segment.isNotEmpty()) {
                    transcript = appendSegment(transcript, segment)
                }
                if (state == VoiceSessionState.PAUSED) {
                    // stopListening() may finish the paused segment asynchronously. Preserve it,
                    // but do not turn Pause into a terminal result.
                    onEvent(VoiceEvent.Transcript(transcript, activeGeneration))
                    onEvent(VoiceEvent.State(VoiceSessionState.PAUSED, sessionId = activeGeneration))
                } else {
                    state = VoiceSessionState.RESULT_READY
                    onEvent(VoiceEvent.Transcript(transcript, activeGeneration))
                    onEvent(VoiceEvent.State(state, sessionId = activeGeneration))
                }
            }

            is VoiceEvent.State -> {
                if (event.state == VoiceSessionState.ERROR && state == VoiceSessionState.PAUSED) {
                    // A recognizer commonly reports ERROR_CLIENT after stopListening during pause.
                    // Keep the logical session paused so Resume remains available.
                    return
                }
                if (event.state == VoiceSessionState.LISTENING) state = event.state
                if (event.state == VoiceSessionState.ERROR) state = event.state
                onEvent(event)
            }

            is VoiceEvent.Translation -> onEvent(event)
        }
    }

    private fun appendSegment(existing: String, segment: String): String {
        if (existing.isBlank()) return segment
        if (existing == segment) return existing
        return "$existing $segment".trim()
    }
}
