package lightning.dsp.volumemanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeManagerNotificationContractTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/root/AndroidManifest.xml").isFile }

    @Test fun notificationDescribesTheServiceRatherThanHookReadiness() {
        val service = File(app,
            "src/root/java/lightning/dsp/volumemanager/backend/LightningDspVolumeService.kt").readText()
        assertTrue(service.contains("Volume Manager service active"))
        assertFalse(service.contains("Volume panel enabled"))
    }
}
