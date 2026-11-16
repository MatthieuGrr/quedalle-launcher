package dev.mlg.quedalle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.mlg.quedalle.model.TEXTURE_OUTLINE
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
    /** No fill at all, only a thin stroke: drawn by [tileSurface], not by a brush. */
    const val OUTLINE = TEXTURE_OUTLINE

    /** All selectable values, in picker order; null = flat color. */
    val all: List<String?> = listOf(null, OUTLINE, IRIDESCENT, GRADIENT, GLASS)

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

/**
 * Clips to [shape] and paints a tile: its texture over [base], or the
 * plain [flat] color when there is none (callers animate [flat] while a
 * tile is dragged).
 */
@Composable
fun Modifier.tileSurface(texture: String?, base: Color, shape: Shape, flat: Color = base): Modifier {
    if (texture == Textures.OUTLINE) {
        // The tile color becomes the stroke; the theme's own tile color
        // would vanish against the background, so it gets a visible grey.
        val palette = LocalQuedallePalette.current
        val stroke = if (base.alpha < 0.5f || base == palette.card) palette.textMuted else base
        return clip(shape).border(1.dp, stroke, shape)
    }
    val brush = Textures.brush(texture, base)
    return clip(shape).then(if (brush != null) Modifier.background(brush) else Modifier.background(flat))
}
