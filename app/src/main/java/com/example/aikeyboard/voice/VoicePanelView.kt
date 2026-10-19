package com.example.aikeyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.aikeyboard.theme.KeyboardTheme
import java.util.Locale

/**
 * Compact voice UI hosted inside the fixed IME content area. The ScrollView owns overflow; this
 * panel never asks the IME window for extra height.
 */
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
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val languageButton = Button(context)
    private val targetButton = Button(context)
    private val recordButton = Button(context)
    private val stopButton = Button(context)
    private val deleteButton = Button(context)
    private val translateButton = Button(context)
    private val insertOriginalButton = Button(context)
    private val insertTranslatedButton = Button(context)
    private val backButton = Button(context)
    private val status = TextView(context)
    private val timer = TextView(context)
    private val result = TextView(context)
    private val waveform = VoiceWaveformView(context)
    private val controls = LinearLayout(context)
    private val content = LinearLayout(context)
    private val scrollView = ScrollView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var language = SpeechLanguage.AUTO
    private var target = TranslationTarget.NONE
    private var state = VoiceSessionState.IDLE
    private var startedAt = 0L
    private var pausedAt = 0L
    private var accumulatedPausedMillis = 0L
    private var transcript = ""
    private var translated = ""
    private var theme = KeyboardTheme.SOLID_WHITE
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateTimer()
            if (state == VoiceSessionState.LISTENING) handler.postDelayed(this, 500L)
        }
    }

    init {
        setBackgroundColor(theme.backgroundColor)
        clipChildren = true
        clipToPadding = true

        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.TOP
        content.setPadding(dp(6), dp(4), dp(6), dp(8))
        scrollView.isFillViewport = true
        scrollView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        scrollView.addView(content, ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT,
        ))
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        status.gravity = Gravity.CENTER
        status.textSize = 15f
        timer.gravity = Gravity.CENTER
        timer.textSize = 15f
        result.textSize = 14f
        result.gravity = Gravity.CENTER
        result.setPadding(dp(8), dp(3), dp(8), dp(3))
        result.maxLines = 5

        configureButton(languageButton) { cycleLanguage() }
        configureButton(targetButton) { cycleTarget() }
        configureButton(recordButton) {
            when (state) {
                VoiceSessionState.LISTENING -> onPause()
                VoiceSessionState.PAUSED -> onResume()
                VoiceSessionState.IDLE,
                VoiceSessionState.RESULT_READY,
                VoiceSessionState.ERROR -> onStart(language)
                else -> Unit
            }
        }
        configureButton(stopButton) { onStop() }
        configureButton(deleteButton) {
            if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED) onCancel()
            else clearResult()
        }
        configureButton(translateButton) { onTranslate(target) }
        configureButton(insertOriginalButton) {
            if (transcript.isNotBlank()) onInsert(transcript)
        }
        configureButton(insertTranslatedButton) {
            if (translated.isNotBlank()) onInsert(translated)
        }
        configureButton(backButton) { onBack() }

        content.addView(status, fixedParams(30))
        content.addView(timer, fixedParams(24))

        val selectors = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        selectors.addView(languageButton, weightedParams())
        selectors.addView(targetButton, weightedParams())
        content.addView(selectors, fixedParams(38))
        content.addView(waveform, fixedParams(28))
        content.addView(result, wrapParams(top = 3))

        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.addView(recordButton, weightedParams())
        controls.addView(stopButton, weightedParams())
        controls.addView(deleteButton, weightedParams())
        content.addView(controls, fixedParams(42, top = 3))
        content.addView(translateButton, fixedParams(38, top = 3))
        content.addView(insertOriginalButton, fixedParams(38, top = 3))
        content.addView(insertTranslatedButton, fixedParams(38, top = 3))
        content.addView(backButton, fixedParams(40, top = 5))
        updateButtons()
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        setBackgroundColor(value.backgroundColor)
        status.setTextColor(value.textColor)
        timer.setTextColor(value.textColor)
        result.setTextColor(value.textColor)
        listOf(
            languageButton,
            targetButton,
            recordButton,
            stopButton,
            deleteButton,
            translateButton,
            insertOriginalButton,
            insertTranslatedButton,
            backButton,
        ).forEach { button ->
            button.setTextColor(value.textColor)
            button.setBackgroundColor(value.keyColor)
        }
        waveform.setColors(value.accentColor, value.backgroundColor)
        invalidate()
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
                status.text = if (event.message.isNotBlank()) event.message else stateLabel(state)
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
                result.visibility = VISIBLE
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
        startedAt = 0L
        pausedAt = 0L
        accumulatedPausedMillis = 0L
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
        stopButton.text = "Stop"
        deleteButton.text = if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED) "Cancel" else "Delete"
        translateButton.text = "Translate result"
        insertOriginalButton.text = "Insert original"
        insertTranslatedButton.text = "Insert translated"

        val recording = state == VoiceSessionState.LISTENING || state == VoiceSessionState.PAUSED
        val hasTranscript = transcript.isNotBlank()
        val hasTranslation = translated.isNotBlank()
        timer.visibility = if (recording) VISIBLE else GONE
        waveform.visibility = if (recording || state == VoiceSessionState.PROCESSING) VISIBLE else GONE
        result.visibility = if (hasTranscript || state == VoiceSessionState.PROCESSING) VISIBLE else GONE
        stopButton.visibility = if (recording) VISIBLE else GONE
        deleteButton.visibility = if (recording || hasTranscript) VISIBLE else GONE
        translateButton.visibility = if (hasTranscript && target != TranslationTarget.NONE) VISIBLE else GONE
        insertOriginalButton.visibility = if (hasTranscript) VISIBLE else GONE
        insertTranslatedButton.visibility = if (hasTranslation) VISIBLE else GONE
        stopButton.isEnabled = recording
        translateButton.isEnabled = hasTranscript && target != TranslationTarget.NONE
        insertOriginalButton.isEnabled = hasTranscript
        insertTranslatedButton.isEnabled = hasTranslation
        recordButton.isEnabled = state != VoiceSessionState.STARTING && state != VoiceSessionState.PROCESSING
        if (state == VoiceSessionState.IDLE) status.text = "Voice typing ready"
        if (!recording) updateTimer()
        requestLayout()
    }

    private fun updateTimer() {
        if (startedAt == 0L) {
            timer.text = ""
            return
        }
        val now = if (state == VoiceSessionState.PAUSED && pausedAt > 0L) pausedAt else System.currentTimeMillis()
        val elapsed = (now - startedAt - accumulatedPausedMillis).coerceAtLeast(0L) / 1000L
        timer.text = String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
    }

    private fun configureButton(button: Button, action: () -> Unit) {
        button.setOnClickListener { action() }
        button.minHeight = dp(34)
        button.minimumHeight = dp(34)
        button.minWidth = 0
        button.minimumWidth = 0
        button.textSize = 13f
        button.setPadding(dp(5), 0, dp(5), 0)
        button.isAllCaps = false
    }

    private fun fixedParams(heightDp: Int, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(heightDp)).apply {
            if (top > 0) topMargin = dp(top)
        }

    private fun wrapParams(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            if (top > 0) topMargin = dp(top)
        }

    private fun weightedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }

    private fun dp(value: Int): Int = (value * density).toInt()

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

private class VoiceWaveformView(context: Context) : View(context) {
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
        val widthPerBar = width / count.toFloat().coerceAtLeast(1f)
        for (index in 0 until count) {
            val wave = if (active) ((index * 17 + System.currentTimeMillis() / 120) % 11) else 2
            val barHeight = (4 + wave * 2) * resources.displayMetrics.density
            val x = widthPerBar * (index + 0.5f)
            canvas.drawRoundRect(
                x - 1.5f * resources.displayMetrics.density,
                barHeight / 2f,
                x + 1.5f * resources.displayMetrics.density,
                height - barHeight / 2f,
                3f,
                3f,
                paint,
            )
        }
        if (active) postInvalidateDelayed(120L)
    }
}
