package dev.mlg.quedalle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mlg.quedalle.R
import dev.mlg.quedalle.model.TileStyle
import dev.mlg.quedalle.model.argbToHsl
import dev.mlg.quedalle.model.hslToArgb
import dev.mlg.quedalle.ui.theme.LocalQuedallePalette
import dev.mlg.quedalle.ui.theme.QuedalleColors
import dev.mlg.quedalle.ui.theme.Textures
import dev.mlg.quedalle.ui.theme.resolveTileColor
import dev.mlg.quedalle.ui.theme.resolveTileTextColor

private enum class CustomSlot { BACKGROUND, TEXT }

private val TextSwatches: List<Int> = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    0xFFE57373.toInt(), 0xFFFFB74D.toInt(), 0xFFFFD54F.toInt(),
    0xFF81C784.toInt(), 0xFF64B5F6.toInt(), 0xFFBA68C8.toInt(),
)

/**
 * The shared background / text / texture controls, applied live through
 * the callbacks. Values are *effective*: [background] uses the
 * TileAppColor sentinel for "theme", [textColor] null = auto,
 * [texture] null = flat.
 */
@Composable
fun AppearanceControls(
    background: Int,
    textColor: Int?,
    texture: String?,
    showTextSection: Boolean,
    onBackground: (Int) -> Unit,
    onTextColor: (Int?) -> Unit,
    onTexture: (String?) -> Unit,
) {
    var customSlot by remember { mutableStateOf<CustomSlot?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.appearance_background))
        ColorPicker(background) { onBackground(it); customSlot = null }
        CustomColorToggle(
            expanded = customSlot == CustomSlot.BACKGROUND,
            onToggle = {
                customSlot = if (customSlot == CustomSlot.BACKGROUND) null else CustomSlot.BACKGROUND
            },
        )
        if (customSlot == CustomSlot.BACKGROUND) {
            HslSliders(initial = background, onColor = onBackground)
        }

        if (showTextSection) {
            SectionLabel(stringResource(R.string.appearance_text))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutoSwatch(selected = textColor == null) { onTextColor(null); customSlot = null }
                TextSwatches.take(4).forEach { c ->
                    ColorSwatch(c, textColor ?: 1, onSelect = { onTextColor(it); customSlot = null })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextSwatches.drop(4).forEach { c ->
                    ColorSwatch(c, textColor ?: 1, onSelect = { onTextColor(it); customSlot = null })
                }
            }
            CustomColorToggle(
                expanded = customSlot == CustomSlot.TEXT,
                onToggle = {
                    customSlot = if (customSlot == CustomSlot.TEXT) null else CustomSlot.TEXT
                },
            )
            if (customSlot == CustomSlot.TEXT) {
                HslSliders(initial = textColor ?: 0xFFEDEDED.toInt(), onColor = onTextColor)
            }
        }

        SectionLabel(stringResource(R.string.appearance_texture))
        TextureRow(texture, resolveTileColor(background), onTexture)
    }
}

/** A sample tile rendered with the given style (used by Settings). */
@Composable
fun TilePreview(label: String?, background: Int, textColor: Int?, texture: String?) {
    val base = resolveTileColor(background)
    val brush = Textures.brush(texture, base)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(base)),
            contentAlignment = Alignment.Center,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    color = resolveTileTextColor(TileStyle(background, textColor, texture), base),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = LocalQuedallePalette.current.textMuted, fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorSwatch(QuedalleColors.TileTransparent, selected, onSelect, transparent = true)
            ColorSwatch(QuedalleColors.TileAppColor, selected, onSelect)
        }
        QuedalleColors.TilePresets.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { color -> ColorSwatch(color, selected, onSelect) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Int, onSelect: (Int) -> Unit, transparent: Boolean = false) {
    val palette = LocalQuedallePalette.current
    val shape = RoundedCornerShape(8.dp)
    val fill = resolveTileColor(color)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .then(
                if (transparent) Modifier.drawBehind {
                    val cell = size.width / 4
                    for (row in 0..3) for (col in 0..3) drawRect(
                        color     = if ((row + col) % 2 == 0) palette.checkerA else palette.checkerB,
                        topLeft   = Offset(col * cell, row * cell),
                        size      = Size(cell, cell),
                    )
                } else Modifier.background(fill)
            )
            .then(
                if (color == selected)
                    Modifier.border(2.dp, palette.textStrong, shape)
                else Modifier
            )
            .clickable { onSelect(color) }
    )
}

@Composable
private fun AutoSwatch(selected: Boolean, onSelect: () -> Unit) {
    val palette = LocalQuedallePalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(palette.card)
            .then(if (selected) Modifier.border(2.dp, palette.textStrong, shape) else Modifier)
            .clickable { onSelect() },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.color_auto), color = palette.textPrimary, fontSize = 9.sp)
    }
}

@Composable
private fun TextureRow(selected: String?, base: Color, onSelect: (String?) -> Unit) {
    val palette = LocalQuedallePalette.current
    val shape = RoundedCornerShape(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Textures.all.forEach { tex ->
            val brush = Textures.brush(tex, base)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(shape)
                        .then(if (brush != null) Modifier.background(brush) else Modifier.background(base))
                        .then(if (tex == selected) Modifier.border(2.dp, palette.textStrong, shape) else Modifier)
                        .clickable { onSelect(tex) },
                )
                Text(
                    stringResource(textureLabelRes(tex)),
                    color = if (tex == selected) palette.textPrimary else palette.textMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun textureLabelRes(texture: String?): Int = when (texture) {
    Textures.IRIDESCENT -> R.string.texture_iridescent
    Textures.GRADIENT   -> R.string.texture_gradient
    Textures.GLASS      -> R.string.texture_glass
    else                -> R.string.texture_none
}

@Composable
private fun CustomColorToggle(expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            stringResource(R.string.color_custom),
            fontSize = 12.sp,
            fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun HslSliders(initial: Int, onColor: (Int) -> Unit) {
    var hsl by remember { mutableStateOf(argbToHsl(initial)) }
    val (h, s, l) = hsl
    Column {
        HslSliderRow("H", h, 0f..360f) { hsl = Triple(it, s, l); onColor(hslToArgb(it, s, l)) }
        HslSliderRow("S", s, 0f..1f)   { hsl = Triple(h, it, l); onColor(hslToArgb(h, it, l)) }
        HslSliderRow("L", l, 0f..1f)   { hsl = Triple(h, s, it); onColor(hslToArgb(h, s, it)) }
    }
}

@Composable
private fun HslSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalQuedallePalette.current.textMuted, fontSize = 11.sp,
            modifier = Modifier.padding(end = 8.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(28.dp),
        )
    }
}
