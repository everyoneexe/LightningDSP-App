package lightning.dsp.volumemanager

import lightning.dsp.volumemanager.backend.DspControlRowRetention
import org.junit.Assert.*
import org.junit.Test

class DspControlRowRetentionTest {
    @Test fun dspToggleBrieflyKeepsOnlyTheTouchedRowVisible() {
        val retention = DspControlRowRetention(durationMillis = 3_000, limit = 2)
        retention.retain("user:0:player.one", 100)

        assertTrue(retention.contains("user:0:player.one", 101))
        assertFalse(retention.contains("user:0:player.two", 101))
        assertTrue(retention.contains("user:0:player.one", 3_099))
        assertFalse(retention.contains("user:0:player.one", 3_100))
    }

    @Test fun retentionIsBoundedAndARepeatToggleExtendsOnlyThatRowsDeadline() {
        val retention = DspControlRowRetention(durationMillis = 3_000, limit = 2)
        retention.retain("one", 100)
        retention.retain("two", 200)
        retention.retain("one", 500)
        retention.retain("three", 600)

        assertFalse(retention.contains("two", 601))
        assertTrue(retention.contains("one", 601))
        assertTrue(retention.contains("three", 601))
    }
}
