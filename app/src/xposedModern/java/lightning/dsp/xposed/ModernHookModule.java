package lightning.dsp.xposed;

import android.os.Build;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import lightning.dsp.interop.AudioCompatibilityHook;
import lightning.dsp.volumemanager.backend.SystemUiVolumePanelHook;
import lightning.dsp.volumemanager.focus.DisableAudioFocusHook;

public final class ModernHookModule extends XposedModule implements HookBackend {
    private volatile String processName;

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        DisableAudioFocusHook.install(this, "android", param.getClassLoader());
    }

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioCompatibilityHook.install(this, param.getPackageName(), processName, param.getApplicationInfo());
        }
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        // Vector dispatches onPackageLoaded starting at Android 10. On
        // Android 9, package-ready still precedes Application.attach.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            AudioCompatibilityHook.install(this, param.getPackageName(), processName, param.getApplicationInfo());
        }
        SystemUiVolumePanelHook.install(this, param.getPackageName(), processName);
    }

    @Override public void before(Method method, int priority, Before callback) {
        hook(method).setPriority(priority).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
            Call call = new Call(chain.getThisObject(), chain.getArgs().toArray());
            callback.run(call);
            return call.shortCircuited() ? call.result() : chain.proceed(call.args);
        });
    }

    @Override public void after(Method method, After callback) {
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
            try {
                return chain.proceed();
            } finally {
                callback.run(new Call(chain.getThisObject(), chain.getArgs().toArray()));
            }
        });
    }

    @Override public Object invokeOriginal(Method method, Object receiver, Object[] args) throws Throwable {
        return getInvoker(method).setType(Invoker.Type.ORIGIN).invoke(receiver, args);
    }
}
