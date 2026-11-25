package lightning.dsp.xposed;

import android.os.Build;
import java.lang.reflect.Method;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

final class HookReflection {
    static Method method(Class<?> owner, String name, Class<?>... args) throws ReflectiveOperationException {
        Method method;
        try {
            method = owner.getDeclaredMethod(name, args);
        } catch (NoSuchMethodException inaccessible) {
            if (Build.VERSION.SDK_INT < 28) throw inaccessible;
            // Look up this member only rather than exempting the host process globally.
            method = HiddenApiBypass.getDeclaredMethod(owner, name, args);
        }
        method.setAccessible(true);
        return method;
    }
}
