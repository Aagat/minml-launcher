package dev.obvious.minimallauncher.catalog

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.util.concurrent.Executors

class AppCatalog(
    context: Context,
    private val onCatalogChanged: (List<AppEntry>) -> Unit,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = refresh()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback, mainHandler)
        refresh()
    }

    fun stop() {
        runCatching { launcherApps.unregisterCallback(callback) }
        executor.shutdownNow()
    }

    fun refresh() {
        executor.execute {
            val personal = Process.myUserHandle()
            val apps = launcherApps.profiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                    val applicationInfo = info.applicationInfo
                    val component = info.componentName
                    AppEntry(
                        stableId = "$serial:${component.flattenToString()}",
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { applicationInfo.packageName },
                        packageName = component.packageName,
                        className = component.className,
                        userSerial = serial,
                        isWorkProfile = user != personal,
                        isMedia = applicationInfo.category in MEDIA_CATEGORIES,
                    )
                }
            }.distinctBy { it.stableId }
            mainHandler.post { onCatalogChanged(AppSearch.rank(apps, "")) }
        }
    }

    fun launch(entry: AppEntry): Result<Unit> = runCatching {
        val user = launcherApps.profiles.firstOrNull { userManager.getSerialNumberForUser(it) == entry.userSerial }
            ?: error("Profile is unavailable")
        launcherApps.startMainActivity(ComponentName(entry.packageName, entry.className), user, null, null)
    }

    private companion object {
        val MEDIA_CATEGORIES = setOf(
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_GAME,
        )
    }
}
