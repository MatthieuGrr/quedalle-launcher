package dev.mlg.quedalle.model

enum class TileFont(val key: String) {
    SYSTEM("system"), SERIF("serif"), MONO("mono");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class TextCase(val key: String) {
    NONE("none"), UPPER("upper"), LOWER("lower");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Where the label sits vertically in its tile. */
enum class VerticalAnchor(val key: String) {
    TOP("top"), CENTER("center"), BOTTOM("bottom");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: CENTER
    }
}

/** How tile labels are written, for the whole launcher. */
data class TileText(
    val sizeSp: Int = DEFAULT_SIZE_SP,
    val alignStart: Boolean = false,
    val bold: Boolean = false,
    val font: TileFont = TileFont.SYSTEM,
    val case: TextCase = TextCase.NONE,
    val vertical: VerticalAnchor = VerticalAnchor.CENTER,
) {
    companion object {
        const val MIN_SIZE_SP = 9
        const val MAX_SIZE_SP = 22
        const val DEFAULT_SIZE_SP = 11

        const val ALIGN_CENTER = "center"
        const val ALIGN_START = "start"

        /** Builds from stored values; anything missing or unknown falls back to the default. */
        fun of(
            sizeSp: Int?, align: String?, bold: Boolean?,
            font: String? = null, case: String? = null, vertical: String? = null,
        ) = TileText(
            sizeSp = (sizeSp ?: DEFAULT_SIZE_SP).coerceIn(MIN_SIZE_SP, MAX_SIZE_SP),
            alignStart = align == ALIGN_START,
            bold = bold ?: false,
            font = TileFont.fromKey(font),
            case = TextCase.fromKey(case),
            vertical = VerticalAnchor.fromKey(vertical),
        )
    }

    val alignKey: String get() = if (alignStart) ALIGN_START else ALIGN_CENTER

    /** [label] as it is drawn on a tile. Stored names are never altered. */
    fun display(label: String): String = when (case) {
        TextCase.NONE -> label
        TextCase.UPPER -> label.uppercase()
        TextCase.LOWER -> label.lowercase()
    }
}
