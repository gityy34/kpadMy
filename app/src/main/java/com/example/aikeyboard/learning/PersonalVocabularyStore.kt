package com.example.aikeyboard.learning

import android.content.Context
import com.example.aikeyboard.prediction.PredictionLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Private bounded vocabulary cache. Disk access is confined to a single background executor;
 * prediction reads an immutable in-memory snapshot only.
 */
class PersonalVocabularyStore(
    context: Context,
    private val enabled: () -> Boolean = { true },
) : PersonalVocabularyRepository {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "personal-vocabulary").apply { isDaemon = true }
    }
    private val lock = Any()
    private val entries = LinkedHashMap<String, PersonalWord>()

    @Volatile
    private var cachedSnapshot: List<PersonalWord> = emptyList()
    private var observedClearGeneration = clearGeneration.get()

    init {
        executor.execute { loadFromDisk() }
    }

    override fun snapshot(): List<PersonalWord> {
        syncExternalClear()
        return if (enabled()) cachedSnapshot else emptyList()
    }

    override fun learn(
        word: String,
        language: PredictionLanguage,
        contextKey: String?,
        explicitlyAccepted: Boolean,
        nowMillis: Long,
    ) {
        if (!enabled() || !PersonalLearningPolicy.isUsefulWord(word)) return
        val normalized = PersonalLearningPolicy.normalize(word, language)
        if (normalized.isEmpty()) return

        val nextSnapshot: List<PersonalWord>
        synchronized(lock) {
            syncExternalClearLocked()
            val key = key(language, normalized)
            val previous = entries[key]
            val next = if (previous == null) {
                PersonalWord(
                    normalizedWord = normalized,
                    displayText = word.trim(),
                    language = language,
                    frequency = 1,
                    confidence = if (explicitlyAccepted) 3 else 1,
                    firstSeenMillis = nowMillis,
                    lastUsedMillis = nowMillis,
                    contextKey = contextKey,
                )
            } else {
                previous.copy(
                    // Keep the spelling the user most recently and intentionally committed.
                    displayText = word.trim(),
                    frequency = (previous.frequency + 1).coerceAtMost(MAX_FREQUENCY),
                    confidence = (previous.confidence + if (explicitlyAccepted) 2 else 1)
                        .coerceAtMost(MAX_CONFIDENCE),
                    lastUsedMillis = nowMillis,
                    contextKey = contextKey ?: previous.contextKey,
                )
            }
            entries[key] = next
            trimToLimitLocked(nowMillis)
            publishLocked()
            nextSnapshot = cachedSnapshot
        }
        queuePersist(nextSnapshot)
    }

    override fun forget(word: PersonalWord) {
        synchronized(lock) {
            syncExternalClearLocked()
            entries.remove(key(word.language, word.normalizedWord))
            publishLocked()
        }
        queuePersist(cachedSnapshot)
    }

    override fun clear() {
        synchronized(lock) {
            entries.clear()
            publishLocked()
        }
        clearPersisted(preferences)
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun loadFromDisk() {
        val raw = preferences.getString(KEY_WORDS, null) ?: return
        val loaded = runCatching { decode(raw) }.getOrDefault(emptyList())
        synchronized(lock) {
            syncExternalClearLocked()
            for (word in loaded) {
                val key = key(word.language, word.normalizedWord)
                val current = entries[key]
                if (current == null || word.lastUsedMillis > current.lastUsedMillis) {
                    entries[key] = word
                }
            }
            trimToLimitLocked(System.currentTimeMillis())
            publishLocked()
        }
    }

    private fun queuePersist(snapshot: List<PersonalWord>) {
        executor.execute {
            if (clearGeneration.get() != observedClearGeneration) return@execute
            preferences.edit().putString(KEY_WORDS, encode(snapshot)).apply()
        }
    }

    private fun publishLocked() {
        cachedSnapshot = entries.values
            .sortedWith(compareByDescending<PersonalWord> { it.confidence }
                .thenByDescending { it.frequency }
                .thenByDescending { it.lastUsedMillis })
            .toList()
    }

    private fun trimToLimitLocked(nowMillis: Long) {
        if (entries.size <= MAX_ENTRIES) return
        val removeCount = entries.size - MAX_ENTRIES
        val victims = entries.values
            .sortedBy { evictionScore(it, nowMillis) }
            .take(removeCount)
        victims.forEach { entries.remove(key(it.language, it.normalizedWord)) }
    }

    private fun evictionScore(word: PersonalWord, nowMillis: Long): Long {
        val ageDays = max(0L, nowMillis - word.lastUsedMillis) / MILLIS_PER_DAY
        return word.confidence * 10_000L + word.frequency * 100L - ageDays
    }

    private fun syncExternalClear() {
        synchronized(lock) { syncExternalClearLocked() }
    }

    private fun syncExternalClearLocked() {
        val generation = clearGeneration.get()
        if (generation == observedClearGeneration) return
        entries.clear()
        cachedSnapshot = emptyList()
        observedClearGeneration = generation
    }

    private fun key(language: PredictionLanguage, normalized: String): String =
        "${language.name}|$normalized"

    private fun encode(words: List<PersonalWord>): String {
        val array = JSONArray()
        words.forEach { word ->
            array.put(JSONObject().apply {
                put("n", word.normalizedWord)
                put("d", word.displayText)
                put("l", word.language.name)
                put("f", word.frequency)
                put("c", word.confidence)
                put("first", word.firstSeenMillis)
                put("last", word.lastUsedMillis)
                word.contextKey?.let { put("x", it) }
            })
        }
        return array.toString()
    }

    private fun decode(raw: String): List<PersonalWord> {
        val array = JSONArray(raw)
        val result = ArrayList<PersonalWord>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val language = runCatching {
                PredictionLanguage.valueOf(item.optString("l"))
            }.getOrNull() ?: continue
            val display = item.optString("d")
            val normalized = item.optString("n")
            if (!PersonalLearningPolicy.isUsefulWord(display) || normalized.isEmpty()) continue
            result += PersonalWord(
                normalizedWord = normalized,
                displayText = display,
                language = language,
                frequency = item.optInt("f", 1).coerceIn(1, MAX_FREQUENCY),
                confidence = item.optInt("c", 1).coerceIn(1, MAX_CONFIDENCE),
                firstSeenMillis = item.optLong("first", 0L),
                lastUsedMillis = item.optLong("last", 0L),
                contextKey = item.optString("x").takeIf { it.isNotEmpty() },
            )
        }
        return result
    }

    companion object {
        private const val PREFERENCES_NAME = "personal_vocabulary"
        private const val KEY_WORDS = "bounded_words"
        private const val MAX_ENTRIES = 512
        private const val MAX_FREQUENCY = 1_000_000
        private const val MAX_CONFIDENCE = 100
        private const val MILLIS_PER_DAY = 86_400_000L
        private val clearGeneration = AtomicLong(0L)

        fun clearPersisted(context: Context) {
            clearPersisted(
                context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }

        private fun clearPersisted(preferences: android.content.SharedPreferences) {
            clearGeneration.incrementAndGet()
            preferences.edit().remove(KEY_WORDS).apply()
        }
    }
}
