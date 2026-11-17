package lightning.dsp.volumemanager

import lightning.dsp.utils.extensions.ContextExtensions.uniquePackageForUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSessionPackageIdentityTest {
    @Test fun fullUidNeverResolvesToThePersonalCopy() {
        assertEquals("com.example.work", uniquePackageForUid(1_010_485, sequenceOf(
            10_485 to "com.example.personal", 1_010_485 to "com.example.work")))
    }

    @Test fun repeatedLauncherActivitiesDoNotMakeOnePackageAmbiguous() {
        assertEquals("com.example.work", uniquePackageForUid(1_010_485, sequenceOf(
            1_010_485 to "com.example.work", 1_010_485 to "com.example.work")))
    }

    @Test fun inaccessibleAndSharedUidPackagesAreNotGuessed() {
        assertNull(uniquePackageForUid(1_010_485, emptySequence()))
        assertNull(uniquePackageForUid(1_010_485, sequenceOf(
            1_010_485 to "com.example.work", 1_010_485 to "com.example.shared")))
        assertNull(uniquePackageForUid(1_010_485, sequenceOf(10_485 to "com.example.work")))
    }
}
