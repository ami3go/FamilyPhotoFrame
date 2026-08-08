package com.example.familyphotoframe.data.diagnostics

import java.util.LinkedHashMap

/** Bounded, thread-safe aggregation and state-change policy used by [DiagnosticsLog]. */
class DiagnosticRateController(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    data class Decision(
        val emit: Boolean,
        val code: String,
        val fields: Map<String, String>,
    )

    private data class BrightnessState(
        var signature: String,
        var lastHeartbeatMs: Long,
        var unchangedChecks: Long = 0L,
    )

    private data class DecodeBucket(
        val signatureFields: Map<String, String>,
        val firstMs: Long,
        var lastMs: Long,
        var total: Long = 0L,
        var sinceSummary: Long = 0L,
        var individualEvents: Int = 0,
        var lastSummaryMs: Long = firstMs,
    )

    private data class PreviewBucket(
        val firstMs: Long,
        var lastMs: Long,
        var hits: Long = 0L,
        var lastSummaryMs: Long = firstMs,
    )

    private val lock = Any()
    private var brightness: BrightnessState? = null
    private val scanBuckets = LinkedHashMap<String, String>()
    private val decodeBuckets = LinkedHashMap<String, DecodeBucket>(16, 0.75f, true)
    private var preview = PreviewBucket(0L, 0L)

    fun evaluate(
        spec: DiagnosticEventSpec,
        fields: Map<String, String>,
        context: DiagnosticContext,
    ): Decision = synchronized(lock) {
        val now = nowMs()
        expire(now)
        when (spec.ratePolicy) {
            DiagnosticRatePolicy.NONE, DiagnosticRatePolicy.STATE_CHANGE ->
                Decision(true, spec.code, fields)
            DiagnosticRatePolicy.BRIGHTNESS_CHANGE -> brightness(fields, now)
            DiagnosticRatePolicy.SCAN_PROGRESS -> scanProgress(spec.code, fields, context)
            DiagnosticRatePolicy.DECODE_FAILURE_AGGREGATE -> decode(spec, fields, now)
            DiagnosticRatePolicy.PREVIEW_HIT_AGGREGATE -> preview(fields, now)
        }
    }

    fun stateSize(): Int = synchronized(lock) {
        (if (brightness == null) 0 else 1) + scanBuckets.size + decodeBuckets.size +
            (if (preview.firstMs == 0L) 0 else 1)
    }

    fun clear() = synchronized(lock) {
        brightness = null
        scanBuckets.clear()
        decodeBuckets.clear()
        preview = PreviewBucket(0L, 0L)
    }

    private fun brightness(fields: Map<String, String>, now: Long): Decision {
        val signature = listOf("level", "action", "mode", "periodToken")
            .joinToString("|") { fields[it].orEmpty() }
        val current = brightness
        if (current == null || current.signature != signature) {
            brightness = BrightnessState(signature, now)
            return Decision(true, "BRIGHTNESS_LEVEL_APPLIED", fields)
        }
        current.unchangedChecks++
        if (now - current.lastHeartbeatMs >= BRIGHTNESS_HEARTBEAT_MS) {
            val duration = (now - current.lastHeartbeatMs).coerceAtLeast(0L)
            current.lastHeartbeatMs = now
            val count = current.unchangedChecks
            current.unchangedChecks = 0L
            return Decision(
                true,
                "BRIGHTNESS_UNCHANGED_HEARTBEAT",
                fields + mapOf("count" to count.toString(), "durationMs" to duration.toString()),
            )
        }
        return Decision(false, "BRIGHTNESS_LEVEL_APPLIED", fields)
    }

    private fun scanProgress(
        code: String,
        fields: Map<String, String>,
        context: DiagnosticContext,
    ): Decision {
        val key = context.operationId ?: listOf(
            fields["sourceKind"], fields["configRevision"], fields["refreshToken"],
        ).joinToString("|")
        val bucket = fields["progressBucket"] ?: fields["found"].orEmpty()
        if (scanBuckets[key] == bucket) return Decision(false, code, fields)
        scanBuckets[key] = bucket
        trim(scanBuckets)
        return Decision(true, code, fields)
    }

    private fun decode(
        spec: DiagnosticEventSpec,
        fields: Map<String, String>,
        now: Long,
    ): Decision {
        if (spec.severity == DiagnosticSeverity.FATAL) return Decision(true, spec.code, fields)
        val signatureFields = listOf(
            "sourceKind", "stage", "format", "errorClass", "errorCode", "permanent",
        ).mapNotNull { key -> fields[key]?.let { key to it } }.toMap()
        val signature = signatureFields.entries.joinToString("|") { "${it.key}=${it.value}" }
        val bucket = decodeBuckets.getOrPut(signature) {
            DecodeBucket(signatureFields, now, now)
        }
        trim(decodeBuckets)
        bucket.lastMs = now
        bucket.total++
        bucket.sinceSummary++
        if (bucket.individualEvents < FIRST_DECODE_EVENTS) {
            bucket.individualEvents++
            return Decision(true, spec.code, fields)
        }
        if (now - bucket.lastSummaryMs >= SUMMARY_INTERVAL_MS) {
            val summaryCount = bucket.sinceSummary
            bucket.sinceSummary = 0L
            bucket.lastSummaryMs = now
            return Decision(
                true,
                "DECODE_FAILURE_SUMMARY",
                bucket.signatureFields + mapOf(
                    "count" to summaryCount.toString(),
                    "failures" to bucket.total.toString(),
                    "firstEpochMs" to bucket.firstMs.toString(),
                    "lastEpochMs" to bucket.lastMs.toString(),
                ),
            )
        }
        trim(decodeBuckets)
        return Decision(false, spec.code, fields)
    }

    private fun preview(fields: Map<String, String>, now: Long): Decision {
        if (preview.firstMs == 0L) preview = PreviewBucket(now, now)
        preview.lastMs = now
        preview.hits++
        if (now - preview.lastSummaryMs < SUMMARY_INTERVAL_MS) {
            return Decision(false, "WEB_PREVIEW_CACHE_HIT", fields)
        }
        val hits = preview.hits
        val duration = (now - preview.lastSummaryMs).coerceAtLeast(0L)
        preview.hits = 0L
        preview.lastSummaryMs = now
        return Decision(
            true,
            "PREVIEW_HIT_SUMMARY",
            mapOf("hits" to hits.toString(), "durationMs" to duration.toString()),
        )
    }

    private fun expire(now: Long) {
        decodeBuckets.entries.removeAll { now - it.value.lastMs >= BUCKET_EXPIRY_MS }
        trim(decodeBuckets)
    }

    private fun <K, V> trim(map: MutableMap<K, V>) {
        while (map.size > capacity.coerceAtLeast(1)) map.remove(map.keys.first())
    }

    companion object {
        const val BRIGHTNESS_HEARTBEAT_MS = 15L * 60L * 1_000L
        const val SUMMARY_INTERVAL_MS = 60_000L
        const val BUCKET_EXPIRY_MS = 15L * 60L * 1_000L
        const val FIRST_DECODE_EVENTS = 3
        const val DEFAULT_CAPACITY = 64
    }
}
