package dev.mlg.quedalle.model

import android.content.ComponentName
import android.os.UserHandle

data class AppInfo(
    val packageName: String,
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle,
    /** Serial number of the profile owning this app; null for the primary profile. */
    val userSerial: Long? = null,
    val isSystemApp: Boolean = false,
    val isPinned: Boolean = false,
    val customLabel: String? = null,
) {
    /** Stable identifier across profiles; equals the bare package name on the primary profile. */
    val key: String get() = appKey(packageName, userSerial)

    val displayLabel: String get() = customLabel ?: label
}

fun appKey(packageName: String, userSerial: Long?): String =
    if (userSerial == null) packageName else "$packageName#$userSerial"
