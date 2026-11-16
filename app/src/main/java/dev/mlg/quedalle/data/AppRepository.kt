package dev.mlg.quedalle.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dev.mlg.quedalle.model.AppInfo
import dev.mlg.quedalle.model.InfoKind
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

/** Sent when a profile (work, private space) is paused or resumed. */
private val PROFILE_ACTIONS = listOf(
    "android.intent.action.MANAGED_PROFILE_AVAILABLE",
    "android.intent.action.MANAGED_PROFILE_UNAVAILABLE",
    "android.intent.action.PROFILE_AVAILABLE",
    "android.intent.action.PROFILE_UNAVAILABLE",
)

/**
 * @param privateSpaceLocked null when the device has no private space (or
 * this launcher is not allowed to see it).
 */
data class InstalledApps(val apps: List<AppInfo>, val privateSpaceLocked: Boolean?)

/** A shortcut an app publishes, as offered for pinning. */
data class AppShortcut(val id: String, val label: String)

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // The profile broadcasts are protected: only the system can send them,
    // and before Android 13 there is no flag to say so.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private val packageEvents: Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply { PROFILE_ACTIONS.forEach(::addAction) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(profileReceiver, filter)
        }
        send(Unit)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(profileReceiver)
        }
    }

    /** Launchable apps across all profiles, refreshed on package and profile events. */
    val apps: Flow<InstalledApps> = merge(packageEvents, refreshRequests)
        .conflate()
        .map { loadApps() }
        .flowOn(Dispatchers.IO)

    /** Forces a reload, e.g. when the launcher comes back to the foreground. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun profiles(): List<UserHandle> = try {
        launcherApps.profiles
    } catch (e: Exception) {
        Log.e(TAG, "Failed to list profiles", e)
        listOf(Process.myUserHandle())
    }

    private fun isPrivateSpace(user: UserHandle): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && try {
            launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the type of profile $user", e)
            false
        }

    private fun privateSpace(): UserHandle? = profiles().firstOrNull(::isPrivateSpace)

    private fun loadApps(): InstalledApps {
        val myUser = Process.myUserHandle()
        val pm = context.packageManager
        var privateSpaceLocked: Boolean? = null
        // Per-profile try/catch: a locked work profile must not wipe the whole list.
        val apps = profiles().flatMap { user ->
            try {
                if (isPrivateSpace(user)) {
                    val locked = userManager.isQuietModeEnabled(user)
                    privateSpaceLocked = locked
                    // A locked private space shows nothing at all, not even in search.
                    if (locked) return@flatMap emptyList()
                }
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
        return InstalledApps(apps, privateSpaceLocked)
    }

    private fun userFor(userSerial: Long?): UserHandle? =
        if (userSerial == null) Process.myUserHandle() else userManager.getUserForSerialNumber(userSerial)

    /**
     * True only when the package is definitely gone from its profile. An app
     * that is merely unreachable (locked, paused or hidden profile, any
     * lookup failure) is reported as still installed, so its tile is never
     * dropped on a doubt.
     */
    fun isUninstalled(packageName: String, userSerial: Long?): Boolean = try {
        val user = userFor(userSerial)
        when {
            user == null -> true // the profile itself was removed
            // e.g. the private space, invisible while another launcher is the default one
            user !in launcherApps.profiles -> false
            !userManager.isUserUnlocked(user) || userManager.isQuietModeEnabled(user) -> false
            else -> launcherApps.getApplicationInfo(packageName, 0, user) == null
        }
    } catch (_: PackageManager.NameNotFoundException) {
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot tell whether $packageName is installed", e)
        false
    }

    // ── Launching ────────────────────────────────────────────────────────────

    /** @return false when the app could not be started. */
    fun launchApp(app: AppInfo): Boolean = try {
        launcherApps.startMainActivity(app.componentName, app.user, null, null)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch ${app.packageName}", e)
        false
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.componentName, app.user, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app info for ${app.packageName}", e)
        }
    }

    fun requestUninstall(app: AppInfo) {
        start(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .putExtra(Intent.EXTRA_USER, app.user)
        )
    }

    /** @return false when nothing could open [uri]. */
    fun openLink(uri: String): Boolean = start(Intent(Intent.ACTION_VIEW, uri.toUri()))

    /** Opens the system screen matching an information tile. */
    fun openInfo(kind: InfoKind): Boolean = start(
        when (kind) {
            InfoKind.CLOCK, InfoKind.ALARM -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            InfoKind.DATE -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            InfoKind.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        }
    )

    fun openAccessibilitySettings(): Boolean = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start $intent", e)
        false
    }

    // ── App shortcuts ────────────────────────────────────────────────────────

    /** Shortcuts [app] offers; empty unless this is the default launcher. */
    fun shortcutsOf(app: AppInfo): List<AppShortcut> = try {
        if (!launcherApps.hasShortcutHostPermission()) emptyList()
        else {
            val query = ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        ShortcutQuery.FLAG_MATCH_MANIFEST or
                        ShortcutQuery.FLAG_MATCH_PINNED
                )
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled }
                .map { AppShortcut(it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString()) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot list shortcuts of ${app.packageName}", e)
        emptyList()
    }

    /**
     * Tells the system which shortcuts of a package have a tile, so the
     * app cannot withdraw them. [shortcutIds] replaces the previous set.
     */
    fun pinShortcuts(packageName: String, userSerial: Long?, shortcutIds: List<String>) {
        try {
            val user = userFor(userSerial) ?: return
            if (launcherApps.hasShortcutHostPermission()) {
                launcherApps.pinShortcuts(packageName, shortcutIds, user)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot pin shortcuts of $packageName", e)
        }
    }

    /** @return false when the shortcut could not be started. */
    fun startShortcut(packageName: String, userSerial: Long?, shortcutId: String): Boolean = try {
        val user = userFor(userSerial) ?: error("profile $userSerial is gone")
        launcherApps.startShortcut(packageName, shortcutId, null, null, user)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start shortcut $shortcutId of $packageName", e)
        false
    }

    // ── Contacts ─────────────────────────────────────────────────────────────

    /**
     * Name and durable link of a contact the user just picked. The picker
     * grants one-time access to that contact only: no contacts permission.
     */
    fun contactLink(picked: Uri): Pair<String, String>? = try {
        val resolver = context.contentResolver
        val name = resolver.query(
            picked, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        val link = ContactsContract.Contacts.getLookupUri(resolver, picked) ?: picked
        name?.takeIf { it.isNotBlank() }?.let { it to link.toString() }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot read the picked contact", e)
        null
    }

    // ── System panels & private space ────────────────────────────────────────

    /**
     * Expands the notification shade or the quick settings. This relies on
     * a non-SDK API that some OEM builds block.
     * @return false when the device refused.
     */
    @Suppress("PrivateApi")
    fun expandStatusBar(quickSettings: Boolean): Boolean = try {
        val service = context.getSystemService("statusbar") ?: error("no statusbar service")
        Class.forName("android.app.StatusBarManager")
            .getMethod(if (quickSettings) "expandSettingsPanel" else "expandNotificationsPanel")
            .invoke(service)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Cannot expand the status bar", e)
        false
    }

    /**
     * Locks the private space, or asks the system to unlock it (the user
     * then authenticates). @return false when there is none or it failed.
     */
    fun togglePrivateSpace(): Boolean = try {
        val user = privateSpace()
        if (user == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false
        else {
            userManager.requestQuietModeEnabled(!userManager.isQuietModeEnabled(user), user)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot toggle the private space", e)
        false
    }
}
