package dev.mlg.quedalle.model

private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * Turns what the user typed into an openable URI: anything that already
 * carries a scheme ("tel:", "mailto:", "https://"…) is kept, a bare
 * address gets "https://". Returns null when there is nothing to open.
 */
fun normalizeLink(input: String): String? {
    val text = input.trim()
    return when {
        text.isEmpty() || text.any { it.isWhitespace() } -> null
        scheme.containsMatchIn(text) -> text
        else -> "https://$text"
    }
}
