package lightning.dsp.interop

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DiagnosticModeContractTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private fun read(path: String) = File(app, path).readText()

    @Test fun diagnosticsAreOffByDefaultAndRootSettingRequiresVerifiedTransaction() {
        assertTrue(read("src/main/res/values/defaults.xml").contains("name=\"default_diagnostic_mode\" translatable=\"false\">false"))
        val option = read("src/root/java/lightning/dsp/interop/DiagnosticModeOption.kt")
        assertTrue(option.contains("AAudioCompatibilityTransaction.apply"))
        assertTrue(option.contains("chmod 700"))
        assertTrue(option.contains("chmod 600"))
        assertFalse(option.contains("persist."))
        assertFalse(option.contains("reboot" + "("))
        val settings = read("src/main/java/lightning/dsp/fragment/settings/SettingsMiscFragment.kt")
        assertTrue(settings.contains("isVisible = isRoot()"))
        assertTrue(settings.contains("withContext(Dispatchers.IO)"))
        assertTrue(settings.contains("DiagnosticModeOption.set"))
    }

    @Test fun rootCommandUpdatesBothStatesAndActuallyVerifiesTheProperty() {
        // Mock every filesystem/property command: no root or device state is touched.
        val stubs = """
            mkdir() { :; }
            chown() { :; }
            chmod() { :; }
            touch() { marker=1; }
            rm() { marker=0; }
            setprop() { value="${'$'}2"; }
            getprop() { printf '%s' "${'$'}value"; }
            marker=0
            value=0
        """.trimIndent()
        for (enabled in listOf(false, true)) {
            val process = ProcessBuilder("sh", "-c", stubs + "\n" + DiagnosticModeOption.command(enabled))
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            assertEquals("LDSP_DIAGNOSTICS:${if (enabled) 1 else 0}", output.trim())
        }
        val mismatched = stubs + "\ngetprop() { printf 'wrong'; }\n" + DiagnosticModeOption.command(true)
        val failed = ProcessBuilder("sh", "-c", mismatched).redirectErrorStream(true).start()
        assertFalse(failed.inputStream.bufferedReader().readText().contains("LDSP_DIAGNOSTICS:"))
        assertNotEquals(0, failed.waitFor())
    }

    @Test fun releaseTreesAreGatedAndRawCommandsAndPointersAreNotLogged() {
        val app = read("src/main/java/lightning/dsp/MainApplication.kt")
        assertFalse(app.contains("Timber.plant(DebugTree())"))
        assertTrue(app.contains("DiagnosticTree(DebugTree())"))
        assertTrue(app.contains("DiagnosticTree(FileLoggerTree.Builder()"))
        assertFalse(read("src/root/java/lightning/dsp/flavor/RootShellImpl.kt").contains("root command:"))
        assertFalse(read("src/main/cpp/liblightningdsp-wrapper/LightningDspWrapper.cpp").contains("memory allocated at %lx"))
        assertTrue(read("src/main/cpp/liblightningdsp-wrapper/Log.h").contains("ldsp_diagnostics::enabled()"))
    }

    @Test fun relayProtocolAndPlaybackTuningRemainIndependentFromOptionalLogs() {
        val relay = read("src/root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        val progress = relay.substringAfter("onProgress = { now, progress, readGap ->")
        val diagnosticGate = "if (diagnosticEnabled && now - lastDiagnostic >= 5_000_000_000L)"
        assertTrue(progress.contains(diagnosticGate))
        assertTrue(progress.substringBefore(diagnosticGate).contains("runCatching { tunePlaybackBuffer() }"))
        assertTrue(progress.substringBefore(diagnosticGate).contains("diagnosticEnabled = enabledNow"))
        assertTrue(progress.substringAfter(diagnosticGate).contains("source.getTimestamp("))
        for (token in listOf("APP_MIXER:SESSION", "APP_MIXER:ROUTING", "APP_MIXER:RUNNING", "APP_MIXER:RELEASED")) {
            assertTrue(relay.contains(token))
        }
        val supervisor = read("src/root/java/lightning/dsp/interop/appmixer/AppMixerSupervisor.kt")
        assertTrue(supervisor.contains("DiagnosticMode.isEnabled()"))
        val timingBranch = supervisor.substringAfter("if (fusionTimingDiagnostic.matches(diagnostic))")
            .substringBefore("else if (diagnostic.startsWith")
        assertTrue(supervisor.contains("if (fusionTimingDiagnostic.matches(diagnostic))"))
        assertTrue(timingBranch.contains("DiagnosticMode.isEnabled()"))
        assertFalse(timingBranch.contains("Log.w("))
        assertTrue(supervisor.contains("routeMayBeRegistered.set(true)"))
    }
}
