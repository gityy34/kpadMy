package com.example.aikeyboard.prediction

/** Compact immutable on-device dictionary. Personal vocabulary is intentionally not included. */
class LocalLexicon {
    data class Entry(
        val text: String,
        val language: PredictionLanguage,
        val frequency: Int,
        val variants: Set<String> = emptySet(),
    )

    private val allEntries = listOf(
        e("the", PredictionLanguage.ENGLISH, 10000), e("this", PredictionLanguage.ENGLISH, 8500),
        e("that", PredictionLanguage.ENGLISH, 8200), e("there", PredictionLanguage.ENGLISH, 7800),
        e("hello", PredictionLanguage.ENGLISH, 7600), e("help", PredictionLanguage.ENGLISH, 7200),
        e("helmet", PredictionLanguage.ENGLISH, 3000), e("how", PredictionLanguage.ENGLISH, 7000),
        e("are", PredictionLanguage.ENGLISH, 9000), e("you", PredictionLanguage.ENGLISH, 9800),
        e("good", PredictionLanguage.ENGLISH, 6500), e("morning", PredictionLanguage.ENGLISH, 6000),
        e("night", PredictionLanguage.ENGLISH, 5500), e("job", PredictionLanguage.ENGLISH, 5200),
        e("thank", PredictionLanguage.ENGLISH, 5000), e("thanks", PredictionLanguage.ENGLISH, 6000),
        e("world", PredictionLanguage.ENGLISH, 5000), e("meeting", PredictionLanguage.ENGLISH, 4000),
        e("office", PredictionLanguage.ENGLISH, 3900), e("today", PredictionLanguage.ENGLISH, 4500),
        e("tomorrow", PredictionLanguage.ENGLISH, 3500), e("please", PredictionLanguage.ENGLISH, 4300),
        e("can", PredictionLanguage.ENGLISH, 5000), e("will", PredictionLanguage.ENGLISH, 5000),
        e("is", PredictionLanguage.ENGLISH, 8500), e("my", PredictionLanguage.ENGLISH, 7000),
        e("keyboard", PredictionLanguage.ENGLISH, 2500),
        e("android", PredictionLanguage.ENGLISH, 3300), e("project", PredictionLanguage.ENGLISH, 3200),
        e("people", PredictionLanguage.ENGLISH, 3400), e("because", PredictionLanguage.ENGLISH, 3600),

        e("آپ", PredictionLanguage.URDU, 9500), e("کیسے", PredictionLanguage.URDU, 8200),
        e("ہیں", PredictionLanguage.URDU, 9000), e("کا", PredictionLanguage.URDU, 7000),
        e("کی", PredictionLanguage.URDU, 7000), e("ہوں", PredictionLanguage.URDU, 7600),
        e("نے", PredictionLanguage.URDU, 6500), e("بھی", PredictionLanguage.URDU, 6500),
        e("میں", PredictionLanguage.URDU, 9000), e("گھر", PredictionLanguage.URDU, 6000),
        e("جا", PredictionLanguage.URDU, 5000), e("ہے", PredictionLanguage.URDU, 8500),
        e("آج", PredictionLanguage.URDU, 5000), e("کل", PredictionLanguage.URDU, 5000),

        e("kya", PredictionLanguage.ROMAN_URDU, 8500, setOf("kia", "kyaa")),
        e("hai", PredictionLanguage.ROMAN_URDU, 9000, setOf("hy", "he")),
        e("mein", PredictionLanguage.ROMAN_URDU, 8000, setOf("main", "me")),
        e("kaise", PredictionLanguage.ROMAN_URDU, 7600, setOf("kese")),
        e("mujhe", PredictionLanguage.ROMAN_URDU, 7200, setOf("mjhy")),
        e("acha", PredictionLanguage.ROMAN_URDU, 7000, setOf("accha")),
        e("nahi", PredictionLanguage.ROMAN_URDU, 8200, setOf("nai", "nhi")),
        e("karna", PredictionLanguage.ROMAN_URDU, 6500, setOf("krna")),
        e("theek", PredictionLanguage.ROMAN_URDU, 6200, setOf("thik")),
        e("kal", PredictionLanguage.ROMAN_URDU, 5800), e("aaj", PredictionLanguage.ROMAN_URDU, 5800),
        e("jana", PredictionLanguage.ROMAN_URDU, 5600), e("ghar", PredictionLanguage.ROMAN_URDU, 6000),
        e("meeting", PredictionLanguage.ROMAN_URDU, 3200), e("office", PredictionLanguage.ROMAN_URDU, 3200),
    )

    private val entriesByLanguage = allEntries.groupBy { it.language }
    private val prefixIndex: Map<String, List<Entry>> = buildMap {
        allEntries.forEach { entry ->
            forms(entry).forEach { form ->
                val normalized = formFor(entry.language, form)
                for (length in 1..minOf(4, normalized.length)) {
                    val key = "${entry.language}:" + normalized.take(length)
                    put(key, (get(key).orEmpty() + entry).distinctBy { it.text })
                }
            }
        }
    }

    fun entries(language: PredictionLanguage): List<Entry> = entriesByLanguage[language].orEmpty()

    fun prefixEntries(language: PredictionLanguage, query: String): List<Entry> {
        if (query.isEmpty()) return entries(language)
        val normalized = formFor(language, query)
        return prefixIndex["$language:" + normalized.take(4)].orEmpty()
            .filter { entry -> forms(entry).any { formFor(language, it).startsWith(normalized) } }
    }

    fun forms(entry: Entry): Set<String> = setOf(entry.text) + entry.variants

    fun nextWords(context: List<String>): List<String> {
        val key = context.takeLast(2).joinToString(" ").lowercase()
        return when (key) {
            "how are" -> listOf("you")
            "thank" -> listOf("you")
            "good" -> listOf("morning", "night", "job")
            "آپ" -> listOf("کیسے", "کا", "کی")
            "آپ کیسے" -> listOf("ہیں")
            "میں" -> listOf("ہوں", "نے", "بھی")
            "میں گھر" -> listOf("جا")
            "meeting kal" -> listOf("hai")
            "aaj office" -> listOf("jana", "hai")
            else -> emptyList()
        }
    }

    private fun formFor(language: PredictionLanguage, value: String): String =
        if (language == PredictionLanguage.ROMAN_URDU) RomanUrdu.normalize(value) else value.lowercase()

    private fun e(
        text: String,
        language: PredictionLanguage,
        frequency: Int,
        variants: Set<String> = emptySet(),
    ) = Entry(text, language, frequency, variants)
}
