package lightning.dsp.volumemanager.backend

import android.app.ActivityManager
import android.content.Context

/** Root control-loop probe; never queues a shell job on the app's heartbeat worker. */
internal object SystemUiHookIdentity {
    fun matches(marker: String?, livePids: Collection<Int>): Boolean =
        marker?.toIntOrNull()?.let { it > 0 && it in livePids } == true

    fun available(context: Context): Boolean = runCatching {
        val properties = Class.forName("android.os.SystemProperties")
        val marker = properties.getDeclaredMethod("get", String::class.java, String::class.java)
            .invoke(null, SystemUiVolumePanelOption.READY_PROPERTY, "") as String
        val processes = context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
        matches(marker, processes.filter { it.processName == "com.android.systemui" }.map { it.pid })
    }.getOrDefault(false)
}
