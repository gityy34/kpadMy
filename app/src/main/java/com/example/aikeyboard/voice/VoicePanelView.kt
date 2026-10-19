package com.example.aikeyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.aikeyboard.theme.KeyboardTheme
import java.util.Locale

class VoicePanelView(
    context: Context,
    private val onStart: (SpeechLanguage) -> Unit,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
    private val onCancel: () -> Unit,
    private val onTranslate: (TranslationTarget) -> Unit,
    private val onInsert: (String) -> Unit,
    private val onBack: () -> Unit,
) : LinearLayout(context) {
    private val languageButton = Button(context)
    private val targetButton = Button(context)
    private val recordButton = Button(context)
    private val stopButton = Button(context)
    private val deleteButton = Button(context)
    private val translateButton = Button(context)
    private val insertOriginalButton = Button(context)
    private val insertTranslatedButton = Button(context)
    private val status = TextView(context)
    private val timer = TextView(context)
    private val result = TextView(context)
    private val waveform = VoiceWaveformView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var language = SpeechLanguage.AUTO
    private var target = TranslationTarget.NONE
    private var state = VoiceSessionState.IDLE
    private var startedAt = 0L
    private var pausedAt = 0L
    private var accumulatedPausedMillis = 0L
    private var transcript = ""
    private var translated = ""
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateTimer()
            if (state == VoiceSessionState.LISTENING) handler.postDelayed(this, 500L)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP
        setPadding(12, 8, 12, 8)
        status.gravity = Gravity.CENTER
        status.textSize = 15f
        timer.gravity = Gravity.CENTER
        timer.textSize = 18f
        result.textSize = 16f
        result.setPadding(12, 4, 12, 4)
        result.minLines = 2
        languageButton.setOnClickListener { cycleLanguage() }
        targetButton.setOnClickListener { cycleTarget() }
        recordButton.setOnClickListener {
            when (state) {
                VoiceSessionState.LISTENING -> onPause()
                VoiceSessionState.PAUSED -> onResume()
                else -> onStart(language)
            }
        }
        stopButton.setOnClickListener { onStop() }
        deleteButton.setOnClickListener {
            if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED) onCancel()
            else clearResult()
        }
        translateButton.setOnClickListener { onTranslate(target) }
        insertOriginalButton.setOnClickListener { if (transcript.isNotBlank()) onInsert(transcript) }
        insertTranslatedButton.setOnClickListener { if (translated.isNotBlank()) onInsert(translated) }
        addView(status, LayoutParams(-1, -2))
        addView(timer, LayoutParams(-1, -2))
        val selectors = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        selectors.addView(languageButton, LayoutParams(0, -2, 1f))
        selectors.addView(targetButton, LayoutParams(0, -2, 1f))
        addView(selectors)
        addView(waveform, LayoutParams(-1, 42))
        addView(result, LayoutParams(-1, -2))
        val controls = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(recordButton, LayoutParams(0, -2, 1f))
        controls.addView(stopButton, LayoutParams(0, -2, 1f))
        controls.addView(deleteButton, LayoutParams(0, -2, 1f))
        addView(controls)
        addView(translateButton, LayoutParams(-1, -2))
        addView(insertOriginalButton, LayoutParams(-1, -2))
        addView(insertTranslatedButton, LayoutParams(-1, -2))
        addView(Button(context).apply {
            text = "Back to Keyboard"
            setOnClickListener { onBack() }
        }, LayoutParams(-1, -2))
        updateButtons()
    }

    fun applyTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.backgroundColor)
        status.setTextColor(theme.textColor)
        timer.setTextColor(theme.textColor)
        result.setTextColor(theme.textColor)
        waveform.setColors(theme.accentColor, theme.keyColor)
    }

    fun handle(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.State -> {
                if (event.state == VoiceSessionState.PAUSED) pausedAt = System.currentTimeMillis()
                if (event.state == VoiceSessionState.LISTENING && pausedAt > 0L) {
                    accumulatedPausedMillis += System.currentTimeMillis() - pausedAt
                    pausedAt = 0L
                }
                state = event.state
                if (event.message.isNotBlank()) status.text = event.message else status.text = stateLabel(state)
                if (state == VoiceSessionState.LISTENING) {
                    if (startedAt == 0L) startedAt = System.currentTimeMillis()
                    waveform.setActive(true)
                    handler.removeCallbacks(timerRunnable)
                    handler.post(timerRunnable)
                } else {
                    waveform.setActive(false)
                    handler.removeCallbacks(timerRunnable)
                }
                updateButtons()
            }
            is VoiceEvent.PartialText -> {
                result.text = event.text
            }
            is VoiceEvent.Transcript -> {
                transcript = event.text
                translated = ""
                result.text = transcript.ifBlank { "No speech detected" }
                updateButtons()
            }
            is VoiceEvent.Translation -> {
                translated = event.text
                result.text = "Original:\n$transcript\n\n${event.target.label}:\n$translated"
                updateButtons()
            }
        }
    }

    private fun cycleLanguage() {
        language = when (language) {
            SpeechLanguage.AUTO -> SpeechLanguage.ENGLISH
            SpeechLanguage.ENGLISH -> SpeechLanguage.URDU
            SpeechLanguage.URDU -> SpeechLanguage.AUTO
        }
        languageButton.text = "Speech: ${language.label}"
    }

    private fun cycleTarget() {
        target = when (target) {
            TranslationTarget.NONE -> TranslationTarget.ENGLISH
            TranslationTarget.ENGLISH -> TranslationTarget.URDU
            TranslationTarget.URDU -> TranslationTarget.NONE
        }
        targetButton.text = "Translate: ${target.label}"
        updateButtons()
    }

    private fun clearResult() {
        transcript = ""
        translated = ""
        result.text = ""
        state = VoiceSessionState.IDLE
        updateButtons()
    }

    private fun updateButtons() {
        languageButton.text = "Speech: ${language.label}"
        targetButton.text = "Translate: ${target.label}"
        recordButton.text = when (state) {
            VoiceSessionState.LISTENING -> "Pause"
            VoiceSessionState.PAUSED -> "Resume"
            else -> "Start"
        }
        stopButton.text = "Stop / Finish"
        deleteButton.text = if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED) "Cancel" else "Delete"
        translateButton.text = "Translate result"
        insertOriginalButton.text = "Insert original"
        insertTranslatedButton.text = "Insert translated"
        stopButton.isEnabled = state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED
        translateButton.isEnabled = transcript.isNotBlank() && target != TranslationTarget.NONE
        insertOriginalButton.isEnabled = transcript.isNotBlank()
        insertTranslatedButton.isEnabled = translated.isNotBlank()
        if (state == VoiceSessionState.IDLE) status.text = "Voice typing ready"
    }

    private fun updateTimer() {
        if (startedAt == 0L) return
        val now = if (state == VoiceSessionState.PAUSED && pausedAt > 0L) pausedAt else System.currentTimeMillis()
        val elapsed = (now - startedAt - accumulatedPausedMillis).coerceAtLeast(0L) / 1000L
        timer.text = String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
    }

    private fun stateLabel(value: VoiceSessionState): String = when (value) {
        VoiceSessionState.IDLE -> "Voice typing ready"
        VoiceSessionState.STARTING -> "Starting microphone…"
        VoiceSessionState.LISTENING -> "Listening…"
        VoiceSessionState.PAUSED -> "Paused"
        VoiceSessionState.PROCESSING -> "Processing speech…"
        VoiceSessionState.RESULT_READY -> "Result ready"
        VoiceSessionState.ERROR -> "Voice error"
    }
}

private class VoiceWaveformView(context: Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false
    private var foreground = 0xFF536FDC.toInt()
    private var background = 0xFFFFFFFF.toInt()

    fun setActive(value: Boolean) { active = value; invalidate() }
    fun setColors(foregroundColor: Int, backgroundColor: Int) {
        foreground = foregroundColor
        background = backgroundColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background)
        paint.color = foreground
        val count = 24
        val widthPerBar = width / count.toFloat()
        for (index in 0 until count) {
            val wave = if (active) ((index * 17 + System.currentTimeMillis() / 120) % 11) else 2
            val height = (4 + wave * 2) * resources.displayMetrics.density
            val x = widthPerBar * (index + 0.5f)
            canvas.drawRoundRect(x - 1.5f * resources.displayMetrics.density, height / 2f, x + 1.5f * resources.displayMetrics.density, this.height - height / 2f, 3f, 3f, paint)
        }
        if (active) postInvalidateDelayed(120L)
    }
}
