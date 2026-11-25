package lightning.dsp.volumemanager.backend;

import android.os.Process;
import android.os.Build;
import android.os.SystemClock;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import lightning.dsp.utils.logging.DiagnosticMode;
import lightning.dsp.volumemanager.surface.SystemUiVolumeSurfaceHost;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Keep verified SystemUI volume dispatch while a live app-owned surface is hosted in its window. */
public final class SystemUiVolumePanelHook implements IXposedHookLoadPackage {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String[] VOLUME_DIALOG_CONTROLLERS = {
        "com.android.systemui.volume.VolumeDialogControllerImpl",
        "com.android.systemui.volume.VolumeDialogControl"
    };
    private static final String LEASE_PROPERTY = "debug.lightningdsp.volume_panel_lease_ms";
    private static final String ENABLED_PROPERTY = "debug.lightningdsp.suppress_volume_panel";
    private static final String READY_PROPERTY = "debug.lightningdsp.volume_panel_hook_pid";
    private static final long LEASE_TTL_MS = 6_000L;
    private static final long CONTROLLER_RETRY_MS = 500L;
    // SystemUI can be relaunched after boot or under memory pressure; Samsung and
    // newer Android builds may publish its Application/controller later than 2 s.
    // Keep retries bounded to 10 s per initialization stage, then fail open to stock.
    private static final int CONTROLLER_ATTEMPTS = 20;
    private static final int FLAG_SHOW_UI = 1;
    private static boolean volumeHookInstalled;

    /** Only hook the AOSP contract: older releases use (int, int), newer ones add a boolean. */
    private static boolean isVolumeChangedMethod(Method method) {
        if (!"onVolumeChangedW".equals(method.getName())) return false;
        Class<?>[] parameters = method.getParameterTypes();
        return (parameters.length == 2 || parameters.length == 3)
                && parameters[0] == int.class && parameters[1] == int.class
                && (parameters.length == 2 || parameters[2] == boolean.class);
    }

