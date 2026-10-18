package com.example.aikeyboard.learning

import com.example.aikeyboard.prediction.PredictionLanguage

/** A deliberately small record: no sentences, documents, or raw typing history are stored. */
data class PersonalWord(
    val normalizedWord: String,
    val displayText: String,
    val language: PredictionLanguage,
    val frequency: Int,
    val confidence: Int,
    val firstSeenMillis: Long,
    val lastUsedMillis: Long,
    val contextKey: String? = null,
)

fun interface PersonalVocabularySource {
    fun snapshot(): List<PersonalWord>
}

interface PersonalVocabularyRepository : PersonalVocabularySource {
    fun learn(
        word: String,
        language: PredictionLanguage,
        contextKey: String? = null,
        explicitlyAccepted: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun forget(word: PersonalWord)
    fun clear()
    fun close()
}

object EmptyPersonalVocabulary : PersonalVocabularySource {
    override fun snapshot(): List<PersonalWord> = emptyList()
}
