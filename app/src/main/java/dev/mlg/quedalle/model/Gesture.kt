package dev.mlg.quedalle.model

/** Home-screen gestures the user can bind to a [GestureAction]. */
enum class Gesture(val key: String) {
    SWIPE_DOWN("swipe_down"),
    SWIPE_UP("swipe_up"),
    DOUBLE_TAP("double_tap"),
}

sealed interface GestureAction {
    data object None : GestureAction
    data object Notifications : GestureAction
    data object QuickSettings : GestureAction
    data object Search : GestureAction
    data object NextProfile : GestureAction
    data object PrivateSpace : GestureAction
    /** Launches the app identified by its [AppInfo.key]. */
    data class LaunchApp(val appKey: String) : GestureAction

    /** Stable text form, used in preferences and backups. */
    fun encode(): String = when (this) {
        None -> "none"
        Notifications -> "notifications"
        QuickSettings -> "quick_settings"
        Search -> "search"
        NextProfile -> "next_profile"
        PrivateSpace -> "private_space"
        is LaunchApp -> APP_PREFIX + appKey
    }

    companion object {
        private const val APP_PREFIX = "app:"

        /** Unknown values (e.g. from a newer version) decode to [None]. */
        fun decode(raw: String?): GestureAction = when {
            raw == null -> None
            raw.startsWith(APP_PREFIX) && raw.length > APP_PREFIX.length ->
                LaunchApp(raw.removePrefix(APP_PREFIX))
            else -> when (raw) {
                "notifications" -> Notifications
                "quick_settings" -> QuickSettings
                "search" -> Search
                "next_profile" -> NextProfile
                "private_space" -> PrivateSpace
                else -> None
            }
        }
    }
}
