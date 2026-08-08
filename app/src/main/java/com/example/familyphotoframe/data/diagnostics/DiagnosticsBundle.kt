package com.example.familyphotoframe.data.diagnostics

/** Bounded, privacy-safe application and runtime context written ahead of JSONL events. */
data class DiagnosticsBundleContext(
    val appVersion: String = "",
    val versionCode: Long = 0L,
    val buildType: String = "",
    val sdkInt: Int = 0,
    val deviceModel: String = "",
    val abi: String = "",
    val sourceKind: String = "NONE",
    val indexedCount: Long = 0L,
    val runtime: DiagnosticRuntimeState.Snapshot = DiagnosticRuntimeState.Snapshot(),
)

/**
 * Encodes the small support-bundle envelope without depending on Android or loading the
 * retained event files. Every returned record is one valid JSON object followed by LF.
 */
object DiagnosticsBundleJson {
    fun prelude(
        context: DiagnosticsBundleContext,
        health: DiagnosticsHealthSnapshot,
        generatedAtEpochMs: Long,
        sessionId: String,
        flushSucceeded: Boolean,
        durableStreamsAttached: Boolean,
    ): ByteArray {
        val retentionStatus = when {
            !durableStreamsAttached -> "MEMORY_ONLY"
            health.standard.rotations > 0L || health.bulk.rotations > 0L ||
                health.standard.retainedGenerations > 1 || health.bulk.retainedGenerations > 1 -> "PARTIAL_RETENTION"
            health.standard.retainedBytes + health.bulk.retainedBytes == 0L -> "EMPTY"
            else -> "COMPLETE"
        }
        val sinkFailed = health.standard.lastAppendErrorClass.isNotEmpty() ||
            health.bulk.lastAppendErrorClass.isNotEmpty()
        val evidenceIncomplete = !flushSucceeded || !durableStreamsAttached ||
            health.droppedTotal > 0L || sinkFailed || health.crashEnvelopePresent ||
            health.standard.rotations > 0L || health.bulk.rotations > 0L ||
            health.standard.retainedGenerations > 1 || health.bulk.retainedGenerations > 1
        val runtime = context.runtime
        return buildString(2_048) {
            record(
                "recordType" to "bundleMetadata",
                "bundleSchemaVersion" to 1,
                "eventSchemaVersions" to listOf(1, 2),
                "generatedAtEpochMs" to generatedAtEpochMs,
                "sessionId" to sessionId,
                "appVersion" to context.appVersion,
                "versionCode" to context.versionCode,
                "buildType" to context.buildType,
                "sdkInt" to context.sdkInt,
                "deviceModel" to context.deviceModel.take(80),
                "abi" to context.abi.take(40),
                "sourceKind" to safeCode(context.sourceKind, "NONE"),
                "indexedCount" to context.indexedCount.coerceAtLeast(0L),
                "retentionStatus" to retentionStatus,
                "evidenceIncomplete" to evidenceIncomplete,
                "mergeOrder" to "sessionId,sequence",
            )
            record(
                "recordType" to "runtimeSnapshot",
                "sampledAtEpochMs" to runtime.memory.sampledAtEpochMs,
                "heapUsedKb" to runtime.memory.heapUsedKb,
                "heapMaxKb" to runtime.memory.heapMaxKb,
                "nativeHeapKb" to runtime.memory.nativeHeapKb,
                "pssKb" to runtime.memory.pssKb,
                "imageCacheKb" to runtime.memory.imageCacheKb,
                "surface" to safeCode(runtime.playback.surface, "UNKNOWN"),
                "engineState" to safeCode(runtime.playback.engineState, "UNKNOWN"),
                "presentationToken" to runtime.playback.presentationToken.take(80),
                "sourceKind" to safeCode(runtime.playback.sourceKind, "NONE"),
                "layout" to safeCode(runtime.playback.layout, "UNKNOWN"),
                "transitionCode" to safeCode(runtime.playback.transitionCode, "UNKNOWN"),
            )
            record(
                "recordType" to "diagnosticsHealth",
                "queueCapacity" to health.queueCapacity,
                "queueDepth" to health.queueDepth,
                "droppedTotal" to health.droppedTotal,
                "droppedSinceLastReport" to health.droppedSinceLastReport,
                "fieldsDropped" to health.fieldsDropped,
                "lastSuccessfulWriteEpochMs" to health.lastSuccessfulWriteEpochMs,
                "lastSuccessfulFlushEpochMs" to health.lastSuccessfulFlushEpochMs,
                "lastFlushTimeoutMs" to health.lastFlushTimeoutMs,
                "flushSucceeded" to flushSucceeded,
                "crashEnvelopePresent" to health.crashEnvelopePresent,
                "crashEnvelopeBytes" to health.crashEnvelopeBytes,
                "rateStateEntries" to health.rateStateEntries,
                "standard" to streamObject(health.standard),
                "bulk" to streamObject(health.bulk),
            )
            append(boundary("standard", "start"))
        }.toByteArray(Charsets.UTF_8)
    }

    fun boundary(stream: String, phase: String): String = jsonObject(
        listOf(
            "recordType" to "streamBoundary",
            "stream" to safeCode(stream, "UNKNOWN"),
            "phase" to safeCode(phase, "UNKNOWN"),
        ),
    ) + "\n"

    fun end(evidenceIncomplete: Boolean): String = jsonObject(
        listOf(
            "recordType" to "bundleEnd",
            "evidenceIncomplete" to evidenceIncomplete,
        ),
    ) + "\n"

    private fun streamObject(stream: DiagnosticsHealthSnapshot.Stream): RawJson = RawJson(
        jsonObject(
            listOf(
                "retainedBytes" to stream.retainedBytes,
                "retainedGenerations" to stream.retainedGenerations,
                "rotations" to stream.rotations,
                "oldestKnownSessionId" to stream.oldestKnownSessionId,
                "oldestKnownSequence" to stream.oldestKnownSequence,
                "newestKnownSessionId" to stream.newestKnownSessionId,
                "newestKnownSequence" to stream.newestKnownSequence,
                "lastSuccessfulWriteEpochMs" to stream.lastSuccessfulWriteEpochMs,
                "lastAppendErrorClass" to stream.lastAppendErrorClass,
                "retentionStatus" to when {
                    stream.retainedBytes == 0L -> "EMPTY"
                    stream.rotations > 0L || stream.retainedGenerations > 1 -> "PARTIAL_RETENTION"
                    else -> "COMPLETE"
                },
            ),
        ),
    )

    private fun StringBuilder.record(vararg fields: Pair<String, Any?>) {
        append(jsonObject(fields.asList())).append('\n')
    }

    private fun jsonObject(fields: List<Pair<String, Any?>>): String = buildString {
        append('{')
        fields.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendString(key)
            append(':')
            appendValue(value)
        }
        append('}')
    }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is RawJson -> append(value.value)
            is Boolean, is Number -> append(value.toString())
            is List<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }
            else -> appendString(value.toString())
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append(String.format("\\u%04x", char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun safeCode(value: String, fallback: String): String = value.uppercase()
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(48)
        .ifEmpty { fallback }

    private data class RawJson(val value: String)
}
