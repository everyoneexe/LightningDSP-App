package lightning.dsp.volumemanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static contract only; OEM method presence and panel behavior require on-device validation. */
class SystemUiVolumePanelHookContractTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/root/AndroidManifest.xml").isFile }

    @Test fun frameworkNeutralEntryKeepsBackendAcrossDeferredInitialization() {
        val hook = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/SystemUiVolumePanelHook.java").readText()
        val host = File(app,
            "src/root/java/lightning/dsp/volumemanager/surface/SystemUiVolumeSurfaceHost.java").readText()
        assertTrue(hook.contains("public static void install(HookBackend backend, String packageName, String processName)"))
        assertTrue(hook.contains("initializeSystemUi(backend, 0)"))
        assertTrue(hook.contains("initializeSystemUi(backend, attempt + 1)"))
        assertTrue(hook.contains("installVolumeController(backend, loader, attempt + 1)"))
        assertTrue(hook.contains("backend.invokeOriginal(method, controller, originalArgs)"))
        assertFalse(hook.contains("de.robv.android.xposed"))
        assertFalse(host.contains("de.robv.android.xposed"))
    }

    @Test fun suppressionUsesVerifiedAospContractAndFailsOpenForUnsupportedBuilds() {
        val hook = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/SystemUiVolumePanelHook.java").readText()
        val lease = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/VolumePanelSuppressionLease.kt").readText()
        val root = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/LightningDspVolumeRoot.kt").readText()
        val option = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/SystemUiVolumePanelOption.kt").readText()
        val host = File(app,
            "src/root/java/lightning/dsp/volumemanager/surface/SystemUiVolumeSurfaceHost.java").readText()
        val compatibility = File(app,
            "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        assertTrue(hook.contains("com.android.systemui.volume.VolumeDialogControllerImpl"))
        assertTrue(hook.contains("com.android.systemui.volume.VolumeDialogControl"))
        assertTrue(hook.contains("if (!SYSTEM_UI.equals(packageName) || !SYSTEM_UI.equals(processName)) return"))
        assertTrue(host.contains("Application.getProcessName()"))
        assertTrue(compatibility.contains("private static boolean isPlatformHookTarget"))
        assertTrue(compatibility.contains("\"android\".equals(packageName)"))
        assertTrue(compatibility.contains("\"system\".equals(packageName)"))
        assertTrue(compatibility.contains("\"com.android.systemui\".equals(packageName)"))
        assertTrue(compatibility.contains("\"system_server\".equals(processName)"))
        assertTrue(compatibility.contains("if (isPlatformHookTarget(packageName, processName) || appId < 10000"))
        assertTrue(hook.contains("installVolumeController(backend, application.getClassLoader(), 0)"))
        assertTrue(hook.contains("CONTROLLER_ATTEMPTS = 20"))
        assertTrue(hook.contains("CONTROLLER_RETRY_MS = 500L"))
        val startupGuard = "if (!startupScheduled.compareAndSet(false, true)) return;"
        assertTrue(hook.contains(startupGuard))
        assertTrue(hook.indexOf("equals(processName)) return") < hook.indexOf(startupGuard))
        assertTrue(hook.indexOf(startupGuard) < hook.indexOf("clearReadinessMarker();"))
        assertTrue(hook.contains("HookPlatform.setProperty(READY_PROPERTY, \"0\")"))
        assertTrue(hook.contains("could not clear stale readiness marker"))
        assertTrue(hook.contains("currentApplication"))
        assertFalse(hook.contains("findAndHookMethod(Application.class, \"onCreate\""))
        assertTrue(hook.contains("int candidateCount = samsung ? VOLUME_DIALOG_CONTROLLERS.length : 1"))
        assertTrue(hook.contains("parameters.length == 2 || parameters.length == 3"))
        assertTrue(hook.contains("backend.before(method, HookBackend.DEFAULT_PRIORITY"))
        assertTrue(hook.contains("if (!suppressionActive()"))
        assertTrue(hook.contains("param.args[1] = flags & ~FLAG_SHOW_UI"))
        assertTrue(hook.indexOf("if (!suppressionActive())") <
            hook.indexOf("if (!SystemUiVolumeSurfaceHost.isReady())"))
        assertTrue(hook.contains("Queue this request through the bounded surface attach"))
        assertTrue(hook.contains("SystemUiVolumeSurfaceHost.showFromVolumeEvent()"))
        assertTrue(root.contains("uid == 0"))
        assertTrue(hook.contains("now - refreshedAt <= LEASE_TTL_MS"))
        assertTrue(hook.contains("HookPlatform.propertyBoolean(ENABLED_PROPERTY, false)"))
        assertTrue(hook.contains("READY_PROPERTY"))
        assertTrue(option.contains(".getBoolean(KEY, false)"))
        assertTrue(option.contains("marker in systemUiPids"))
        assertTrue(lease.contains("uptimeMillis - refreshedAt <= TTL_MS"))
        assertTrue(root.contains("settings.optBoolean(\"systemui_panel_opted_in\")"))
        assertTrue(root.contains("settings.optBoolean(\"panel_overlay_ready\")"))
        assertTrue(root.contains("shouldSuppressSystemUiHook"))
        assertTrue(root.contains("SystemUiHookIdentity.available(context)"))
        assertTrue(root.contains("panelOptedIn.get(), liveSystemUiHook"))
        assertTrue(root.contains("VolumePanelSuppressionLease.PROPERTY, \"0\")"))
    }
}
