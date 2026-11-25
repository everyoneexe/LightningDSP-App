package lightning.dsp.volumemanager.focus;

import android.media.AudioAttributes;
import android.media.AudioManager;

import lightning.dsp.utils.logging.DiagnosticMode;
import lightning.dsp.xposed.HookBackend;
import lightning.dsp.xposed.HookPlatform;
import java.lang.reflect.Method;

/** Optional system_server hook. The runtime property defaults to off. */
public final class DisableAudioFocusHook {
    private static final String PROPERTY = "debug.lightningdsp.disable_audio_focus";
    private static final String READY = "debug.lightningdsp.audio_focus_hook_pid";

    public static void install(HookBackend backend, String packageName, ClassLoader loader) {
        if (!DisableAudioFocusTarget.isFrameworkPackage(packageName)) return;
        Class<?> focusControl = HookPlatform.findClassIfExists(
                "com.android.server.audio.MediaFocusControl", loader);
        if (focusControl == null) {
            HookPlatform.log("LightningDSP AudioFocus: MediaFocusControl missing in " + packageName);
            return;
        }
        try {
            int hooks = 0;
            for (Method method : focusControl.getDeclaredMethods()) {
                if (!"requestAudioFocus".equals(method.getName())) continue;
                backend.before(method, HookBackend.DEFAULT_PRIORITY, param -> {
                    boolean enabled;
                    try {
                        enabled = HookPlatform.propertyBoolean(PROPERTY, false);
                    } catch (Throwable ignored) {
                        return; // Unknown framework behavior: retain the original focus policy.
                    }
                    if (!enabled) return;
                    // Android 17 places an int before AudioAttributes; older releases
                    // place AudioAttributes first. Resolve by type, not argument index.
                    AudioAttributes attributes = null;
                    for (Object argument : param.args) {
                        if (argument instanceof AudioAttributes) {
                            attributes = (AudioAttributes) argument;
                            break;
                        }
                    }
                    if (attributes == null || attributes.getUsage() == AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        return;
                    param.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                });
                hooks++;
            }
            if (hooks == 0) {
                HookPlatform.log("LightningDSP AudioFocus: requestAudioFocus method missing in " + packageName);
                return;
            }
            // A volatile, PID-bound acknowledgement lets the app distinguish an active
            // hook from mere installation or a stale marker after system_server restarts.
            HookPlatform.setProperty(READY, Integer.toString(android.os.Process.myPid()));
            String readyPid = HookPlatform.property(READY, "");
            if (!Integer.toString(android.os.Process.myPid()).equals(readyPid)) {
                HookPlatform.log("LightningDSP AudioFocus: readiness property rejected in " + packageName);
                return;
            }
            if (DiagnosticMode.isEnabled()) HookPlatform.log("LightningDSP AudioFocus hook ready in " + packageName
                    + " pid=" + readyPid + " hooks=" + hooks);
        } catch (Throwable error) {
            HookPlatform.log("LightningDSP AudioFocus: hook setup failed in " + packageName);
            if (DiagnosticMode.isEnabled()) HookPlatform.log(error);
        }
    }

}
