package dev.mlg.quedalle.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dev.mlg.quedalle.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

private const val TAG = "AppRepository"

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val packageEvents: Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        send(Unit)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    /** Launchable apps across all profiles, refreshed on package events. */
    val apps: Flow<List<AppInfo>> = merge(packageEvents, refreshRequests)
        .conflate()
        .map { loadApps() }
        .flowOn(Dispatchers.IO)

    /** Forces a reload, e.g. when the launcher comes back to the foreground. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun loadApps(): List<AppInfo> {
        val myUser = Process.myUserHandle()
        val pm = context.packageManager
        val profiles = try {
            launcherApps.profiles
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list profiles", e)
            listOf(myUser)
        }
        // Per-profile try/catch: a locked work profile must not wipe the whole list.
        return profiles.flatMap { user ->
            try {
                launcherApps.getActivityList(null, user)
                    .distinctBy { it.applicationInfo.packageName }
                    .map { activity ->
                        val label = activity.label.toString()
                        AppInfo(
                            packageName = activity.applicationInfo.packageName,
                            label = if (user == myUser) label
                                    else pm.getUserBadgedLabel(label, user).toString(),
                            componentName = activity.componentName,
                            user = user,
                            userSerial = if (user == myUser) null
                                         else userManager.getSerialNumberForUser(user),
                            isSystemApp = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load apps for profile $user", e)
                emptyList()
            }
        }
    }

    fun launchApp(app: AppInfo) {
        try {
            launcherApps.startMainActivity(app.componentName, app.user, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.packageName}", e)
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app info for ${app.packageName}", e)
        }
    }

    fun requestUninstall(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .putExtra(Intent.EXTRA_USER, app.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request uninstall of ${app.packageName}", e)
        }
    }
}
