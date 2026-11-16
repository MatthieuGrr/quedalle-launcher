package dev.mlg.quedalle.model

/** What a text-only information tile displays. */
enum class InfoKind(val key: String) {
    CLOCK("clock"),
    DATE("date"),
    BATTERY("battery"),
    ALARM("alarm");

    companion object {
        fun fromKey(key: String?): InfoKind? = entries.firstOrNull { it.key == key }
    }
}
