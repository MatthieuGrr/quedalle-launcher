package dev.mlg.quedalle

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Exists for one thing: turning the screen off for the "lock screen"
 * gesture, which Android only allows through an accessibility service.
 * It is off unless the user enables it, asks for no events and cannot
 * read window content.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** True once the user has turned the service on in the system settings. */
        val isEnabled: Boolean get() = instance != null

        /** @return false when the service is not enabled (or the device is too old). */
        fun lock(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
