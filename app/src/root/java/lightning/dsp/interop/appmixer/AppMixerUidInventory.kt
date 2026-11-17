package lightning.dsp.interop.appmixer

import android.content.Context
import android.os.Process
import android.os.UserManager
import android.content.pm.LauncherApps
import lightning.dsp.session.shared.SessionDspControlPreferences

/** Profile-scoped inventory; unknown package membership rejects that profile's route. */
internal class AppMixerUidInventory(private val context: Context) {
    data class Route(
        val excludedUids: Set<Int>,
        val includesSystemSounds: Boolean,
        val userIds: Set<Int>,
        val workProfileIncluded: Boolean,
    )

    fun readRoute(includeSystemSounds: Boolean): Route? {
        return try {
            val packages = context.packageManager
            val preferences = context.getSharedPreferences("lightningdsp_volume_panel", Context.MODE_PRIVATE)
            @Suppress("DEPRECATION")
            val personal = packages.getInstalledApplications(0)
            val currentUser = Process.myUid() / 100000
            val launcher = context.getSystemService(LauncherApps::class.java)
            val personalApps = appsFor(personal, currentUser, packages::getPackagesForUid, preferences)
                ?: return null

            // PackageManager may reject cross-profile UID membership even when LauncherApps
            // exposes the managed profile. Never let that optional inventory failure disable
            // the personal Fusion route, and never guess shared-UID membership from activities.
            val optedInToWork = preferences.getBoolean("process_work_profile_audio", false)
            val workInventory = if (optedInToWork && launcher != null) runCatching {
                val userManager = context.getSystemService(UserManager::class.java)
                val work = launcher.profiles.filter { profile ->
                    userManager?.isQuietModeEnabled(profile) != true
                }.flatMap { profile ->
                    launcher.getActivityList(null, profile).map { it.applicationInfo }
                }.filter { it.uid >= 0 && it.uid / 100000 != currentUser }
                if (work.isEmpty()) emptyList() else {
                    appsFor(work, currentUser, packages::getPackagesForUid, preferences)
                        ?: throw IncompleteProfileInventory()
                }
            }.getOrNull() else null
            val workIncluded = optedInToWork && workInventory != null
            val apps = if (workIncluded) personalApps + workInventory.orEmpty() else personalApps
            val users = if (workIncluded) (apps.map { it.uid / 100000 } + currentUser).toSet() else setOf(currentUser)
            return routeFor(apps, users, includeSystemSounds, workIncluded)
        } catch (_: RuntimeException) { null }
    }

    private fun appsFor(
        installed: List<android.content.pm.ApplicationInfo>,
        currentUser: Int,
        packagesForUid: (Int) -> Array<String>?,
        preferences: android.content.SharedPreferences,
    ): List<AppMixerUidSelection.App>? {
        val uids = installed.map { it.uid }.filter { it >= 0 && it % 100000 in 10000..19999 }.toSet()
        val apps = ArrayList<AppMixerUidSelection.App>()
        for (uid in uids) {
            val members = packagesForUid(uid)?.toSet() ?: return null
            if (members.isEmpty()) return null
            for (name in members) apps += AppMixerUidSelection.App(uid, name,
                preferences.getBoolean(SessionDspControlPreferences.preferenceKey(name, uid / 100000, currentUser), true))
        }
        return apps
    }

    private fun routeFor(
        apps: List<AppMixerUidSelection.App>,
        users: Set<Int>,
        includeSystemSounds: Boolean,
        workProfileIncluded: Boolean,
    ): Route? {
        val exclusions = AppMixerUidSelection.exclusions(apps, Process.myUid(), includeSystemSounds, users.size)
        if (exclusions != null) return Route(exclusions, includeSystemSounds, users, workProfileIncluded)
        if (includeSystemSounds) {
            // Preserve Fusion Mode if the extra usage rules exceed AudioPolicy's
            // criterion budget; degrade only optional system-sound coverage.
            AppMixerUidSelection.exclusions(apps, Process.myUid(), false, users.size)?.let {
                return Route(it, false, users, workProfileIncluded)
            }
        }
        return null
    }

    private class IncompleteProfileInventory : RuntimeException()
}
