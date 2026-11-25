package lightning.dsp.xposed;

import java.lang.reflect.Method;

/** Only the hook operations used by LightningDSP, independent of the framework API. */
public interface HookBackend {
    int DEFAULT_PRIORITY = 50;
    int LOWEST_PRIORITY = Integer.MIN_VALUE;

    interface Before { void run(Call call) throws Throwable; }
    interface After { void run(Call call) throws Throwable; }

    final class Call {
        public final Object receiver;
        public final Object[] args;
        private boolean shortCircuited;
        private Object result;

        Call(Object receiver, Object[] args) {
            this.receiver = receiver;
            this.args = args;
        }

        public void setResult(Object result) {
            this.result = result;
            shortCircuited = true;
        }

        boolean shortCircuited() { return shortCircuited; }
        Object result() { return result; }
    }

    void before(Method method, int priority, Before callback) throws Throwable;
    void after(Method method, After callback) throws Throwable;
    Object invokeOriginal(Method method, Object receiver, Object[] args) throws Throwable;
}
