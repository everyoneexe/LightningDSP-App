package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.backend.SystemUiHookIdentity
import org.junit.Assert.*
import org.junit.Test

class SystemUiHookIdentityTest {
    @Test fun markerMustIdentifyALiveSystemUiProcess() {
        assertTrue(SystemUiHookIdentity.matches("100", listOf(100)))
        assertFalse(SystemUiHookIdentity.matches("100", listOf(101)))
        assertFalse(SystemUiHookIdentity.matches("100", emptyList()))
        assertFalse(SystemUiHookIdentity.matches("0", listOf(0)))
        assertFalse(SystemUiHookIdentity.matches("-1", listOf(-1)))
        assertFalse(SystemUiHookIdentity.matches("garbage", listOf(100)))
        assertFalse(SystemUiHookIdentity.matches(null, listOf(100)))
    }
}
