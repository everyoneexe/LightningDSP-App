package lightning.dsp.interop.appmixer

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import lightning.dsp.session.shared.SessionDspControlPreferences

class AppMixerWorkProfileTest {
    @Test fun personalAndWorkCopiesHaveIndependentPower() {
        val personal = SessionDspControlPreferences.preferenceKey("same.app", 0, 0)
        val work = SessionDspControlPreferences.preferenceKey("same.app", 10, 0)
        assertNotEquals(personal, work)
        val apps = listOf(AppMixerUidSelection.App(10001, "same.app", true),
            AppMixerUidSelection.App(1010001, "same.app", false))
        assertEquals(setOf(0, 10002, 1010001), AppMixerUidSelection.exclusions(apps, 10002, false, 2))
    }

    @Test fun userRulesConsumeTheirOwnCriterionBudget() {
        assertEquals(15, AppMixerExperimentConfig.maxExcludedUids(false, 2))
        assertEquals(12, AppMixerExperimentConfig.maxExcludedUids(true, 2))
        assertEquals(setOf(0, 10), AppMixerExperimentConfig.parseUserIds("0,10"))
        assertThrows(IllegalArgumentException::class.java) { AppMixerExperimentConfig.parseUserIds("0,0") }
        assertThrows(IllegalArgumentException::class.java) { AppMixerExperimentConfig.parseUserIds("-1") }
        assertThrows(IllegalArgumentException::class.java) {
            AppMixerExperimentConfig.parseExcludedUids("--exclude-uids=0," + (10001..10012).joinToString(","), true, 2)
        }
    }

    @Test fun profileRoutingDefaultsOnAndUpdatesTheExistingRelay() {
        val app = sequenceOf(File("."), File("app")).first { File(it, "src/root").isDirectory }
        fun source(name: String) = File(app, "src/root/java/lightning/dsp/interop/appmixer/$name.kt").readText()
        val inventory = source("AppMixerUidInventory")
        assertTrue(inventory.contains("getBoolean(\"process_work_profile_audio\", true)"))
        assertTrue(inventory.contains("launcher.profiles"))
        assertTrue(inventory.contains("SessionDspControlPreferences.preferenceKey"))
        assertTrue(source("AppMixerModeController").contains("supervisor.updateRules(next, nextSystemSounds, nextUsers)"))
        assertTrue(source("AppMixerRoutingExperiment").contains("require(config.userId in users)"))
        assertTrue(source("AppMixerRoutingExperiment").contains("for (user in users.sorted())"))
    }

    @Test fun workProfileInventoryUsesCompleteRootMembershipAndKeepsCoverageStatusVisible() {
        val app = sequenceOf(File("."), File("app")).first { File(it, "src/root").isDirectory }
        val inventory = File(app, "src/root/java/lightning/dsp/interop/appmixer/AppMixerUidInventory.kt").readText()
        val controller = File(app, "src/root/java/lightning/dsp/interop/appmixer/AppMixerModeController.kt").readText()
        assertTrue(inventory.contains("ProcessBuilder(\"su\", \"-c\", \"cmd package list packages -U --user \$userId\")"))
        assertTrue(inventory.contains("val users = if (workIncluded) workInventory!!.userIds + currentUser else setOf(currentUser)"))
        assertTrue(inventory.contains("workProfileIncluded: Boolean"))
        assertTrue(controller.contains("work-profile audio is excluded because its app inventory is unavailable"))
        assertTrue(controller.contains("nextWorkProfileIncluded"))
    }
}
