package com.example.aikeyboard.glide

import com.example.aikeyboard.core.KeySpec
import com.example.aikeyboard.core.KeyboardLanguage
import com.example.aikeyboard.prediction.SuggestionCandidate

data class GlidePoint(val x: Float, val y: Float)

/** Live geometry supplied by the canvas keyboard whenever its layout or size changes. */
data class GlideKeyGeometry(
    val key: KeySpec,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class GlideGesture(
    val path: List<GlidePoint>,
    val activeLanguage: KeyboardLanguage,
    val provisionalText: String,
)

data class GlideRecognition(
    val candidates: List<SuggestionCandidate>,
    val confidence: Int,
    val extractedPath: String,
)

/** Reusable bounded path buffer so ACTION_MOVE does not allocate a point object each time. */
class GlidePathBuffer(private val capacity: Int = 256) {
    private val xValues = FloatArray(capacity)
    private val yValues = FloatArray(capacity)
    var size: Int = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(x: Float, y: Float) {
        if (size == capacity) {
            // Preserve the start and the most recent path while keeping memory bounded.
            val keepFrom = capacity / 3
            val keepCount = capacity - keepFrom
            xValues.copyInto(xValues, 0, keepFrom, capacity)
            yValues.copyInto(yValues, 0, keepFrom, capacity)
            size = keepCount
        }
        xValues[size] = x
        yValues[size] = y
        size++
    }

    fun pointAt(index: Int): GlidePoint = GlidePoint(xValues[index], yValues[index])

    fun toList(): List<GlidePoint> = List(size) { index -> pointAt(index) }
}
