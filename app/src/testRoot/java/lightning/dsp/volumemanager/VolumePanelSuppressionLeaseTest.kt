package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.backend.VolumePanelSuppressionLease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePanelSuppressionLeaseTest {
    @Test fun systemUiHookCanQueueDuringReattachOnlyForLiveOptedInManager() {
        assertTrue(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = true, panelEnabled = true,
            bypass = false, optedIn = true, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = false, active = true, panelEnabled = true,
            bypass = false, optedIn = true, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = false, panelEnabled = true,
            bypass = false, optedIn = true, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = true, panelEnabled = false,
            bypass = false, optedIn = true, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = true, panelEnabled = true,
            bypass = false, optedIn = false, hookReady = true
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = true, panelEnabled = true,
            bypass = false, optedIn = true, hookReady = false
        ))
        assertFalse(VolumePanelSuppressionLease.shouldSuppressSystemUiHook(
            root = true, active = true, panelEnabled = true,
            bypass = true, optedIn = true, hookReady = true
        ))
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