    private static boolean suppressionActive() {
        try {
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            if (!(Boolean) XposedHelpers.callStaticMethod(properties, "getBoolean", ENABLED_PROPERTY, false))
                return false;
            String value = (String) XposedHelpers.callStaticMethod(properties, "get", LEASE_PROPERTY, "0");
            long refreshedAt = Long.parseLong(value);
            long now = SystemClock.uptimeMillis();
            return refreshedAt > 0 && now >= refreshedAt && now - refreshedAt <= LEASE_TTL_MS;
        } catch (Throwable ignored) {
            return false; // Missing/stale state preserves the native SystemUI volume panel.
        }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam info) {
        // SystemUI also runs auxiliary processes (for example :screenshot).
        // Only its main process owns the volume dialog and may host our window.
        if (!SYSTEM_UI.equals(info.packageName) || !SYSTEM_UI.equals(info.processName)) return;
        if (Build.VERSION.SDK_INT < 30) return;
        clearReadinessMarker();
        // Vector's legacy bridge can load this hook after SystemUI's Application.onCreate.
        // A hook placed on onCreate would then never run, leaving the stock dialog forever.
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> initializeSystemUi(0), CONTROLLER_RETRY_MS);
    }

    /** Do not leave a dead SystemUI PID advertised while this process reinitializes. */
    private static void clearReadinessMarker() {
        try {
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            XposedHelpers.callStaticMethod(properties, "set", READY_PROPERTY, "0");
        } catch (Throwable error) {
            XposedBridge.log("LightningDSP SystemUI volume panel: could not clear stale readiness marker");
        }
    }

    private static void initializeSystemUi(int attempt) {
        if (volumeHookInstalled) return;
        Application application = null;
        try {
            Class<?> activityThread = XposedHelpers.findClass("android.app.ActivityThread", null);
            application = (Application) XposedHelpers.callStaticMethod(activityThread, "currentApplication");
        } catch (Throwable ignored) { /* ActivityThread may not publish the Application yet. */ }
        if (application == null) {
            if (attempt + 1 < CONTROLLER_ATTEMPTS) {
                new Handler(Looper.getMainLooper()).postDelayed(
                    () -> initializeSystemUi(attempt + 1), CONTROLLER_RETRY_MS);
            } else {
                XposedBridge.log("LightningDSP SystemUI volume panel: application unavailable after startup retries; native behavior retained");
            }
            return;
        }
        SystemUiVolumeSurfaceHost.install(application);
        installVolumeController(application.getClassLoader(), 0);
    }

    /** SystemUI's concrete controller is not reliably resolvable during handleLoadPackage. */
    private static void installVolumeController(ClassLoader loader, int attempt) {
        if (volumeHookInstalled) return;
        try {
            boolean samsung = "samsung".equalsIgnoreCase(Build.MANUFACTURER);
            Class<?> control = null;
            int candidateCount = samsung ? VOLUME_DIALOG_CONTROLLERS.length : 1;
            for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                String name = VOLUME_DIALOG_CONTROLLERS[candidateIndex];
                Class<?> candidate = XposedHelpers.findClassIfExists(name, loader);
                if (candidate == null) continue;
                for (Method method : candidate.getDeclaredMethods()) {
                    if (isVolumeChangedMethod(method)) { control = candidate; break; }
                }
                if (control != null) break;
            }
            if (control == null) {
                if (attempt + 1 < CONTROLLER_ATTEMPTS) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> installVolumeController(loader, attempt + 1), CONTROLLER_RETRY_MS);
                } else {
                    XposedBridge.log("LightningDSP SystemUI volume panel: controller unavailable after startup retries; native behavior retained");
                }
                return;
            }
            int hooks = 0;
            for (Method method : control.getDeclaredMethods()) {
                if (!isVolumeChangedMethod(method)) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (Build.VERSION.SDK_INT < 30) return;
                        if (param.args == null || param.args.length < 2 || !(param.args[1] instanceof Integer)) return;
                        // Capture the exact platform callback, not a second volume adjustment. The
                        // root proxy may already have stripped SHOW_UI before SystemUI receives it.
                        Looper callbackLooper = Looper.myLooper();
                        if (callbackLooper != null) {
                            final Handler callbackHandler = new Handler(callbackLooper);
                            final Object controller = param.thisObject;
                            final Object[] originalArgs = param.args.clone();
                            originalArgs[1] = ((Integer) originalArgs[1]) | FLAG_SHOW_UI;
                            SystemUiVolumeSurfaceHost.setStockFallback(() -> callbackHandler.post(() -> {
                                try { XposedBridge.invokeOriginalMethod(method, controller, originalArgs); }
                                catch (Throwable ignored) { /* Unsupported native callback remains untouched. */ }
                            }));
                        }
                        int flags = (Integer) param.args[1];
                        if (!suppressionActive()) {
                            if (!SystemUiVolumeSurfaceHost.isReady() && (flags & FLAG_SHOW_UI) != 0)
                                SystemUiVolumeSurfaceHost.warmFromVolumeEvent();
                            return;
                        }
                        if (!SystemUiVolumeSurfaceHost.isReady()) {
                            if ((flags & FLAG_SHOW_UI) != 0) {
                                // Queue this request through the bounded surface attach. On
                                // failure the host replays this exact stock callback once.
                                SystemUiVolumeSurfaceHost.showFromVolumeEvent();
                                param.args[1] = flags & ~FLAG_SHOW_UI;
                            }
                            return;
                        }
                        if ((flags & FLAG_SHOW_UI) != 0) {
                            SystemUiVolumeSurfaceHost.showFromVolumeEvent();
                            param.args[1] = flags & ~FLAG_SHOW_UI;
                        }
                    }
                });
                hooks++;
            }
            if (hooks == 0) {
                XposedBridge.log("LightningDSP SystemUI volume panel: supported onVolumeChangedW signature not found; native behavior retained");
                return;
            }
            // Only advertise readiness after a real hook was installed. The app compares
            // this PID with the live SystemUI process before allowing the option to be enabled.
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            String pid = Integer.toString(Process.myPid());
            XposedHelpers.callStaticMethod(properties, "set", READY_PROPERTY, pid);
            String readyPid = (String) XposedHelpers.callStaticMethod(properties, "get", READY_PROPERTY, "");
            if (!pid.equals(readyPid)) {
                XposedBridge.log("LightningDSP SystemUI volume panel: readiness property rejected; native behavior retained");
                return;
            }
            volumeHookInstalled = true;
            if (DiagnosticMode.isEnabled()) XposedBridge.log("LightningDSP SystemUI volume panel hook ready pid=" + Process.myPid() + " hooks=" + hooks);
        } catch (Throwable error) {
            XposedBridge.log("LightningDSP SystemUI volume panel hook unavailable; native behavior retained");
            if (DiagnosticMode.isEnabled()) XposedBridge.log(error);
        }
    }
}
