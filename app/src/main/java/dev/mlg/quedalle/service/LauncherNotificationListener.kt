package dev.mlg.quedalle.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherNotificationListener : NotificationListenerService() {

    companion object {
        private val _notifiedPackages = MutableStateFlow<Set<String>>(emptySet())
        val notifiedPackages: StateFlow<Set<String>> = _notifiedPackages.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sync()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = sync()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = sync()

    private fun sync() {
        _notifiedPackages.value = try {
            activeNotifications?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
