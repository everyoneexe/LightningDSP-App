package lightning.dsp.interop.appmixer

import org.junit.Assert.*
import org.junit.Test

class AppMixerFixedDspCaptureTest {
    @Test fun diagnosticSeparatesFragmentWaitAndDspWorkWithoutChangingPcm() {
        var now = 0L
        val timing = AppMixerDspTiming { now }
        val capture = AppMixerFixedDspCapture(4) { input, output, count ->
            now += 3_000
            input.copyInto(output, endIndex = count)
        }
        val output = ShortArray(4)
        assertEquals(0, capture.read(output, timing) { input, offset, _ ->
            input[offset] = 1
            input[offset + 1] = 2
            2
        })
        assertEquals(2, capture.pendingSamples)
        now += 2_000_000
        assertEquals(0, capture.read(output, timing) { _, _, _ -> 0 })
        assertEquals(4, capture.read(output, timing) { input, offset, _ ->
            input[offset] = 3
            input[offset + 1] = 4
            2
        })
        assertArrayEquals(shortArrayOf(1, 2, 3, 4), output)
        assertEquals(0, capture.pendingSamples)
        assertEquals(2_000_000L, timing.maxAssemblyNanos)
        assertEquals(3_000L, timing.maxDspNanos)
        assertEquals(1, timing.blocks)
    }

    @Test fun captureAgeUsesSharedTimebaseAndRejectsBadTimestamps() {
        assertEquals(22_000L, AppMixerCaptureAge.estimateUs(960, 1920, 1_000_000_000, 1_002_000_000, 48000))
        assertEquals(5_000L, AppMixerCaptureAge.estimateUs(1200, 960, 1_000_000_000, 1_010_000_000, 48000))
        assertNull(AppMixerCaptureAge.estimateUs(960, 100_000, 1_000_000_000, 1_002_000_000, 48000))
        assertNull(AppMixerCaptureAge.estimateUs(960, 1920, 1_000_000_000, 2_002_000_000, 48000))
        assertNull(AppMixerCaptureAge.estimateUs(960, 1920, 1_000_000_000, 999_999_999, 48000))
    }

    @Test fun partialAndEmptyReadsProduceOneFixedQuantumWithoutLosingSamples() {
        var calls = 0
        val capture = AppMixerFixedDspCapture(4) { input, output, count ->
            calls++
            assertEquals(4, count)
            for (i in 0 until count) output[i] = (input[i] * 2).toShort()
        }
        val output = ShortArray(4)
        var next = 1
        for (fragment in listOf(1, 0, 2, 1, 4)) {
            val result = capture.read(output) { data, offset, requested ->
                assertTrue(fragment <= requested)
                for (i in 0 until fragment) data[offset + i] = (next++).toShort()
                fragment
            }
            if (result > 0) {
                assertEquals(4, result)
                val first = if (calls == 1) 2 else 10
                assertArrayEquals(ShortArray(4) { (first + it * 2).toShort() }, output)
            }
        }
        assertEquals(2, calls)
    }

    @Test fun errorDoesNotProcessPartialOrStalePcm() {
        val capture = AppMixerFixedDspCapture(4) { _, _, _ -> fail("Incomplete quantum") }
        val output = ShortArray(4)
        assertEquals(0, capture.read(output) { _, _, _ -> 2 })
        assertEquals(-6, capture.read(output) { _, _, _ -> -6 })
    }

    @Test fun invalidSizesAndOverreadsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AppMixerFixedDspCapture(3) { _, _, _ -> } }
        val capture = AppMixerFixedDspCapture(4) { _, _, _ -> }
        assertThrows(IllegalStateException::class.java) { capture.read(ShortArray(4)) { _, _, _ -> 5 } }
        assertThrows(IllegalArgumentException::class.java) { capture.read(ShortArray(2)) { _, _, _ -> 0 } }
    }
}
