package dev.mlg.quedalle.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val isPinned: Boolean = false,
    val hasNotification: Boolean = false,
)
