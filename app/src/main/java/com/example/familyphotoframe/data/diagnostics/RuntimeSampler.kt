package com.example.familyphotoframe.data.diagnostics

/**
 * Periodic runtime sampling, so a soak run leaves behind evidence rather than an
 * impression.
 *
 * The Phase 1 gate (spec §22.2) asks for "heap growth < 5% over a rolling 6h window" and
 * "24h NAS offline/online cycling with no crash / no permanent blank". Neither can be
 * judged from event logs alone: the first needs a time series, and the second needs proof
 * the process stayed alive (and, when it did not, that it came back).
 *
 * Sampling is deliberately lightweight. At the one-minute default a 24-hour run produces
 * 1,440 compact samples — enough to expose rapid pressure on low-memory frames while
 * remaining negligible next to the photo cache.
 *
 * Pure Kotlin: the heap readings come from [Runtime], and the clock and scheduling are
 * injected, so the rolling-window logic is unit-testable without a device.
 */
class RuntimeSampler(
    private val diagnostics: DiagnosticsLog,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val heap: () -> HeapReading = { HeapReading.current() },
    /** Optional Android/process/cache readings supplied by the wiring layer. */
    private val extraFields: () -> Map<String, String> = { emptyMap() },
) {
    data class HeapReading(val usedBytes: Long, val maxBytes: Long) {
        companion object {
            fun current(): HeapReading {
                val rt = Runtime.getRuntime()
                return HeapReading(rt.totalMemory() - rt.freeMemory(), rt.maxMemory())
            }
        }
    }

    private val startedAtMs = nowMs()

    /**
     * Record one sample. Emits absolute numbers only; the *analysis* computes growth,
     * because a rolling window computed on-device would have to be recomputed anyway
     * whenever the analysis window changed.
     */
    fun sample(): HeapReading {
        val h = reading()
        val fields = linkedMapOf(
            "heapUsedKb" to (h.usedBytes / 1024).toString(),
            "heapMaxKb" to (h.maxBytes / 1024).toString(),
            "uptimeSec" to ((nowMs() - startedAtMs) / 1000).toString(),
        )
        fields.putAll(runCatching { extraFields() }.getOrDefault(emptyMap()))
        diagnostics.log(DiagnosticsLog.Category.MEMORY, "HEAP_SAMPLE", "", fields)
        return h
    }

    /** Read current heap pressure without emitting a durable one-minute sample. */
    fun reading(): HeapReading = heap()

    companion object {
        /** One minute: catches rapid pressure on low-memory frames while remaining cheap. */
        const val DEFAULT_INTERVAL_MS = 60_000L

        /** Pressure is checked more often than evidence is persisted. */
        const val PRESSURE_CHECK_INTERVAL_MS = 10_000L
    }
}
