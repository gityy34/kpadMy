package com.example.aikeyboard.voice

import java.util.concurrent.Executors

/** Offline starter translation provider. It is deliberately bounded and has no network access. */
class LocalTranslationProvider : TranslationProvider {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyboardTranslation").apply { isDaemon = true }
    }

    override fun translate(text: String, target: TranslationTarget, callback: (Result<String>) -> Unit) {
        executor.execute {
            if (target == TranslationTarget.NONE) {
                callback(Result.success(text))
                return@execute
            }
            val normalized = text.trim().lowercase()
            val translated = when (target) {
                TranslationTarget.ENGLISH -> URDU_TO_ENGLISH[normalized]
                TranslationTarget.URDU -> ENGLISH_TO_URDU[normalized]
                TranslationTarget.NONE -> text
            }
            if (translated != null) callback(Result.success(translated))
            else callback(Result.failure(IllegalStateException("Offline translation phrase is unavailable")))
        }
    }

    override fun close() { executor.shutdownNow() }

    companion object {
        private val URDU_TO_ENGLISH = mapOf(
            "آپ کیسے ہیں؟" to "How are you?",
            "آپ کیسے ہیں" to "How are you",
            "میں کل آپ کو فون کروں گا۔" to "I will call you tomorrow.",
            "میٹنگ دس بجے شروع ہوگی۔" to "The meeting will start at ten.",
        )
        private val ENGLISH_TO_URDU = mapOf(
            "hello, how are you?" to "ہیلو، آپ کیسے ہیں؟",
            "how are you?" to "آپ کیسے ہیں؟",
            "i will call you tomorrow morning." to "میں آپ کو کل صبح فون کروں گا۔",
            "the meeting will start at ten." to "میٹنگ دس بجے شروع ہوگی۔",
        )
    }
}
