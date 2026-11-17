package lightning.dsp.volumemanager

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static contract only; target-method presence and panel behavior require a One UI device. */
class SamsungVolumePanelHookContractTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/root/AndroidManifest.xml").isFile }

    @Test fun suppressionIsSystemUiScopedAndLeaseGated() {
        val hook = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/SamsungVolumePanelHook.java").readText()
        val lease = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/VolumePanelSuppressionLease.kt").readText()
        val root = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/LightningDspVolumeRoot.kt").readText()
        val option = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/SystemUiVolumePanelOption.kt").readText()
        assertTrue(hook.contains("com.android.systemui.volume.VolumeDialogControllerImpl"))
        assertTrue(hook.contains("com.android.systemui.volume.VolumeDialogControl"))
        assertTrue(hook.contains("!SYSTEM_UI.equals(info.packageName) || !\"samsung\".equalsIgnoreCase(Build.MANUFACTURER)"))
        assertTrue(hook.contains("hookAllMethods(control, \"onVolumeChangedW\""))
        assertTrue(hook.contains("if (!suppressionActive()"))
        assertTrue(hook.contains("param.args[1] = flags & ~FLAG_SHOW_UI"))
        assertTrue(root.contains("Build.MANUFACTURER.equals(\"samsung\", ignoreCase = true)"))
        assertTrue(hook.contains("now - refreshedAt <= LEASE_TTL_MS"))
        assertTrue(hook.contains("getBoolean\", ENABLED_PROPERTY, false"))
        assertTrue(hook.contains("READY_PROPERTY"))
        assertTrue(option.contains(".getBoolean(KEY, false)"))
        assertTrue(option.contains("marker in systemUiPids"))
        assertTrue(lease.contains("uptimeMillis - refreshedAt <= TTL_MS"))
        assertTrue(root.contains("settings.optBoolean(\"systemui_panel_opted_in\")"))
        assertTrue(root.contains("settings.optBoolean(\"systemui_hook_ready\")"))
        assertTrue(root.contains("VolumePanelSuppressionLease.PROPERTY, \"0\")"))
    }
}
