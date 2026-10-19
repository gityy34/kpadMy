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
        onEvent(VoiceEvent.State(state))
        speech.start(selectedLanguage)
    }

    fun pause() {
        if (state != VoiceSessionState.LISTENING) return
        state = VoiceSessionState.PAUSED
        speech.pause()
        onEvent(VoiceEvent.State(state))
    }

    fun resume() {
        if (state != VoiceSessionState.PAUSED) return
        state = VoiceSessionState.STARTING
        speech.start(language)
        onEvent(VoiceEvent.State(state))
    }

    fun stop() {
        if (state != VoiceSessionState.LISTENING && state != VoiceSessionState.PAUSED) return
        state = VoiceSessionState.PROCESSING
        speech.stop()
        onEvent(VoiceEvent.State(state))
    }

    fun cancel() {
        generation.incrementAndGet()
        activeGeneration = generation.get()
        speech.cancel()
        transcript = ""
        state = VoiceSessionState.IDLE
        onEvent(VoiceEvent.State(state))
    }

    fun translate(target: TranslationTarget) {
        val text = transcript
        if (text.isBlank()) return
        val request = activeGeneration
        translation.translate(text, target) { result ->
            mainHandler.post {
                if (generation.get() != request) return@post
                result.onSuccess { onEvent(VoiceEvent.Translation(it, target)) }
                    .onFailure { onEvent(VoiceEvent.State(VoiceSessionState.ERROR, "Translation is unavailable offline")) }
            }
        }
    }

    fun close() {
        generation.incrementAndGet()
        speech.close()
        translation.close()
    }

    fun handleProviderEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.PartialText -> onEvent(event)
            is VoiceEvent.Transcript -> {
                transcript = mergeTranscript(transcript, event.text)
                // stopListening() is also used for Pause. Keep the session paused when its
                // final segment arrives so Resume starts a new segment without losing text.
                val paused = state == VoiceSessionState.PAUSED
                state = if (paused) VoiceSessionState.PAUSED else VoiceSessionState.RESULT_READY
                onEvent(VoiceEvent.Transcript(transcript))
                onEvent(VoiceEvent.State(state))
            }
            is VoiceEvent.State -> {
                if (event.state == VoiceSessionState.LISTENING) state = event.state
                if (event.state == VoiceSessionState.ERROR) state = event.state
                onEvent(event)
            }
            is VoiceEvent.Translation -> onEvent(event)
        }
    }
    private fun mergeTranscript(previous: String, incoming: String): String {
        val next = incoming.trim()
        if (next.isEmpty()) return previous
        if (previous.isBlank()) return next
        if (next == previous || previous.endsWith(next)) return previous
        if (next.startsWith(previous)) return next
        return "$previous $next".trim()
    }

}
