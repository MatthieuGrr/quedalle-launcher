package dev.mlg.quedalle.model

/** Visual customization of a tile. Defaults mean "follow the theme". */
data class TileStyle(
    /** ARGB background; the TileAppColor sentinel means "theme card color". */
    val background: Int = 0xFF141414.toInt(),
    /** ARGB text color; null = automatic contrast against the background. */
    val textColor: Int? = null,
    /** Texture id (see Textures); null = flat color. */
    val texture: String? = null,
)

sealed class TileItem {
    abstract val id: String

    data class App(
        val info: AppInfo,
        val style: TileStyle = TileStyle(),
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
