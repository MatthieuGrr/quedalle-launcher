package dev.mlg.quedalle.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.mlg.quedalle.model.AppInfo

class AppRepository(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        return resolveInfos
            .map { AppInfo(packageName = it.activityInfo.packageName, label = it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
