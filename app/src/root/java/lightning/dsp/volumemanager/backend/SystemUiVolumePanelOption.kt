package lightning.dsp.volumemanager.backend

import android.content.Context
import com.topjohnwu.superuser.Shell
import lightning.dsp.flavor.RootShellImpl

/** Opt-in only when the Samsung SystemUI hook acknowledges its current process. */
object SystemUiVolumePanelOption {
    const val PREFERENCES = "lightningdsp_volume_panel"
    const val KEY = "suppress_systemui_volume_panel"
    const val ENABLED_PROPERTY = "debug.lightningdsp.suppress_volume_panel"
    const val READY_PROPERTY = "debug.lightningdsp.volume_panel_hook_pid"
    private const val SYSTEM_UI = "com.android.systemui"

    fun requested(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(KEY, false)

    /** A PID match distinguishes an active SystemUI hook from installation or stale state. */
    fun available(): Boolean {
        if (!RootShellImpl.hasRootShell()) return false
        val result = Shell.cmd("getprop $READY_PROPERTY", "pidof $SYSTEM_UI").exec()
        val marker = result.out.getOrNull(0)?.trim()
        val systemUiPids = result.out.getOrNull(1)?.trim()?.split(Regex("\\s+"))?.toSet().orEmpty()
        return result.isSuccess && !marker.isNullOrEmpty() && marker in systemUiPids
    }

    /** Persist the choice only after the live hook and volatile runtime flag are verified. */
    fun set(context: Context, enabled: Boolean): Boolean {
        if (enabled && !available()) return false
        if (!RootShellImpl.hasRootShell()) return false
        val value = if (enabled) "1" else "0"
        val result = Shell.cmd("setprop $ENABLED_PROPERTY $value", "getprop $ENABLED_PROPERTY").exec()
        if (!result.isSuccess || result.out.lastOrNull()?.trim() != value) return false
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        return true
    }

    fun restore(context: Context) {
        if (requested(context)) set(context, true)
    }
}
