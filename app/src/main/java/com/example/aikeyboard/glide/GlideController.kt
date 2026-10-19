package com.example.aikeyboard.glide

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Latest-gesture-wins boundary; a late recognition result can never overwrite a newer gesture. */
class GlideController(
    private val engine: GlideEngine,
    private val geometryProvider: () -> List<GlideKeyGeometry>,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyboardGlide").apply { isDaemon = true }
    },
) : Closeable {

    private val sessionCounter = AtomicLong(0L)

    fun recognize(
        gesture: GlideGesture,
        onResult: (GlideRecognition) -> Unit,
    ) {
        val session = sessionCounter.incrementAndGet()
        executor.execute {
            val result = runCatching { engine.recognize(gesture, geometryProvider()) }
                .getOrDefault(GlideRecognition(emptyList(), 0, ""))
            if (sessionCounter.get() == session) onResult(result)
        }
    }

    fun cancel() {
        sessionCounter.incrementAndGet()
    }

    override fun close() {
        sessionCounter.incrementAndGet()
        executor.shutdownNow()
    }
}
