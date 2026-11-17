package lightning.dsp.volumemanager.surface

/** Bounds the exported surface bridge without granting privileged app operations. */
internal object SurfaceSessionPolicy {
    const val LEASE_MILLIS = 6_000L
    const val MAX_DIMENSION = 4_096
    const val MAX_PIXELS = 8_388_608L

    fun callerAllowed(callerUid: Int, systemUiUid: Int, appUid: Int): Boolean =
        callerUid >= 0 && callerUid == systemUiUid && callerUid / 100_000 == appUid / 100_000

    fun validRequest(displayId: Int, width: Int, height: Int, generation: Long): Boolean =
        displayId >= 0 && width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION &&
            width.toLong() * height <= MAX_PIXELS && generation > 0L

    fun validRelayout(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Boolean =
        width in 1..maxWidth && height in 1..maxHeight &&
            width.toLong() * height <= MAX_PIXELS

    fun current(ownerMatches: Boolean, expectedGeneration: Long, generation: Long): Boolean =
        ownerMatches && generation > 0L && expectedGeneration == generation

    fun leaseValid(lastHeartbeat: Long, now: Long): Boolean =
        lastHeartbeat >= 0L && now >= lastHeartbeat && now - lastHeartbeat <= LEASE_MILLIS

    fun mayPresent(attached: Boolean, renderedLocked: Boolean, actualLocked: Boolean,
                   requestedLocked: Boolean): Boolean =
        attached && renderedLocked == (actualLocked || requestedLocked)
}
