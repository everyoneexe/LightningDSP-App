package lightning.dsp.volumemanager.surface

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityEvent
import android.view.ContextThemeWrapper
import android.view.SurfaceControlViewHost
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import lightning.dsp.R
import lightning.dsp.volumemanager.backend.LightningDspVolumeManager
import lightning.dsp.volumemanager.backend.SystemUiVolumePanelOption
import lightning.dsp.volumemanager.backend.VolumeManagerState
import lightning.dsp.volumemanager.ui.LightningVolumeFloatingPanel
import lightning.dsp.volumemanager.ui.VolumeOverlayIdlePolicy
import kotlin.math.roundToInt
import timber.log.Timber

/** App-owned UI embedded in the trusted SystemUI window; no general command API is exported. */
class VolumePanelSurfaceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var warnings = 0
    private val expire = object : Runnable {
        override fun run() {
            val current = session ?: return
            if (!allowed() || !SurfaceSessionPolicy.leaseValid(current.lastHeartbeat, SystemClock.uptimeMillis()) ||
                !current.locked && locked()) {
                close(current, notify = true)
            } else main.postDelayed(this, PRIVACY_CHECK_MILLIS)
        }
    }
    private val privacyEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // Never retain an unrestricted surface across a screen/keyguard transition.
                session?.let { close(it, notify = true) }
            } else if (intent.action == VOLUME_CHANGED_ACTION) {
                session?.takeIf { it.locked }?.let { it.streamRevision++ }
            }
        }
    }

    private val bridge = object : IVolumePanelSurfaceService.Stub() {
        override fun attach(owner: IBinder?, hostToken: IBinder?, displayId: Int, width: Int, height: Int,
                            generation: Long, requestedLocked: Boolean,
                            callback: IVolumePanelSurfaceCallback?) {
            if (!trustedCaller() || owner == null || hostToken == null || callback == null ||
                !SurfaceSessionPolicy.validRequest(displayId, width, height, generation)) return
            main.post {
                if (Build.VERSION.SDK_INT < 30 || !allowed() || !owner.isBinderAlive) {
                    runCatching { callback.surfaceFailed(generation) }
                    return@post
                }
                val previous = session
                if (previous != null && (previous.owner != owner || generation <= previous.generation)) {
                    runCatching { callback.surfaceFailed(generation) }
                    return@post
                }
                previous?.let { close(it, notify = false) }
                create(owner, hostToken, displayId, width, height, generation, requestedLocked, callback)
            }
        }

        override fun heartbeat(owner: IBinder?, generation: Long, attached: Boolean, requestedLocked: Boolean) {
            if (!trustedCaller() || owner == null) return
            main.post {
                val current = session ?: return@post
                if (!SurfaceSessionPolicy.current(current.owner == owner, current.generation, generation)) return@post
                if (!allowed() || !SurfaceSessionPolicy.mayPresent(attached, current.locked, locked(), requestedLocked)) {
                    close(current, notify = true)
                    return@post
                }
                current.lastHeartbeat = SystemClock.uptimeMillis()
                VolumePanelSurfaceClient.markReady(owner, generation, true, current.locked)
            }
        }

        override fun release(owner: IBinder?, generation: Long) {
            if (!trustedCaller() || owner == null) return
            main.post {
                session?.takeIf {
                    SurfaceSessionPolicy.current(it.owner == owner, it.generation, generation)
                }?.let { close(it, notify = false) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, privacyEvents, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(VOLUME_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onBind(intent: Intent?): IBinder? = if (Build.VERSION.SDK_INT >= 30) bridge else null

    override fun onDestroy() {
        session?.let { close(it, notify = false) }
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(privacyEvents)
        super.onDestroy()
    }

    private fun trustedCaller(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(SYSTEM_UI, 0)
        // OEM SystemUI may share the platform UID. That UID is an OS trust boundary,
        // not proof that a caller is a particular process inside that shared UID.
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 &&
            SurfaceSessionPolicy.callerAllowed(Binder.getCallingUid(), info.uid, Process.myUid())
    }.getOrDefault(false)

    private fun allowed(): Boolean = LightningDspVolumeManager.isEnabled &&
        SystemUiVolumePanelOption.requested(this)

    private fun locked(): Boolean = getSystemService(KeyguardManager::class.java)?.let {
        it.isKeyguardLocked || it.isDeviceLocked
    } ?: true

    @RequiresApi(30)
    @Suppress("DEPRECATION")
    private fun create(owner: IBinder, token: IBinder, displayId: Int, width: Int, height: Int,
                       generation: Long, requestedLocked: Boolean,
                       callback: IVolumePanelSurfaceCallback) {
        val display = getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
        if (display == null || displayId != android.view.Display.DEFAULT_DISPLAY) {
            warnUnavailable("display")
            runCatching { callback.surfaceFailed(generation) }
            return
        }
        val panelContext = ContextThemeWrapper(createDisplayContext(display), R.style.Theme_RootlessLightningDSP)
        val current = Session(owner, generation, callback, locked() || requestedLocked)
        session = current
        VolumePanelSurfaceClient.markReady(owner, generation, false, current.locked)
        try {
            owner.linkToDeath(current.death, 0)
            val surfaceHost = SurfaceControlViewHost(panelContext, display, token)
            current.surfaceHost = surfaceHost
            val root = object : FrameLayout(panelContext) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                        event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                        event.actionMasked == MotionEvent.ACTION_MOVE)
                        current.interaction(VolumeOverlayIdlePolicy.Interaction.TOUCH, true)
                    return try { super.dispatchTouchEvent(event) } finally {
                        if (event.actionMasked == MotionEvent.ACTION_UP ||
                            event.actionMasked == MotionEvent.ACTION_CANCEL)
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.TOUCH, false)
                    }
                }

                override fun dispatchHoverEvent(event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.HOVER, true)
                        MotionEvent.ACTION_HOVER_EXIT ->
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.HOVER, false)
                    }
                    return super.dispatchHoverEvent(event)
                }

                override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
                    when (event.eventType) {
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED ->
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.ACCESSIBILITY, true)
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED ->
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.ACCESSIBILITY, false)
                    }
                    return super.requestSendAccessibilityEvent(child, event)
                }
            }
            root.setViewTreeLifecycleOwner(current)
            root.setViewTreeSavedStateRegistryOwner(current)
            val view = ComposeView(panelContext).apply {
                setViewTreeLifecycleOwner(current)
                setViewTreeSavedStateRegistryOwner(current)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (current.locked) LockedStreamPanel(panelContext, current.streamRevision)
                        else LightningVolumeFloatingPanel(current.state, onClose = {
                            runCatching { callback.dismiss(generation) }
                        }, onPanelFocusChanged = {
                            current.interaction(VolumeOverlayIdlePolicy.Interaction.FOCUS, it)
                        })
                    }
                }
            }
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
            val firstDraw = object : ViewTreeObserver.OnDrawListener {
                private var delivered = false
                override fun onDraw() {
                    if (delivered) return
                    delivered = true
                    main.post {
                        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(this)
                        if (session !== current) return@post
                        current.drawTimeout?.let(main::removeCallbacks)
                        current.drawTimeout = null
                        if (!allowed() || (!current.locked && locked())) {
                            close(current, notify = true)
                            return@post
                        }
                        try {
                            val surface = surfaceHost.surfacePackage ?: error("Surface creation failed")
                            try { callback.surfaceReady(generation, surface, current.locked) }
                            finally { surface.release() }
                        } catch (error: Exception) {
                            warnUnavailable("first draw callback", error)
                            close(current, notify = true)
                        }
                    }
                }
            }
            root.viewTreeObserver.addOnDrawListener(firstDraw)
            current.drawTimeout = Runnable {
                warnUnavailable("first draw timeout")
                close(current, notify = true)
            }.also {
                main.postDelayed(it, FIRST_DRAW_TIMEOUT_MILLIS)
            }
            surfaceHost.setView(root, width, height)
            current.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            if (!current.locked) {
                val stateObserver = Observer<VolumeManagerState> { state ->
                    if (session === current) {
                        if (!allowed() || locked()) close(current, notify = true)
                        else current.state = state
                    }
                }
                current.observer = stateObserver
                LightningDspVolumeManager.state.observeForever(stateObserver)
            }
            if (session !== current || !current.locked && locked()) {
                close(current, notify = true)
                return
            }
            main.removeCallbacks(expire)
            main.postDelayed(expire, PRIVACY_CHECK_MILLIS)
        } catch (error: Exception) {
            warnUnavailable("create", error)
            close(current, notify = true)
        }
    }

    private fun warnUnavailable(stage: String, error: Exception? = null) {
        if (warnings >= MAX_WARNINGS) return
        warnings++
        // Essential fallback receipt only: never log exception messages, tokens or app identities.
        Timber.w("Volume surface unavailable at %s (%s)", stage,
            error?.javaClass?.simpleName ?: "unavailable")
    }

    private fun close(current: Session, notify: Boolean) {
        if (session !== current) return
        session = null
        main.removeCallbacks(expire)
        current.drawTimeout?.let(main::removeCallbacks)
        current.drawTimeout = null
        VolumePanelSurfaceClient.clear(current.owner, current.generation)
        current.observer?.let { LightningDspVolumeManager.state.removeObserver(it) }
        current.observer = null
        runCatching { current.owner.unlinkToDeath(current.death, 0) }
        if (Build.VERSION.SDK_INT >= 30) runCatching { current.surfaceHost?.release() }
        current.surfaceHost = null
        current.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (notify) runCatching { current.callback.surfaceFailed(current.generation) }
    }

    private inner class Session(val owner: IBinder, val generation: Long,
                                val callback: IVolumePanelSurfaceCallback, val locked: Boolean) : SavedStateRegistryOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this).apply { performRestore(null) }
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        var lastHeartbeat = SystemClock.uptimeMillis()
        var surfaceHost: SurfaceControlViewHost? = null
        var state by mutableStateOf(if (locked) VolumeManagerState()
            else LightningDspVolumeManager.state.value ?: VolumeManagerState())
        var streamRevision by mutableStateOf(0L)
        var observer: Observer<VolumeManagerState>? = null
        var drawTimeout: Runnable? = null
        val death = IBinder.DeathRecipient { main.post { close(this, notify = false) } }
        private val idle = VolumeOverlayIdlePolicy(3_300L, SystemClock.uptimeMillis())
        private var interactionActive = false

        fun interaction(kind: VolumeOverlayIdlePolicy.Interaction, active: Boolean) {
            if (session !== this) return
            idle.setInteraction(kind, active, SystemClock.uptimeMillis())
            val engaged = idle.remainingMillis(SystemClock.uptimeMillis()) == null
            if (engaged != interactionActive) {
                interactionActive = engaged
                runCatching { callback.interaction(generation, engaged) }
                    .onFailure { close(this, notify = false) }
            }
        }
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val MAX_WARNINGS = 4
        private const val PRIVACY_CHECK_MILLIS = 250L
        private const val FIRST_DRAW_TIMEOUT_MILLIS = 1_500L
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}

/** No application identity, media metadata, navigation or DSP controls may enter this branch. */
@Composable
private fun LockedStreamPanel(context: Context, revision: Long) {
    val audio = context.getSystemService(AudioManager::class.java)
    MaterialTheme {
        Surface {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Volume", style = MaterialTheme.typography.titleMedium)
                listOf(AudioManager.STREAM_MUSIC to "Media", AudioManager.STREAM_RING to "Ring",
                    AudioManager.STREAM_ALARM to "Alarm").forEach { (stream, label) ->
                    val maximum = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
                    var value by remember(stream, revision) { mutableStateOf(audio.getStreamVolume(stream).toFloat()) }
                    Text(label)
                    Slider(value = value, valueRange = 0f..maximum.toFloat(), onValueChange = {
                        value = it
                        LightningDspVolumeManager.setSystemStreamVolume(stream, it.roundToInt())
                    })
                }
            }
        }
    }
}
