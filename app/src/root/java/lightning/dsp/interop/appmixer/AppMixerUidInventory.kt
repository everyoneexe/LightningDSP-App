package lightning.dsp.interop.appmixer

import android.content.Context
import android.os.Process
import android.os.UserManager
import android.content.pm.LauncherApps
import lightning.dsp.session.shared.SessionDspControlPreferences

/** Profile-scoped inventory; unknown package membership rejects the entire snapshot. */
internal class AppMixerUidInventory(private val context: Context) {
    data class Route(val excludedUids: Set<Int>, val includesSystemSounds: Boolean, val userIds: Set<Int>)

    fun readRoute(includeSystemSounds: Boolean): Route? {
        return try {
            val packages = context.packageManager
            val preferences = context.getSharedPreferences("lightningdsp_volume_panel", Context.MODE_PRIVATE)
            @Suppress("DEPRECATION")
            val personal = packages.getInstalledApplications(0)
            val currentUser = Process.myUid() / 100000
            val launcher = context.getSystemService(LauncherApps::class.java)
            val work = if (preferences.getBoolean("process_work_profile_audio", false))
                launcher?.profiles.orEmpty().filter { profile ->
                    context.getSystemService(UserManager::class.java)?.isQuietModeEnabled(profile) != true
                }.flatMap { profile ->
                    launcher!!.getActivityList(null, profile).map { it.applicationInfo }
                }.filter { it.uid / 100000 != currentUser } else emptyList()
            val users = (work.map { it.uid / 100000 } + currentUser).toSet()
            val installed = personal + work
            val uids = installed.map { it.uid }.filter { it >= 0 && it % 100000 in 10000..19999 }.toSet()
            val apps = ArrayList<AppMixerUidSelection.App>()
            for (uid in uids) {
                val members = packages.getPackagesForUid(uid)?.toSet() ?: return null
                if (members.isEmpty()) return null
                for (name in members) apps += AppMixerUidSelection.App(uid, name,
                    preferences.getBoolean(SessionDspControlPreferences.preferenceKey(name, uid / 100000, currentUser), true))
            }
            val exclusions = AppMixerUidSelection.exclusions(apps, Process.myUid(), includeSystemSounds, users.size)
            if (exclusions != null) Route(exclusions, includeSystemSounds, users)
            else if (includeSystemSounds) {
                // Preserve Fusion Mode if the extra usage rules exceed AudioPolicy's
                // criterion budget; degrade only optional system-sound coverage.
                AppMixerUidSelection.exclusions(apps, Process.myUid(), false, users.size)?.let { Route(it, false, users) }
            } else null
        } catch (_: RuntimeException) { null }
    }
}
