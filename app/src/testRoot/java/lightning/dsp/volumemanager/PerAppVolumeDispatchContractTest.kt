package lightning.dsp.volumemanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring guards, not a measurement of player or device response latency. */
class PerAppVolumeDispatchContractTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/root/AndroidManifest.xml").isFile }
    private fun backend(name: String) = File(app,
        "src/root/java/lightning/dsp/volumemanager/backend/$name.kt").readText()

    @Test fun gestureSettingsDoNotWaitForSystemUiShellProbe() {
        val sendSettings = backend("LightningDspVolumeManager")
            .substringAfter("private fun sendSettings() {")
            .substringBefore("private fun publish(")
        assertTrue(sendSettings.contains("output.offer(rules.toString())"))
        assertFalse(sendSettings.contains("SystemUiVolumePanelOption.available()"))
        assertFalse(sendSettings.contains("Shell.cmd"))
    }

    @Test fun changedSettingsWakeRootAndBypassPeriodicDiscoveryThrottle() {
        val root = backend("LightningDspVolumeRoot")
        val reader = root.substringAfter("val reader = Thread {")
            .substringBefore("val ownership =")
        assertTrue(reader.contains("else -> latestSettings.set(line)"))
        assertTrue(reader.contains("wakeups.offer(Unit)"))
        assertTrue(root.contains("settingsChanged = true"))
        assertTrue(root.contains("if (!settingsChanged && !refresh && now - lastPlaybackScanAt < 500L) continue"))
    }
}
