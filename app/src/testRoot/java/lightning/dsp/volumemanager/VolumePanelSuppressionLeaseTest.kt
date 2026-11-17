package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.backend.VolumePanelSuppressionLease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePanelSuppressionLeaseTest {
    @Test fun suppressionRequiresExplicitOptInAndLiveSystemUiHook() {
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, true, true, true, true, false))
        assertTrue(VolumePanelSuppressionLease.shouldSuppress(
            true, true, true, true, true, false, optedIn = true, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(
            true, true, true, true, true, false, optedIn = false, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(
            true, true, true, true, true, false, optedIn = true, hookReady = false
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, true, true, false, true, false, true, true))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(false, true, true, true, true, false, true, true))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, false, true, true, true, false, true, true))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, true, false, true, true, false, true, true))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, true, true, true, false, false, true, true))
        assertFalse(VolumePanelSuppressionLease.shouldSuppress(true, true, true, true, true, true, true, true))
    }

    @Test fun genericControllerKeepsStockPanelUnlessOptInAndOverlayAreLive() {
        assertFalse(VolumePanelSuppressionLease.shouldSuppressController(
            active = true, panelEnabled = true, overlayReady = true, controllerReady = true,
            optedIn = false, bypass = false
        ))
        assertTrue(VolumePanelSuppressionLease.shouldSuppressController(
            active = true, panelEnabled = true, overlayReady = true, controllerReady = true,
            optedIn = true, bypass = false
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressController(
            active = true, panelEnabled = true, overlayReady = false, controllerReady = true,
            optedIn = true, bypass = false
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressController(
            active = true, panelEnabled = true, overlayReady = true, controllerReady = false,
            optedIn = true, bypass = false
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressController(
            active = true, panelEnabled = true, overlayReady = true, controllerReady = true,
            optedIn = true, bypass = true
        ))
    }

    @Test fun enabledLeaseUsesMonotonicRefreshTimestamp() {
        assertEquals("12000", VolumePanelSuppressionLease.propertyValue(true, 12_000L))
        assertEquals("0", VolumePanelSuppressionLease.propertyValue(false, 12_000L))
        assertEquals("0", VolumePanelSuppressionLease.propertyValue(true, 0L))
    }

    @Test fun suppressionExpiresWhenRootBridgeStopsRenewingLease() {
        assertTrue(VolumePanelSuppressionLease.isActive("12000", 18_000L))
        assertFalse(VolumePanelSuppressionLease.isActive("12000", 18_001L))
        assertFalse(VolumePanelSuppressionLease.isActive("12000", 11_999L))
        assertFalse(VolumePanelSuppressionLease.isActive("0", 12_000L))
        assertFalse(VolumePanelSuppressionLease.isActive("bad", 12_000L))
        assertFalse(VolumePanelSuppressionLease.isActive(null, 12_000L))
    }
}
