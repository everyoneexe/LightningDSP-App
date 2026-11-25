package lightning.dsp.xposed;

import android.app.Application;
import android.util.Log;
import java.lang.reflect.Method;

/** Narrow reflection helpers for the Android framework methods used by the hooks. */
public final class HookPlatform {
    private static final String TAG = "LightningDSP_Xposed";
    private static volatile Method propertyGetter;
    private static volatile Method booleanPropertyGetter;
    private static volatile Method propertySetter;
    private static volatile Method applicationGetter;

    private HookPlatform() {}

    public static Class<?> findClass(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return findClass(name, loader); }
        catch (ClassNotFoundException | LinkageError ignored) { return null; }
    }

    public static Method method(Class<?> owner, String name, Class<?>... args)
            throws ReflectiveOperationException {
        return HookReflection.method(owner, name, args);
    }

    public static String property(String name, String fallback) throws ReflectiveOperationException {
        Method getter = propertyGetter;
        if (getter == null) propertyGetter = getter = method(findClass("android.os.SystemProperties", null),
                "get", String.class, String.class);
        return (String) getter.invoke(null, name, fallback);
    }

    public static boolean propertyBoolean(String name, boolean fallback) throws ReflectiveOperationException {
        Method getter = booleanPropertyGetter;
        if (getter == null) booleanPropertyGetter = getter = method(findClass("android.os.SystemProperties", null),
                "getBoolean", String.class, boolean.class);
        return (Boolean) getter.invoke(null, name, fallback);
    }

    public static void setProperty(String name, String value) throws ReflectiveOperationException {
        Method setter = propertySetter;
        if (setter == null) propertySetter = setter = method(findClass("android.os.SystemProperties", null),
                "set", String.class, String.class);
        setter.invoke(null, name, value);
    }

    public static Application currentApplication() throws ReflectiveOperationException {
        Method getter = applicationGetter;
        if (getter == null) applicationGetter = getter = method(findClass("android.app.ActivityThread", null), "currentApplication");
        return (Application) getter.invoke(null);
    }

    public static void log(String message) { Log.i(TAG, message); }
    public static void log(Throwable error) { Log.w(TAG, "Hook operation failed", error); }
}
