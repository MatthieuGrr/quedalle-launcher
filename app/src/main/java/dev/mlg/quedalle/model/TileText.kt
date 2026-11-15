package dev.mlg.quedalle.model

/** How tile labels are written, for the whole launcher. */
data class TileText(
    val sizeSp: Int = DEFAULT_SIZE_SP,
    val alignStart: Boolean = false,
    val bold: Boolean = false,
) {
    companion object {
        const val MIN_SIZE_SP = 9
        const val MAX_SIZE_SP = 22
        const val DEFAULT_SIZE_SP = 11

        const val ALIGN_CENTER = "center"
        const val ALIGN_START = "start"

        fun of(sizeSp: Int?, align: String?, bold: Boolean?) = TileText(
            sizeSp = (sizeSp ?: DEFAULT_SIZE_SP).coerceIn(MIN_SIZE_SP, MAX_SIZE_SP),
            alignStart = align == ALIGN_START,
            bold = bold ?: false,
        )
    }

    val alignKey: String get() = if (alignStart) ALIGN_START else ALIGN_CENTER
}
