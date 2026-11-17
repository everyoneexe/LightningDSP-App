package lightning.dsp.volumemanager.surface

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import lightning.dsp.volumemanager.backend.LightningDspVolumeManager
import lightning.dsp.volumemanager.backend.SystemUiVolumePanelOption

/** App-local receipt of an authenticated SystemUI host; never persisted or backed up. */
object VolumePanelSurfaceClient {
    const val PREPARE = "com.lightning.dsp.action.VOLUME_SURFACE_PREPARE"
    const val SHOW = "com.lightning.dsp.action.VOLUME_SURFACE_SHOW"
    const val HIDE = "com.lightning.dsp.action.VOLUME_SURFACE_HIDE"
    private data class Receipt(val owner: IBinder, val generation: Long, val ready: Boolean,
                               val locked: Boolean, val at: Long)
    @Volatile private var receipt: Receipt? = null

    @JvmStatic fun markReady(owner: IBinder, generation: Long, ready: Boolean, locked: Boolean = false) {
        receipt = Receipt(owner, generation, ready, locked, SystemClock.uptimeMillis())
        // Heartbeats must also refresh the root's absolute expiry, not only state changes.
        LightningDspVolumeManager.onSurfaceReadinessChanged()
    }

    @JvmStatic fun clear(owner: IBinder, generation: Long) {
        val current = receipt ?: return
        if (current.owner == owner && current.generation == generation) {
            receipt = null
            LightningDspVolumeManager.onSurfaceReadinessChanged()
        }
    }

    fun readyUntil(context: Context): Long {
        if (Build.VERSION.SDK_INT < 30 || !SystemUiVolumePanelOption.requested(context)) return 0
        val current = receipt ?: return 0
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return 0
        val locked = keyguard.isKeyguardLocked || keyguard.isDeviceLocked
        return if (SurfaceReadinessPolicy.ready(current.ready && current.owner.isBinderAlive,
                current.at, SystemClock.uptimeMillis(), current.locked, locked))
            current.at + SurfaceReadinessPolicy.TTL_MS else 0
    }

    fun prepare(context: Context) {
        if (Build.VERSION.SDK_INT >= 30 && SystemUiVolumePanelOption.requested(context)) signal(context, PREPARE)
    }
    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 30) signal(context, SHOW)
    }
    fun hide(context: Context) {
        receipt = null
        LightningDspVolumeManager.onSurfaceReadinessChanged()
        if (Build.VERSION.SDK_INT >= 30) signal(context, HIDE)
    }
    private fun signal(context: Context, action: String) {
        // The SystemUI dynamic receiver requires our signature permission as sender.
        runCatching { context.sendBroadcast(Intent(action).setPackage("com.android.systemui")) }
    }
}
