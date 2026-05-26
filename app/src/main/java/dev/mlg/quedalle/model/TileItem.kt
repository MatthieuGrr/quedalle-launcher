package dev.mlg.quedalle.model

sealed class TileItem {
    abstract val id: String

    data class App(val info: AppInfo) : TileItem() {
        override val id: String get() = info.packageName
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
