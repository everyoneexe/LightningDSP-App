package lightning.dsp.interop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioCompatibilityLoadGateTest {
    private val app = sequenceOf(File("."), File("app"))
        .first { File(it, "src/root/AndroidManifest.xml").isFile }

    @Test fun disabledOptionDoesNotLoadTheNativeHook() {
        val source = File(app, "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        val handle = source.substringAfter("public static void install(HookBackend backend,")
        val guard = handle.indexOf("if (!aaudioRequested && !sessionReuseEnabled && !sessionPreparationEnabled)")
        val load = handle.indexOf("System.loadLibrary(\"ldsp_aaudio_compat\")")
        assertTrue("the opt-in gate must precede native library loading", guard >= 0 && load > guard)
        assertTrue(handle.substring(guard, load).contains("return;"))
        assertTrue(source.contains("return \"1\".equals(HookPlatform.property"))
    }

    @Test fun sessionPreparationHooksAudioTrackBeforePlaybackWithoutLoadingTheNativePatch() {
        val source = File(app, "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        val handle = source.substringAfter("public static void install(HookBackend backend,")
        assertTrue(handle.indexOf("if (sessionPreparationEnabled) installAudioTrackPreparationHook(backend);") <
            handle.indexOf("System.loadLibrary(\"ldsp_aaudio_compat\")"))
        assertTrue(source.contains("HookPlatform.method(android.media.AudioTrack.class, \"play\""))
        assertTrue(source.contains("HookBackend.LOWEST_PRIORITY, param ->"))
        assertTrue(source.contains("\"prepare_session\""))
        assertTrue(source.contains("Looper.myLooper() == Looper.getMainLooper()"))
    }

    @Test fun sharedEntryRetainsPlatformAndAppIdentityGatesWithoutLegacyApiReferences() {
        val source = File(app, "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        assertFalse(source.contains("de.robv.android.xposed"))
        assertFalse(source.contains("XposedHelpers"))
        assertTrue(source.contains("isPlatformHookTarget(packageName, processName)"))
        assertTrue(source.contains("appId < 10000 || appId >= 20000 || appInfo == null"))
        assertTrue(source.contains("appInfo.flags & ApplicationInfo.FLAG_SYSTEM"))
        assertTrue(source.contains("if (param.receiver instanceof Application) trackFocus"))
        assertTrue(source.contains("backend.after(HookPlatform.method(Application.class"))
    }

    @Test fun nativeInitAlsoRequiresOptInBeforeInstallingHooks() {
        val source = File(app, "src/main/cpp/aaudio-compat/aaudio_compat.cpp").readText()
        val init = source.substringAfter("LoadedCallback native_init(const NativeApi* api)")
        assertTrue(init.indexOf("!hookRequested()) return nullptr;") >= 0)
        assertTrue(init.indexOf("!hookRequested()) return nullptr;") < init.indexOf("observerReady.store("))
        assertTrue(init.contains("return libraryLoaded;"))
        assertFalse("libaaudio entry points must not be inline patched", source.contains("api->hook("))
    }

    @Test fun childLaunchAdapterPreservesTheScopedOptInAndNormalHookChain() {
        val source = File(app, "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        val entry = source.substringAfter("public static void install(HookBackend backend,")
        assertTrue(entry.indexOf("if (!aaudioRequested && !sessionReuseEnabled && !sessionPreparationEnabled)") < entry.indexOf("installLaunchHook(backend);"))
        assertTrue(entry.indexOf("appInfo.flags & ApplicationInfo.FLAG_SYSTEM") < entry.indexOf("installLaunchHook(backend);"))
        assertTrue(source.contains("HookPlatform.findClass(\"java.lang.ProcessImpl\", null), \"start\","))
        assertTrue(source.contains("String[].class, Map.class, String.class, ProcessBuilder.Redirect[].class,"))
        assertTrue(source.contains("boolean.class), HookBackend.LOWEST_PRIORITY"))
        assertTrue(source.contains("() -> new ProcessBuilder().environment()"))
        assertTrue(source.contains("param.args[1] = adapted"))
        assertFalse(source.contains("param.args ="))
        val launch = source.substringAfter("private static void installLaunchHook(HookBackend backend)")
            .substringBefore("public static void install(HookBackend backend,")
        assertFalse(launch.contains("afterHookedMethod"))
        assertFalse(source.contains("invokeOriginalMethod"))
        assertFalse(source.contains("XC_MethodReplacement"))
    }

    @Test fun loadedLibrarySweepStopsAfterAnyCompatibleBackendIsRebound() {
        val source = File(app, "src/root/java/lightning/dsp/interop/AudioCompatibilityHook.java").readText()
        val scan = source.substringAfter("private static void scanLoadedAudioLibraries()")
            .substringBefore("private static void startLoadedLibraryScan()")
        val start = source.substringAfter("private static void startLoadedLibraryScan()")
            .substringBefore("/** Do not even load the native library")

        assertTrue("the sweep must stop on OpenSL as well as AAudio", scan.contains("if (nativeReady())"))
        assertFalse("OpenSL-only apps must not be polled forever waiting for AAudio", scan.contains("if (nativeAAudioReady())"))
        assertTrue("the initial sweep should avoid scheduling retries after a successful backend bind", start.contains("if (nativeReady() || !libraryScanScheduled"))
        assertTrue("the retry loop should stop once a backend bind is reported", start.contains("!aaudioHookRequested() || nativeReady()"))
        val native = File(app, "src/main/cpp/aaudio-compat/aaudio_compat.cpp").readText()
        assertTrue("later dlopen operations remain covered by the registered linker callback", native.contains("return libraryLoaded;"))
    }
}
