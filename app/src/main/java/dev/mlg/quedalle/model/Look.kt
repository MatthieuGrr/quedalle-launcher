package dev.mlg.quedalle.model

/** Geometry of the grid, in dp, for the whole launcher. */
data class TileShape(
    val cornerDp: Int = DEFAULT_CORNER,
    val gapDp: Int = DEFAULT_GAP,
    val marginDp: Int = DEFAULT_MARGIN,
) {
    companion object {
        const val MAX_CORNER = 28
        const val MAX_GAP = 16
        const val MAX_MARGIN = 24
        const val DEFAULT_CORNER = 10
        const val DEFAULT_GAP = 8
        const val DEFAULT_MARGIN = 12

        fun of(cornerDp: Int?, gapDp: Int?, marginDp: Int?) = TileShape(
            cornerDp = (cornerDp ?: DEFAULT_CORNER).coerceIn(0, MAX_CORNER),
            gapDp = (gapDp ?: DEFAULT_GAP).coerceIn(0, MAX_GAP),
            marginDp = (marginDp ?: DEFAULT_MARGIN).coerceIn(0, MAX_MARGIN),
        )
    }
}

/**
 * Screen-level look. The background is chosen per theme, since a color
 * that suits the dark theme makes the light one unreadable; null follows
 * the theme.
 */
data class ScreenLook(
    val backgroundDark: Int? = null,
    val backgroundLight: Int? = null,
    val haptics: Boolean = false,
) {
    fun background(dark: Boolean): Int? = if (dark) backgroundDark else backgroundLight
}

/** A ready-made look: sets theme, background, tile style, shape and typography at once. */
data class LookPreset(
    val key: String,
    val theme: ThemeMode,
    /** Background for [theme]; null = the theme's own. */
    val background: Int?,
    val style: TileStyle,
    val shape: TileShape,
    /** The user's text size is kept: it is a readability setting, not a look. */
    val text: TileText,
) {
    companion object {
        const val BLACK = "black"
        const val PAPER = "paper"
        const val OUTLINE = "outline"
        const val MOSAIC = "mosaic"

        val all: List<LookPreset> = listOf(
            // The launcher as it comes: also the way back to the defaults.
            LookPreset(BLACK, ThemeMode.DARK, null, TileStyle(), TileShape(), TileText()),
            LookPreset(
                PAPER, ThemeMode.LIGHT, 0xFFF2EFE9.toInt(),
                TileStyle(background = 0xFFE4DFD3.toInt()),
                TileShape(cornerDp = 3),
                TileText(font = TileFont.SERIF, alignStart = true, vertical = VerticalAnchor.BOTTOM),
            ),
            LookPreset(
                OUTLINE, ThemeMode.DARK, null,
                TileStyle(texture = TEXTURE_OUTLINE),
                TileShape(cornerDp = 0),
                TileText(font = TileFont.MONO, case = TextCase.LOWER),
            ),
            LookPreset(
                MOSAIC, ThemeMode.DARK, null,
                TileStyle(),
                TileShape(cornerDp = 0, gapDp = 2, marginDp = 0),
                TileText(
                    bold = true, case = TextCase.UPPER,
                    alignStart = true, vertical = VerticalAnchor.BOTTOM,
                ),
            ),
        )
    }
}
