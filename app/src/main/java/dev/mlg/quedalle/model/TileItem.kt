package dev.mlg.quedalle.model

/** Explicit "automatic text color" override (alpha 0 — never a real color). */
const val TEXT_COLOR_AUTO = 1

/** Explicit "flat, no texture" override. */
const val TEXTURE_NONE = "none"

/**
 * Visual customization of a tile.
 *
 * As an *override* (persisted per tile): null fields inherit the global
 * style; [TEXT_COLOR_AUTO] / [TEXTURE_NONE] force auto/flat.
 * As an *effective* style (after [mergeTileStyle]): background null means
 * "theme card color", textColor null means automatic contrast, texture
 * null means flat.
 */
data class TileStyle(
    val background: Int? = null,
    val textColor: Int? = null,
    val texture: String? = null,
)

/** Resolves a per-tile [override] against the [global] default style. */
fun mergeTileStyle(override: TileStyle, global: TileStyle): TileStyle = TileStyle(
    background = override.background ?: global.background,
    textColor = when (override.textColor) {
        null -> global.textColor
        TEXT_COLOR_AUTO -> null
        else -> override.textColor
    },
    texture = when (override.texture) {
        null -> global.texture
        TEXTURE_NONE -> null
        else -> override.texture
    },
)

sealed class TileItem {
    abstract val id: String

    data class App(
        val info: AppInfo,
        /** Effective style (override merged with the global style). */
        val style: TileStyle = TileStyle(),
        /** Raw per-tile override, persisted as-is on reorder. */
        val override: TileStyle = TileStyle(),
    ) : TileItem() {
        override val id: String get() = info.key
    }

    data class Spacer(
        override val id: String,
        val color: Int,
        val texture: String? = null,
    ) : TileItem()

    data class Divider(
        override val id: String,
        val color: Int,
    ) : TileItem()
}

fun List<TileItem>.fullRowFlags(): List<Boolean> = map { it is TileItem.Divider }
