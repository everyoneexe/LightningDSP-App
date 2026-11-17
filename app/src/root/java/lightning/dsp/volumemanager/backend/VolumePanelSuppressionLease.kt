package lightning.dsp.volumemanager.backend

/** Short-lived SystemUI opt-in; expires automatically if the root volume bridge disappears. */
internal object VolumePanelSuppressionLease {
    const val PROPERTY = "debug.lightningdsp.volume_panel_lease_ms"
    const val REFRESH_INTERVAL_MS = 2_000L
    const val TTL_MS = 6_000L

    /**
     * The direct SystemUI hook may suppress while its embedded surface is being rebuilt.
     * It queues the request and replays the original stock callback if attachment fails.
     */
    fun shouldSuppressSystemUiHook(root: Boolean, active: Boolean, panelEnabled: Boolean,
                                   bypass: Boolean, optedIn: Boolean, hookReady: Boolean): Boolean =
        root && active && panelEnabled && optedIn && hookReady && !bypass

    fun shouldSuppressController(active: Boolean, panelEnabled: Boolean, overlayReady: Boolean,
                                 controllerReady: Boolean, optedIn: Boolean, bypass: Boolean): Boolean =
        active && panelEnabled && overlayReady && controllerReady && optedIn && !bypass

    fun propertyValue(enabled: Boolean, uptimeMillis: Long): String =
        if (enabled && uptimeMillis > 0L) uptimeMillis.toString() else "0"

    fun isActive(propertyValue: String?, uptimeMillis: Long): Boolean {
        val refreshedAt = propertyValue?.toLongOrNull() ?: return false
        return refreshedAt > 0L && uptimeMillis >= refreshedAt && uptimeMillis - refreshedAt <= TTL_MS
    }
}
