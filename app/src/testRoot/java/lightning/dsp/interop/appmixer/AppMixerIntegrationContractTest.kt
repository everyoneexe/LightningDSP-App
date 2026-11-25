package lightning.dsp.interop.appmixer

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Cross-file guards complement fake-process tests; they do not prove device audio. */
class AppMixerIntegrationContractTest {
    private fun read(path: String) = sequenceOf(File("src/$path"), File("app/src/$path"))
        .first { it.isFile }.readText()

    @Test fun serviceRetiresOtherOwnersBeforeAcquiringRelaySessionLease() {
        val source = read("main/java/lightning/dsp/service/RootAudioProcessorService.kt")
        val acquire = source.substringAfter("private fun acquireMixerExclusive()")
            .substringBefore("private fun updateServiceNotification()")
        assertTrue(acquire.indexOf("it?.close()") < acquire.indexOf("retiring.awaitClosed(8_000)"))
        assertTrue(acquire.indexOf("it?.close()") < acquire.indexOf("acquireMixerSessionLease()"))
        assertTrue(acquire.indexOf("acquireMixerSessionLease()") < acquire.indexOf("retiring.awaitClosed(8_000)"))
        assertTrue(acquire.indexOf("retiring.awaitClosed(8_000)") < acquire.indexOf("retired.any { !it.isClosed() }"))
        assertTrue(acquire.contains("uncertainMixerLease = lease"))
        assertTrue(source.contains("else if (app.rootSessionDatabase.isMixerSessionLeaseHeld())"))
        assertTrue(source.contains("Previous Fusion Mode cleanup is pending or uncertain"))
    }

    @Test fun thirdModeSharesExistingAppPowerAndExcludesNormalEngineAdmission() {
        val settings = read("main/java/lightning/dsp/fragment/settings/SettingsAudioFormatFragment.kt")
        val database = read("main/java/lightning/dsp/session/root/RootSessionDatabase.kt")
        val inventory = read("root/java/lightning/dsp/interop/appmixer/AppMixerUidInventory.kt")
        assertTrue(settings.contains(".putBoolean(mixerKey, mixerEnabled).apply()"))
        assertTrue(settings.contains("sessionMode?.isChecked = !globalEnabled && !mixerEnabled"))
        assertTrue(database.contains("!app.isAppMixerMode && id !in supplementalSessionReservations"))
        assertTrue(database.contains("!app.isAppMixerMode && (id <= 0 ||"))
        assertTrue(inventory.contains("SessionDspControlPreferences.preferenceKey(name, uid / 100000, currentUser), true"))
    }

    @Test fun persistedPoweredAppMixerModeStartsProcessorWhenMainActivityOpens() {
        val activity = read("main/java/lightning/dsp/activity/MainActivity.kt")
        assertTrue(activity.contains("app.isEnhancedProcessing ||"))
        assertTrue(activity.contains("app.isAppMixerMode && prefsApp.get(R.string.key_powered_on)"))
        assertTrue(activity.contains("RootAudioProcessorService.startServiceEnhanced(this)"))
    }

    @Test fun relayAcknowledgementDoesNotContainUnboundedUidList() {
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        val acknowledgement = helper.lineSequence().first { it.contains("APP_MIXER:RUNNING session=") }
        assertTrue(acknowledgement.contains("uidCount="))
        assertFalse(acknowledgement.contains("joinToString"))
        assertTrue(helper.contains("kotlin.system.exitProcess(exitCode)"))
    }

    @Test fun supervisedRouteDoesNotPeriodicallyExpireOrRestart() {
        val controller = read("root/java/lightning/dsp/interop/appmixer/AppMixerModeController.kt")
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        assertFalse(controller.contains("reconcile(true)"))
        assertTrue(helper.contains("if (prepareExternalEffect == null)"))
        assertTrue(helper.contains("else Long.MAX_VALUE"))
    }

    @Test fun appMixerDoesNotAbandonSlowDspPreparationAndReportsEndpointState() {
        val preparer = read("root/java/lightning/dsp/interop/appmixer/AppMixerEffectPreparer.kt")
        val engine = read("main/java/lightning/dsp/interop/LightningDspRemoteEngine.kt")
        assertTrue(preparer.contains("PREPARATION_TIMEOUT_MS = 20L * 1_000L"))
        assertTrue(preparer.contains("DSP state preparation timed out"))
        assertTrue(preparer.contains("DSP state preparation was rejected"))
        assertTrue(engine.contains("sessionPreparationStatus()"))
        assertTrue(engine.contains("append(\" control=\").append(canControlEndpoint())"))
        assertTrue(preparer.contains("power=\${runCatching(shouldEnable)"))
    }

    @Test fun localBackendRequiresZeroFrameworkEffectsAfterPreparation() {
        assertEquals(0, AppMixerEffectInventory.expectedAfterPreparation(localDsp = true))
        assertEquals(1, AppMixerEffectInventory.expectedAfterPreparation(localDsp = false))
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        assertTrue(helper.contains("checkNoExistingEffects(expectedCount = AppMixerEffectInventory.expectedAfterPreparation(config.localDsp))"))
        assertTrue(helper.contains("check(count == expectedCount)"))
        assertFalse(helper.contains("allowOwnDiagnostic = true"))
    }

