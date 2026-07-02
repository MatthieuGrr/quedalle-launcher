package dev.mlg.quedalle.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.mlg.quedalle.model.blendArgb
import dev.mlg.quedalle.model.darken
import dev.mlg.quedalle.model.lighten

/**
 * Static tile textures, rendered as Compose brushes — no assets, no
 * animation, no battery cost. Ids are persisted in TileDef.texture.
 */
object Textures {
    const val IRIDESCENT = "iris"
    const val GRADIENT = "gradient"
    const val GLASS = "glass"

    /** All selectable values, in picker order; null = flat color. */
    val all: List<String?> = listOf(null, IRIDESCENT, GRADIENT, GLASS)

    /** Soft pastel stops for the iridescent effect (independent of base color). */
    private val iridescentStops = listOf(
        Color(0xFFE8B5D8), Color(0xFFB5C7E8), Color(0xFFB5E8D5),
        Color(0xFFE8E3B5), Color(0xFFE8C0B5),
    )

    /**
     * Brush for [texture] over [base]; null when the tile is a flat color.
     * All brushes are diagonal or vertical gradients — cheap and static.
     */
    fun brush(texture: String?, base: Color): Brush? = when (texture) {
        IRIDESCENT -> Brush.linearGradient(
            colors = iridescentStops,
            start = Offset.Zero,
            end = Offset.Infinite,
        )
        GRADIENT -> Brush.linearGradient(
            colors = listOf(
                Color(lighten(base.toArgb(), 0.12f)),
                base,
                Color(darken(base.toArgb(), 0.35f)),
            ),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
        GLASS -> Brush.verticalGradient(
            colors = listOf(
                Color(blendArgb(base.toArgb(), 0xFFFFFFFF.toInt(), 0.28f)),
                Color(blendArgb(base.toArgb(), 0xFFFFFFFF.toInt(), 0.08f)),
                base,
                Color(darken(base.toArgb(), 0.10f)),
            ),
        )
        else -> null
    }

    /**
     * A single color that stands for the texture's overall brightness,
     * used for automatic text contrast.
     */
    fun representativeColor(texture: String?, base: Int): Int = when (texture) {
        IRIDESCENT -> {
            var argb = iridescentStops.first().toArgb()
            for (stop in iridescentStops.drop(1)) argb = blendArgb(argb, stop.toArgb(), 0.5f)
            argb
        }
        GLASS -> blendArgb(base, 0xFFFFFFFF.toInt(), 0.12f)
        else -> base
    }
}
