package lightning.dsp.xposed;

import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

final class HookReflection {
    static Method method(Class<?> owner, String name, Class<?>... args) {
        return XposedHelpers.findMethodExact(owner, name, args);
    }
}