    @Test fun systemSoundCoverageIsOptionalAndModeScoped() {
        val settings = read("main/java/lightning/dsp/fragment/settings/SettingsAudioFormatFragment.kt")
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        val database = read("main/java/lightning/dsp/session/root/RootSessionDatabase.kt")
        assertTrue(settings.contains("isVisible = isRoot() && (!globalEnabled || mixerEnabled)"))
        assertTrue(settings.contains("key_audioformat_include_system_sounds"))
        assertTrue(helper.contains("AudioAttributes.USAGE_NOTIFICATION"))
        assertTrue(helper.contains("AudioAttributes.USAGE_NOTIFICATION_EVENT"))
        assertTrue(helper.contains("AudioAttributes.USAGE_ASSISTANCE_SONIFICATION"))
        assertTrue(helper.contains("if (includeSystemSounds)"))
        assertTrue(database.contains("!app.isGlobalMode && app.getSharedPreferences"))
    }

    @Test fun localDspCanRequestLowLatencyWithoutDuplicatingFrameworkEffects() {
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        val track = helper.substringAfter("player = AudioTrack.Builder()")
            .substringBefore("check(player.state == AudioTrack.STATE_INITIALIZED")
        assertTrue(track.contains("AudioTrack.PERFORMANCE_MODE_NONE"))
        assertTrue(track.contains("if (config.localDsp) AudioTrack.PERFORMANCE_MODE_LOW_LATENCY"))
        assertTrue(helper.indexOf("AppMixerLocalDsp.open(") < helper.indexOf("player = AudioTrack.Builder()"))
        assertTrue(helper.contains("AppMixerFixedDspCapture(relayFrames * AppMixerLatencyPolicy.CHANNELS, dsp::process)"))
        val pumpStartup = helper.substringAfter("beforePump = {").substringBefore("progressIntervalNanos")
        assertTrue(pumpStartup.contains("source.startRecording()"))
        assertTrue(pumpStartup.indexOf("if (!config.localDsp) startCapture()") in 0 until pumpStartup.indexOf("target.play()"))
        assertTrue(pumpStartup.indexOf("target.play()") in 0 until pumpStartup.indexOf("if (config.localDsp) startCapture()"))
        assertTrue(pumpStartup.indexOf("if (config.localDsp) startCapture()") in 0 until pumpStartup.indexOf("APP_MIXER:RUNNING"))
        val preparer = read("root/java/lightning/dsp/interop/appmixer/AppMixerEffectPreparer.kt")
        val localBranch = preparer.substringAfter("if (localDsp) {").substringBefore("return prepareFrameworkEffect")
        assertFalse(localBranch.contains("LightningDspRemoteEngine"))
        assertTrue(localBranch.contains("reportLocalSampleRate(48000f)"))
        val local = read("root/java/lightning/dsp/interop/appmixer/AppMixerLocalDsp.kt")
        assertTrue(local.contains("synchronizeRelayState(force = true)"))
        assertTrue(local.contains("synchronizeRelayState(force = false)"))
        assertTrue(local.contains("worker.awaitTermination(5, TimeUnit.SECONDS)"))
        assertTrue(local.contains("owner.applicationInfo.uid == ownerUid"))
    }

    @Test fun fusionStabilityIsOptInFusionOnlyAndRestartsWithItsBufferPolicy() {
        val settings = read("main/java/lightning/dsp/fragment/settings/SettingsAudioFormatFragment.kt")
        val preferences = read("main/res/xml/app_audio_format_preferences.xml")
        val defaults = read("main/res/values/defaults.xml")
        val controller = read("root/java/lightning/dsp/interop/appmixer/AppMixerModeController.kt")
        val launcher = read("root/java/lightning/dsp/interop/appmixer/AppMixerProcessLauncher.kt")
        val helper = read("root/java/lightning/dsp/interop/appmixer/AppMixerRoutingExperiment.kt")
        assertTrue(settings.contains("fusionOptions?.isVisible = isRoot() && mixerEnabled"))
        assertTrue(preferences.contains("@string/key_audioformat_fusion_stability_mode"))
        assertTrue(defaults.contains("default_audioformat_fusion_stability_mode"))
        assertTrue(defaults.contains("default_audioformat_fusion_stability_mode\" translatable=\"false\">false"))
        assertTrue(controller.contains("stabilityModeKey -> runCatching { executor.execute { if (requested) reconcile(renew = true) } }"))
        assertTrue(launcher.contains("--stability-mode="))
        assertTrue(helper.contains("stabilityMode = args[7].endsWith(\"=1\")"))
        assertTrue(helper.contains("AppMixerPlaybackBufferTuner(recoveryFloor, config.stabilityMode)"))
    }
}
