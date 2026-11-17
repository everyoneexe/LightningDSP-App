package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.surface.SurfaceSessionPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SurfaceSessionPolicyTest {
    @Test fun callerMustBeInstalledSystemUiUidInTheServiceUser() {
        assertTrue(SurfaceSessionPolicy.callerAllowed(10286, 10286, 10652))
        assertTrue(SurfaceSessionPolicy.callerAllowed(1000, 1000, 10652))
        assertFalse(SurfaceSessionPolicy.callerAllowed(10485, 10286, 10652))
        assertFalse(SurfaceSessionPolicy.callerAllowed(1010286, 1010286, 10652))
        assertFalse(SurfaceSessionPolicy.callerAllowed(-1, -1, 10652))
    }

    @Test fun requestsHaveBoundedMemoryAndPositiveGeneration() {
        assertTrue(SurfaceSessionPolicy.validRequest(0, 1080, 1600, 1))
        assertFalse(SurfaceSessionPolicy.validRequest(-1, 1080, 1600, 1))
        assertFalse(SurfaceSessionPolicy.validRequest(0, 0, 1600, 1))
        assertFalse(SurfaceSessionPolicy.validRequest(0, 4096, 4096, 1))
        assertFalse(SurfaceSessionPolicy.validRequest(0, 1080, 1600, 0))
        assertFalse(SurfaceSessionPolicy.validRequest(0, Int.MAX_VALUE, Int.MAX_VALUE, 1))
    }

    @Test fun staleOwnersGenerationsAndClockValuesAreRejected() {
        assertTrue(SurfaceSessionPolicy.current(true, 2, 2))
        assertFalse(SurfaceSessionPolicy.current(false, 2, 2))
        assertFalse(SurfaceSessionPolicy.current(true, 2, 1))
        assertFalse(SurfaceSessionPolicy.current(true, 0, 0))
        assertTrue(SurfaceSessionPolicy.leaseValid(100, 6100))
        assertFalse(SurfaceSessionPolicy.leaseValid(100, 6101))
        assertFalse(SurfaceSessionPolicy.leaseValid(100, 99))
    }

    @Test fun lockedPrivacyCannotBeOverriddenByAnUnlockedHostClaim() {
        assertFalse(SurfaceSessionPolicy.mayPresent(true, false, true, false))
        assertTrue(SurfaceSessionPolicy.mayPresent(true, true, true, false))
        assertTrue(SurfaceSessionPolicy.mayPresent(true, true, false, true))
        assertFalse(SurfaceSessionPolicy.mayPresent(false, true, true, true))
    }

    @Test fun exportedBridgeAuthenticatesBeforePostingAndLockedContentHasNoPrivateControls() {
        val app = sequenceOf(File("."), File("app")).first { File(it, "src/root/AndroidManifest.xml").isFile }
        val manifest = File(app, "src/root/AndroidManifest.xml").readText()
        val host = File(app,
            "src/root/java/lightning/dsp/volumemanager/surface/SystemUiVolumeSurfaceHost.java").readText()
        val permission = "com.lightning.dsp.permission.VOLUME_SURFACE_HOST"
        assertTrue(manifest.contains("<permission android:name=\"$permission\""))
        assertTrue(manifest.contains("<uses-permission android:name=\"$permission\""))
        assertTrue(host.contains(permission))
        val service = File(app,
            "src/root/java/lightning/dsp/volumemanager/surface/VolumePanelSurfaceService.kt").readText()
        assertTrue(service.contains("SurfaceSessionPolicy.callerAllowed(Binder.getCallingUid()"))
        assertTrue(service.contains("owner.linkToDeath(current.death, 0)"))
        assertTrue(service.contains("!current.locked && locked()"))
        assertTrue(service.contains("ViewTreeObserver.OnDrawListener"))
        assertTrue(service.contains("FIRST_DRAW_TIMEOUT_MILLIS"))
        assertTrue(service.contains("private const val MAX_WARNINGS = 4"))
        assertTrue(service.contains("error?.javaClass?.simpleName"))
        assertFalse(service.contains("Timber.w(error"))
        assertTrue(service.contains("Intent.ACTION_SCREEN_OFF"))
        assertTrue(service.contains("MotionEvent.ACTION_CANCEL"))
        assertTrue(service.contains("VolumeOverlayIdlePolicy.Interaction.ACCESSIBILITY"))
        assertTrue(service.contains("idle.remainingMillis(SystemClock.uptimeMillis()) == null"))
        assertTrue(service.contains("callback.interaction(generation, engaged)"))
        assertTrue(service.contains("VolumePanelSurfaceClient.clear(current.owner, current.generation)"))
        val locked = service.substringAfter("private fun LockedStreamPanel")
        assertTrue(locked.contains("STREAM_MUSIC"))
        assertTrue(locked.contains("STREAM_RING"))
        assertFalse(locked.contains("LightningVolumeFloatingPanel"))
        assertFalse(locked.contains("VolumeManagerState"))
        assertFalse(locked.contains("startActivity"))
        assertFalse(locked.contains("show_session_dsp_controls"))
    }
}
