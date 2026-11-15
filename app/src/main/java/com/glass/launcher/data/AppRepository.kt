package com.glass.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: String,
) {
    val key get() = "$packageName/$activityName"
    val component get() = ComponentName(packageName, activityName)
    val sortKey = label.lowercase()
    val letter: Char = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
}

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .map {
                AppInfo(
                    label = it.label.toString(),
                    packageName = it.componentName.packageName,
                    activityName = it.componentName.className,
                    category = categoryOf(it.applicationInfo),
                )
            }
            .sortedBy { it.sortKey }
    }

    private fun categoryOf(ai: ApplicationInfo): String = when (ai.category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Media"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        else -> if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) "System" else "Other"
    }

    fun launch(app: AppInfo): Boolean = runCatching {
        launcherApps.startMainActivity(app.component, Process.myUserHandle(), null, null); true
    }.getOrElse {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
    }

    /** App shortcuts (long-press menu). Only works when we are the default home app. */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        launcherApps.getShortcuts(q, Process.myUserHandle()).orEmpty().take(4)
    }.getOrDefault(emptyList())

    fun startShortcut(s: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(s, null, null) }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, Process.myUserHandle(), null, null) }
    }

    fun uninstall(app: AppInfo) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
