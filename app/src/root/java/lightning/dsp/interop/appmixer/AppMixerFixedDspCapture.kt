package lightning.dsp.interop.appmixer

/** Assemble one constant DSP quantum from arbitrary non-blocking capture fragments.
 * A short/empty capture never reconfigures FIR processing or replays stale samples. */
internal class AppMixerFixedDspCapture(
    private val samples: Int,
    private val process: (ShortArray, ShortArray, Int) -> Unit,
) {
    init { require(samples in 2..65536 && samples % 2 == 0) }
    private val input = ShortArray(samples)
    private var pending = 0
    val pendingSamples: Int get() = pending

    fun read(output: ShortArray, timing: AppMixerDspTiming? = null,
             capture: (ShortArray, Int, Int) -> Int): Int {
        require(output.size >= samples)
        val available = samples - pending
        val count = capture(input, pending, available)
        if (count < 0) return count
        check(count <= available) { "Capture exceeded the requested quantum" }
        if (timing != null && count > 0 && pending == 0) timing.firstFragment()
        pending += count
        if (pending != samples) return 0
        timing?.beforeDsp()
        process(input, output, samples)
        timing?.afterDsp()
        pending = 0
        return samples
    }
}

/** Relay-thread-only timing, enabled by the existing diagnostic switch. */
internal class AppMixerDspTiming(private val clock: () -> Long = System::nanoTime) {
    private var firstFragmentNanos = 0L
    private var hasFirstFragment = false
    private var dspStart = 0L
    var maxAssemblyNanos = 0L
        private set
    var maxDspNanos = 0L
        private set
    var blocks = 0
        private set

    fun firstFragment() {
        firstFragmentNanos = clock()
        hasFirstFragment = true
    }
    fun beforeDsp() {
        dspStart = clock()
        if (hasFirstFragment) maxAssemblyNanos = maxOf(maxAssemblyNanos, dspStart - firstFragmentNanos)
        hasFirstFragment = false
    }
    fun afterDsp() {
        maxDspNanos = maxOf(maxDspNanos, clock() - dspStart)
        blocks++
    }
    fun resetWindow() {
        maxAssemblyNanos = 0
        maxDspNanos = 0
        blocks = 0
    }
    fun reset() {
        resetWindow()
        hasFirstFragment = false
    }
}

/** The estimate starts at AudioRecord's timestamp, not at the app's sound event. */
internal object AppMixerCaptureAge {
    fun estimateUs(consumed: Long, position: Long, timestampNanos: Long, nowNanos: Long, rate: Int): Long? {
        if (consumed <= 0 || position <= 0 || rate <= 0 || timestampNanos <= 0 || nowNanos < timestampNanos) return null
        val elapsed = nowNanos - timestampNanos
        val frames = position - consumed
        // Reject stale or apparently different frame origins rather than making
        // up an age. A recent timestamp may trail the latest completed read.
        if (elapsed > 1_000_000_000L || frames !in -rate.toLong()..rate.toLong()) return null
        val age = elapsed + frames * 1_000_000_000L / rate
        return if (age in 0..1_000_000_000L) age / 1000 else null
    }
}
