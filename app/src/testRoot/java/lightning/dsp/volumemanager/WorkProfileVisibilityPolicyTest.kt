package lightning.dsp.volumemanager

import java.io.File
import lightning.dsp.volumemanager.ui.WorkProfileVisibilityPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkProfileVisibilityPolicyTest {
    @Test fun accessibleWorkAppsAreVisibleByDefaultWithoutChangingPersonalApps() {
        val showWorkApps = WorkProfileVisibilityPolicy.DEFAULT_SHOW_WORK_APPS
        assertTrue(WorkProfileVisibilityPolicy.includes(workProfile = true, showWorkProfileApps = showWorkApps))
        assertTrue(WorkProfileVisibilityPolicy.includes(workProfile = false, showWorkProfileApps = showWorkApps))
    }

    @Test fun hidingWorkAppsDoesNotHidePersonalCopyOfTheSamePackage() {
        assertFalse(WorkProfileVisibilityPolicy.includes(workProfile = true, showWorkProfileApps = false))
        assertTrue(WorkProfileVisibilityPolicy.includes(workProfile = false, showWorkProfileApps = false))
    }

    @Test fun controlIsOnlyOfferedWhenAnAccessibleWorkAppIsPresentAndRowsAreBadged() {
        val app = sequenceOf(File("."), File("app")).first { File(it, "src/root/AndroidManifest.xml").isFile }
        val compose = File(app, "src/root/java/lightning/dsp/volumemanager/ui/LightningVolumeComposeUi.kt").readText()
        assertTrue(compose.contains("if (hasWorkProfileApps) item(key = \"work_profile_visibility\")"))
        assertTrue(compose.contains("\"show_work_profile_apps\", WorkProfileVisibilityPolicy.DEFAULT_SHOW_WORK_APPS"))
        assertTrue(compose.contains("WorkProfileBadge"))
        assertTrue(compose.contains("Icons.Default.Work"))
        assertTrue(compose.contains("hasWorkProfileApps && !processingModes.first"))
        assertTrue(compose.contains("Process work profile audio"))
        assertTrue(compose.contains("LightningDspVolumeManager::setProcessWorkProfileAudio"))

        val sessionDatabase = File(app, "src/main/java/lightning/dsp/session/root/RootSessionDatabase.kt").readText()
        assertTrue(sessionDatabase.contains("workProfileAudioAllowed"))
        assertTrue(sessionDatabase.contains("getBoolean(\"process_work_profile_audio\", false)"))
        assertTrue(sessionDatabase.contains("isPackageDspEnabled(packageName, session.uid) && enabled"))
        assertTrue(sessionDatabase.contains("if (app.isGlobalMode) return true"))
        assertTrue(sessionDatabase.contains("if (app.isAppMixerMode) return false"))
        assertTrue(compose.contains("val sessionMode = !processingModes.first && !processingModes.second"))
    }
}
