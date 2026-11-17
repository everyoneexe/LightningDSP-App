package lightning.dsp.volumemanager.backend

/** Brief visual continuity after a per-app DSP toggle; it never changes DSP or playback state. */
internal class DspControlRowRetention(
    private val durationMillis: Long = DURATION_MILLIS,
    private val limit: Int = 32,
) {
    private val expirations = linkedMapOf<String, Long>()

    init { require(durationMillis > 0 && limit > 0) }

    @Synchronized
    fun retain(identity: String, nowMillis: Long) {
        if (identity.isBlank()) return
        prune(nowMillis)
        expirations.remove(identity)
        expirations[identity] = nowMillis + durationMillis
        while (expirations.size > limit) expirations.remove(expirations.keys.first())
    }

    @Synchronized
    fun contains(identity: String, nowMillis: Long): Boolean {
        prune(nowMillis)
        return expirations[identity]?.let { nowMillis < it } == true
    }

    @Synchronized
    private fun prune(nowMillis: Long) {
        expirations.entries.removeAll { nowMillis >= it.value }
    }

    internal companion object {
        const val DURATION_MILLIS = 3_000L
    }
}
