package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.surface.SurfaceReadinessPolicy
import org.junit.Assert.*
import org.junit.Test

class SurfaceReadinessPolicyTest {
    @Test fun attachedReceiptExpiresAndCannotCrossLockStates() {
        assertTrue(SurfaceReadinessPolicy.ready(true, 100L, 6100L, false, false))
        assertFalse(SurfaceReadinessPolicy.ready(true, 100L, 6101L, false, false))
        assertFalse(SurfaceReadinessPolicy.ready(true, 100L, 99L, false, false))
        assertFalse(SurfaceReadinessPolicy.ready(true, 100L, 100L, false, true))
        assertTrue(SurfaceReadinessPolicy.ready(true, 100L, 100L, true, false))
        assertFalse(SurfaceReadinessPolicy.ready(false, 100L, 100L, true, true))
        assertFalse(SurfaceReadinessPolicy.ready(true, 0L, 100L, true, true))
        assertTrue(SurfaceReadinessPolicy.ready(true, 100L, 100L, true, true))
    }
}
