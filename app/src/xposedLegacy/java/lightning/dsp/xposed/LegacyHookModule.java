package lightning.dsp.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import lightning.dsp.interop.AudioCompatibilityHook;
import lightning.dsp.volumemanager.backend.SystemUiVolumePanelHook;
import lightning.dsp.volumemanager.focus.DisableAudioFocusHook;

public final class LegacyHookModule implements IXposedHookLoadPackage, HookBackend {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam info) {
        DisableAudioFocusHook.install(this, info.packageName, info.classLoader);
        AudioCompatibilityHook.install(this, info.packageName, info.processName, info.appInfo);
        SystemUiVolumePanelHook.install(this, info.packageName, info.processName);
    }

    @Override public void before(Method method, int priority, Before callback) {
        XposedBridge.hookMethod(method, new XC_MethodHook(priority) {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Call call = new Call(param.thisObject, param.args);
                callback.run(call);
                if (call.shortCircuited()) param.setResult(call.result());
            }
        });
    }

    @Override public void after(Method method, After callback) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                callback.run(new Call(param.thisObject, param.args));
            }
        });
    }

    @Override public Object invokeOriginal(Method method, Object receiver, Object[] args) throws Throwable {
        return XposedBridge.invokeOriginalMethod(method, receiver, args);
    }
}
