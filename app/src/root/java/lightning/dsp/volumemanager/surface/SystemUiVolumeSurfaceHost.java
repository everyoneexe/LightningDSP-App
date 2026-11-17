package lightning.dsp.volumemanager.surface;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import de.robv.android.xposed.XposedBridge;

/** A SystemUI-owned volume window; app-owned views are embedded, never executed in SystemUI. */
public final class SystemUiVolumeSurfaceHost {
    public static final String PREPARE = "com.lightning.dsp.action.VOLUME_SURFACE_PREPARE";
    public static final String SHOW = "com.lightning.dsp.action.VOLUME_SURFACE_SHOW";
    public static final String HIDE = "com.lightning.dsp.action.VOLUME_SURFACE_HIDE";
    private static final String APP = "com.lightning.dsp";
    private static final String PERMISSION = "com.lightning.dsp.permission.VOLUME_SURFACE_HOST";
    private static final long IDLE_MS = 3_300L;
    private static final long FADE_MS = 500L;
    private static final long REQUEST_TIMEOUT_MS = 2_000L;
    private static final long HEARTBEAT_MS = 2_000L;
    private static volatile SystemUiVolumeSurfaceHost instance;
    private static volatile Runnable stockFallback;
    private static int warnings;

    private static synchronized void warn(String stage, Throwable error) {
        if (warnings++ >= 4) return;
        XposedBridge.log("LightningDSP SystemUI volume surface unavailable at " + stage + ": " +
                (error == null ? "unavailable" : error.getClass().getSimpleName()) + "; stock UI retained");
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final KeyguardManager keyguard;
    private final IBinder owner = new Binder();
    private IVolumePanelSurfaceService service;
    private IBinder serviceBinder;
    private WindowManager windows;
    private FrameLayout window;
    private SurfaceView surface;
    private WindowManager.LayoutParams layout;
    private boolean bound;
    private boolean attaching;
    private volatile boolean ready;
    private boolean visible;
    private boolean requestedShow;
    private volatile boolean renderingLocked = true;
    private volatile boolean privacyListenerReady;
    private volatile boolean listenerLocked = true;
    private boolean touching;
    private boolean hovering;
    private boolean remoteInteracting;
    private long generation;
    private long requestStarted;
    private long lastBindAttempt;
    private long lastInteraction;
    private volatile int appUid = -1;

    private SystemUiVolumeSurfaceHost(Context context) {
        this.context = context.getApplicationContext();
        keyguard = context.getSystemService(KeyguardManager.class);
    }

    /** Called only in the scoped SystemUI application process. Unsupported APIs retain stock UI. */
    public static void install(Context context) {
        if (Build.VERSION.SDK_INT < 30 || !"com.android.systemui".equals(context.getPackageName())) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (instance != null) return;
            try {
                SystemUiVolumeSurfaceHost host = new SystemUiVolumeSurfaceHost(context);
                host.register();
                instance = host;
            } catch (Throwable error) {
                warn("registration", error);
                // A failed host must not suppress the stock volume window.
            }
        });
    }

    public static boolean isReady() {
        SystemUiVolumeSurfaceHost host = instance;
        return host != null && host.ready && host.renderingLocked == host.presentationLocked();
    }

    /** Captured from a verified controller callback, including its original looper. */
    public static void setStockFallback(Runnable fallback) { stockFallback = fallback; }

    public static void showFromVolumeEvent() {
        SystemUiVolumeSurfaceHost host = instance;
        if (host != null) host.main.post(host::show);
    }

    /** Prepare only: the event that found an unavailable surface must retain its stock UI. */
    public static void warmFromVolumeEvent() {
        SystemUiVolumeSurfaceHost host = instance;
        if (host != null) host.main.post(() -> {
            if (!host.ready && host.optedIn()) {
                host.requestedShow = false;
                host.prepare();
            }
        });
    }

    private void register() {
        IntentFilter commands = new IntentFilter();
        commands.addAction(PREPARE);
        commands.addAction(SHOW);
        commands.addAction(HIDE);
        BroadcastReceiver commandReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context unused, Intent intent) {
                String action = intent.getAction();
                if (HIDE.equals(action)) { stop(false); return; }
                if (!optedIn()) { stop(false); return; }
                if (SHOW.equals(action)) show(); else prepare();
            }
        };
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(commandReceiver, commands, PERMISSION, main, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(commandReceiver, commands, PERMISSION, main);

        IntentFilter state = new IntentFilter();
        state.addAction(Intent.ACTION_SCREEN_OFF);
        state.addAction(Intent.ACTION_SCREEN_ON);
        state.addAction(Intent.ACTION_USER_PRESENT);
        state.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        state.addAction("android.intent.action.USER_SWITCHED");
        BroadcastReceiver stateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context unused, Intent intent) {
                // Remove the old surface before any privacy/display transition; never unlock keyguard.
                stop(false);
                if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) &&
                        !"android.intent.action.USER_SWITCHED".equals(intent.getAction()) && optedIn()) prepare();
            }
        };
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(stateReceiver, state, null, main, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(stateReceiver, state, null, main);
        if (Build.VERSION.SDK_INT >= 33 && keyguard != null) {
            try {
                listenerLocked = keyguard.isKeyguardLocked() || keyguard.isDeviceLocked();
                keyguard.addKeyguardLockedStateListener(command -> main.post(command), locked -> {
                    listenerLocked = locked;
                    if (window != null && ((locked && !renderingLocked) ||
                            renderingLocked != presentationLocked())) {
                        stop(false);
                        if (optedIn()) prepare();
                    }
                });
                privacyListenerReady = true;
            } catch (Throwable ignored) { /* Without a trusted lock listener show only restricted content. */ }
        }
    }

    private boolean presentationLocked() {
        // Older/unsupported keyguard listeners cannot safely host private app rows above keyguard.
        if (!privacyListenerReady || keyguard == null) return true;
        try { return listenerLocked || keyguard.isKeyguardLocked() || keyguard.isDeviceLocked(); }
        catch (Throwable ignored) { return true; }
    }

    private boolean optedIn() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (Boolean) properties.getDeclaredMethod("getBoolean", String.class, boolean.class)
                    .invoke(null, "debug.lightningdsp.suppress_volume_panel", false);
        } catch (Throwable ignored) { return false; }
    }

    private void prepare() {
        if (!optedIn()) { stop(false); return; }
        if (window != null && renderingLocked != presentationLocked()) {
            boolean pendingShow = requestedShow;
            releaseWindow();
            requestedShow = pendingShow;
        }
        if (service != null) { attachWindow(); return; }
        if (bound) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastBindAttempt < REQUEST_TIMEOUT_MS) { fail(); return; }
        lastBindAttempt = now;
        try {
            appUid = context.getPackageManager().getApplicationInfo(APP, 0).uid;
            Intent bind = new Intent().setComponent(new ComponentName(APP,
                    "lightning.dsp.volumemanager.surface.VolumePanelSurfaceService"));
            bound = context.bindService(bind, connection, Context.BIND_AUTO_CREATE);
            if (!bound) { warn("binding", null); fail(); }
            else main.postDelayed(bindTimeout, REQUEST_TIMEOUT_MS);
        } catch (Throwable error) { warn("binding", error); fail(); }
    }

    private final Runnable bindTimeout = () -> { if (service == null || !ready) fail(); };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!bound || !APP.equals(name.getPackageName()) ||
                    !"lightning.dsp.volumemanager.surface.VolumePanelSurfaceService".equals(name.getClassName())) {
                fail(); return;
            }
            try {
                serviceBinder = binder;
                binder.linkToDeath(serviceDeath, 0);
                service = IVolumePanelSurfaceService.Stub.asInterface(binder);
                attachWindow();
            } catch (Throwable ignored) { fail(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { fail(); }
        @Override public void onBindingDied(ComponentName name) { fail(); }
        @Override public void onNullBinding(ComponentName name) { fail(); }
    };
    private final IBinder.DeathRecipient serviceDeath = () -> main.post(this::fail);

    private void attachWindow() {
        if (window != null || service == null) return;
        try {
            renderingLocked = presentationLocked();
            int type = WindowManager.LayoutParams.class.getField("TYPE_VOLUME_OVERLAY").getInt(null);
            DisplayManager displays = context.getSystemService(DisplayManager.class);
            Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) throw new IllegalStateException("Default display unavailable");
            Context windowContext = context.createDisplayContext(display).createWindowContext(type, null);
            windows = windowContext.getSystemService(WindowManager.class);
            android.util.DisplayMetrics metrics = windowContext.getResources().getDisplayMetrics();
            int margin = Math.max(1, Math.round(16f * metrics.density));
            int width = Math.min(Math.round(420f * metrics.density), Math.max(1, metrics.widthPixels - 2 * margin));
            int height = Math.min(Math.round(600f * metrics.density), Math.max(1, metrics.heightPixels - 4 * margin));
            layout = new WindowManager.LayoutParams(width, height, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.CENTER;
            layout.alpha = 0f;
            layout.setTitle("LightningDSP volume surface");
            window = new FrameLayout(windowContext) {
                @Override public boolean dispatchTouchEvent(MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: touching = true; interaction(); break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: touching = false; interaction(); break;
                        case MotionEvent.ACTION_MOVE: interaction(); break;
                        case MotionEvent.ACTION_OUTSIDE: hide(); return true;
                    }
                    return super.dispatchTouchEvent(event);
                }
                @Override public boolean dispatchHoverEvent(MotionEvent event) {
                    hovering = event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT;
                    interaction();
                    return super.dispatchHoverEvent(event);
                }
            };
            surface = new SurfaceView(windowContext);
            surface.setZOrderOnTop(true);
            surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            surface.getHolder().addCallback(surfaceCallbacks);
            window.addView(surface, new FrameLayout.LayoutParams(-1, -1));
            generation++;
            attaching = false;
            windows.addView(window, layout);
            requestStarted = SystemClock.uptimeMillis();
            main.removeCallbacks(heartbeat);
            main.post(heartbeat);
        } catch (Throwable error) { warn("window", error); fail(); }
    }

    private final SurfaceHolder.Callback surfaceCallbacks = new SurfaceHolder.Callback() {
        @Override public void surfaceCreated(SurfaceHolder holder) { requestSurface(); }
        @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (!attaching) requestSurface();
        }
        @Override public void surfaceDestroyed(SurfaceHolder holder) {
            ready = false;
            heartbeat(false);
        }
    };

    private void requestSurface() {
        if (attaching || service == null || surface == null || layout == null) return;
        IBinder hostToken = surface.getHostToken();
        if (hostToken == null) return;
        try {
            attaching = true;
            int displayId = surface.getDisplay().getDisplayId();
            service.attach(owner, hostToken, displayId, layout.width, layout.height,
                    generation, renderingLocked, callback);
        } catch (Throwable ignored) { fail(); }
    }

    private final IVolumePanelSurfaceCallback callback = new IVolumePanelSurfaceCallback.Stub() {
        private boolean trusted() { return Binder.getCallingUid() == appUid && appUid >= 0; }
        @Override public void surfaceReady(long resultGeneration, SurfaceControlViewHost.SurfacePackage result,
                                          boolean locked) {
            if (!trusted()) { if (result != null) result.release(); return; }
            main.post(() -> {
                if (resultGeneration != generation || surface == null || window == null ||
                        locked != renderingLocked || renderingLocked != presentationLocked() || !optedIn()) {
                    if (result != null) result.release();
                    if (resultGeneration == generation) fail();
                    return;
                }
                try {
                    // SurfaceView takes ownership of the transferred SurfacePackage.
                    surface.setChildSurfacePackage(result);
                    ready = surface.isAttachedToWindow();
                    if (!ready) { fail(); return; }
                    main.removeCallbacks(bindTimeout);
                    heartbeat(true);
                    if (requestedShow) reveal();
                } catch (Throwable error) { warn("surface", error); fail(); }
            });
        }
        @Override public void surfaceFailed(long resultGeneration) {
            if (trusted()) main.post(() -> { if (resultGeneration == generation) fail(); });
        }
        @Override public void dismiss(long resultGeneration) {
            if (trusted()) main.post(() -> { if (resultGeneration == generation) hide(); });
        }
        @Override public void interaction(long resultGeneration, boolean active) {
            if (trusted()) main.post(() -> {
                if (resultGeneration != generation) return;
                remoteInteracting = active;
                SystemUiVolumeSurfaceHost.this.interaction();
            });
        }
    };

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (window == null || service == null) return;
            if (!optedIn() || renderingLocked != presentationLocked()) { stop(false); return; }
            if (!ready && SystemClock.uptimeMillis() - requestStarted >= REQUEST_TIMEOUT_MS) { fail(); return; }
            if (ready) {
                if (!window.isAttachedToWindow()) { fail(); return; }
                heartbeat(true);
            }
            main.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private void heartbeat(boolean attached) {
        if (service == null) return;
        try { service.heartbeat(owner, generation, attached, renderingLocked); }
        catch (Throwable ignored) { main.post(this::fail); }
    }

    private void show() {
        if (!optedIn()) { stop(false); return; }
        requestedShow = true;
        if (ready && window != null && renderingLocked == presentationLocked()) reveal();
        else prepare();
    }

    private void reveal() {
        if (!ready || window == null || layout == null || renderingLocked != presentationLocked()) { fail(); return; }
        try {
            visible = true;
            requestedShow = false;
            window.animate().cancel();
            window.setAlpha(1f);
            layout.alpha = 1f;
            layout.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            windows.updateViewLayout(window, layout);
            interaction();
        } catch (Throwable ignored) { fail(); }
    }

    private void interaction() {
        lastInteraction = SystemClock.uptimeMillis();
        main.removeCallbacks(dismissTimeout);
        if (visible) main.postDelayed(dismissTimeout, IDLE_MS);
    }

    private final Runnable dismissTimeout = new Runnable() {
        @Override public void run() {
            if (!visible || window == null) return;
            long remaining = IDLE_MS - (SystemClock.uptimeMillis() - lastInteraction);
            if (touching || hovering || remoteInteracting || window.hasFocus()) {
                main.postDelayed(this, IDLE_MS);
            } else if (remaining > 0) main.postDelayed(this, remaining);
            else hide();
        }
    };

    private void hide() {
        requestedShow = false;
        visible = false;
        touching = false;
        hovering = false;
        remoteInteracting = false;
        main.removeCallbacks(dismissTimeout);
        if (window == null || layout == null) return;
        try {
            // Remove touch first; the fading window must never leave invisible controls active.
            layout.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            windows.updateViewLayout(window, layout);
            final FrameLayout oldWindow = window;
            final long oldGeneration = generation;
            window.animate().cancel();
            window.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                if (window != oldWindow || generation != oldGeneration || visible) return;
                layout.alpha = 0f;
                try { windows.updateViewLayout(window, layout); }
                catch (Throwable ignored) { fail(); }
            }).start();
        } catch (Throwable ignored) { fail(); }
    }

    private void fail() { stop(true); }

    private void stop(boolean fallback) {
        boolean wasRequested = visible || requestedShow;
        releaseWindow();
        service = null;
        if (serviceBinder != null) {
            try { serviceBinder.unlinkToDeath(serviceDeath, 0); } catch (Throwable ignored) { }
            serviceBinder = null;
        }
        if (bound) {
            bound = false;
            try { context.unbindService(connection); } catch (Throwable ignored) { }
        }
        main.removeCallbacks(bindTimeout);
        if (fallback && wasRequested) {
            Runnable original = stockFallback;
            if (original != null) {
                try { original.run(); } catch (Throwable ignored) { }
            }
        }
    }

    private void releaseWindow() {
        ready = false;
        requestedShow = false;
        visible = false;
        touching = false;
        hovering = false;
        remoteInteracting = false;
        main.removeCallbacks(heartbeat);
        main.removeCallbacks(dismissTimeout);
        if (service != null) {
            try { service.heartbeat(owner, generation, false, renderingLocked); } catch (Throwable ignored) { }
            try { service.release(owner, generation); } catch (Throwable ignored) { }
        }
        FrameLayout oldWindow = window;
        window = null;
        surface = null;
        layout = null;
        attaching = false;
        generation++;
        if (oldWindow != null && windows != null) {
            oldWindow.animate().cancel();
            try { windows.removeViewImmediate(oldWindow); } catch (Throwable ignored) { }
        }
        windows = null;
    }
}
