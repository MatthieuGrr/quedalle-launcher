package dev.mlg.quedalle.model

import java.text.Normalizer

private val diacritics = Regex("\\p{Mn}+")

/** Lowercases and strips diacritics so "Téléphone" matches "telephone". */
fun normalizeForSearch(text: String): String =
    diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

/**
 * Match rank of [label] against [query]: 0 = prefix match, 1 = word-prefix
 * match, 2 = substring match, null = no match. Lower ranks first.
 */
fun searchRank(label: String, query: String): Int? {
    val normLabel = normalizeForSearch(label)
    val normQuery = normalizeForSearch(query)
    if (normQuery.isBlank()) return 2
    return when {
        normLabel.startsWith(normQuery) -> 0
        normLabel.split(' ').any { it.startsWith(normQuery) } -> 1
        normLabel.contains(normQuery) -> 2
        else -> null
    }
}
