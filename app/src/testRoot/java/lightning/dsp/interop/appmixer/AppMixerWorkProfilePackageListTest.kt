package lightning.dsp.interop.appmixer

import org.junit.Assert.*
import org.junit.Test

class AppMixerWorkProfilePackageListTest {
    @Test fun parsesCompleteProfileMembershipAndPreservesSharedUidPackages() {
        val result = AppMixerWorkProfilePackageList.parse(10, """
            package:android uid:1001000
            package:player.one uid:1010485
            package:player.shared uid:1010485
            package:player.two uid:1010520
        """.trimIndent())

        assertEquals(setOf("player.one", "player.shared"), result?.get(1_010_485))
        assertEquals(setOf("player.two"), result?.get(1_010_520))
        assertFalse(result.orEmpty().containsKey(1_001_000))
    }

    @Test fun malformedOrWrongUserOutputFailsClosed() {
        assertNull(AppMixerWorkProfilePackageList.parse(10, "package:player.one uid:10001"))
        assertNull(AppMixerWorkProfilePackageList.parse(10, "package:player.one"))
        assertNull(AppMixerWorkProfilePackageList.parse(10, ""))
    }

    @Test fun routeUsesRootInventoryForCompleteProfilePackagesWithABoundedCache() {
        val app = sequenceOf(java.io.File("."), java.io.File("app")).first { java.io.File(it, "src/root").isDirectory }
        val source = java.io.File(app, "src/root/java/lightning/dsp/interop/appmixer/AppMixerUidInventory.kt").readText()
        assertTrue(source.contains("ProcessBuilder(\"su\", \"-c\", \"cmd package list packages -U --user \$userId\")"))
        assertTrue(source.contains("WORK_PACKAGE_CACHE_MILLIS = 15_000L"))
        assertTrue(source.contains("membersByUid[uid / 100000]?.containsKey(uid)"))
    }
}
