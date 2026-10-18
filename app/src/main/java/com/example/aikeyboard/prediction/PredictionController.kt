package com.example.aikeyboard.prediction

import com.example.aikeyboard.core.CompositionSnapshot
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs prediction away from the input thread. Every request supersedes the previous request;
 * both cancellation and the generation check protect the UI from stale results.
 */
class PredictionController(
    private val engine: PredictionEngine = LocalPredictionEngine(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyboardPrediction").apply { isDaemon = true }
    },
) : Closeable {

    private val requestCounter = AtomicLong(0L)

    private val resultGate = Any()

    @Volatile
    private var pendingRequest: Future<*>? = null

    @Volatile
    private var latestId: Long = 0L

    @Synchronized
    fun request(
        snapshot: CompositionSnapshot,
        onResult: (PredictionResult) -> Unit,
    ): Long {
        val requestId = requestCounter.incrementAndGet()
        synchronized(resultGate) {
            latestId = requestId
            pendingRequest?.cancel(false)
            pendingRequest = executor.submit {
                val candidates = runCatching { engine.predict(snapshot) }.getOrDefault(emptyList())
                synchronized(resultGate) {
                    if (latestId == requestId) {
                        // Acceptance and callback are one atomic decision relative to a newer
                        // request, so an old result cannot slip through during request handoff.
                        onResult(PredictionResult(requestId, snapshot, candidates))
                    }
                }
            }
        }
        return requestId
    }

    fun latestRequestId(): Long = latestId

    @Synchronized
    fun clear() {
        synchronized(resultGate) {
            latestId = requestCounter.incrementAndGet()
            pendingRequest?.cancel(false)
            pendingRequest = null
        }
    }

    @Synchronized
    override fun close() {
        synchronized(resultGate) {
            latestId = requestCounter.incrementAndGet()
            pendingRequest?.cancel(true)
            pendingRequest = null
        }
        executor.shutdownNow()
    }
}
