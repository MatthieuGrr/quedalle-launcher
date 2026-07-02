package dev.mlg.quedalle.model

sealed class TileItem {
    abstract val id: String

    data class App(val info: AppInfo) : TileItem() {
        override val id: String get() = info.key
    }

    data class Spacer(
        override val id: String,
        val color: Int,
    ) : TileItem()

    data class Divider(
        override val id: String,
        val color: Int,
    ) : TileItem()
}

fun List<TileItem>.fullRowFlags(): List<Boolean> = map { it is TileItem.Divider }
