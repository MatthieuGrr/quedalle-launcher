package dev.mlg.quedalle.model

/** Explicit "automatic text color" override (alpha 0 — never a real color). */
const val TEXT_COLOR_AUTO = 1

/** Explicit "flat, no texture" override. */
const val TEXTURE_NONE = "none"

/** Texture id of the "outline" look: a thin stroke, no fill. */
const val TEXTURE_OUTLINE = "outline"

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

/** What a shortcut tile opens. */
sealed interface ShortcutTarget {
    /** A shortcut published by an app (a conversation, a route…). */
    data class AppShortcut(
        val packageName: String,
        val userSerial: Long?,
        val shortcutId: String,
    ) : ShortcutTarget

    /** Anything openable by URI: a web page, a contact, "tel:"… */
    data class Link(val uri: String) : ShortcutTarget
}

sealed class TileItem {
    abstract val id: String

    /** Tiles drawn as a text card that follows the global tile style. */
    sealed class Styled : TileItem() {
        /** Effective style (override merged with the global style). */
        abstract val style: TileStyle
        /** Raw per-tile override, as persisted. */
        abstract val override: TileStyle
    }

    data class App(
        val info: AppInfo,
        override val style: TileStyle = TileStyle(),
        override val override: TileStyle = TileStyle(),
    ) : Styled() {
        override val id: String get() = info.key
    }

    data class Shortcut(
        override val id: String,
        val label: String,
        val target: ShortcutTarget,
        override val style: TileStyle = TileStyle(),
        override val override: TileStyle = TileStyle(),
    ) : Styled()

    data class Info(
        override val id: String,
        val kind: InfoKind,
        override val style: TileStyle = TileStyle(),
        override val override: TileStyle = TileStyle(),
    ) : Styled()

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
