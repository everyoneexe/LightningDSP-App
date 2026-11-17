package lightning.dsp.volumemanager.surface

internal object SurfaceReadinessPolicy {
    const val TTL_MS = 6_000L
    fun ready(acknowledged: Boolean, refreshedAt: Long, now: Long,
              renderedLocked: Boolean, deviceLocked: Boolean): Boolean =
        acknowledged && refreshedAt > 0 && now >= refreshedAt && now - refreshedAt <= TTL_MS &&
            (!deviceLocked || renderedLocked)
}
