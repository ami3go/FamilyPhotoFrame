package com.example.familyphotoframe.data.diagnostics

import java.io.File

/** Lightweight `/proc` counters available on the API-22 target without extra permission. */
class ProcfsResourceSampler(
    private val fileDescriptorDirectory: File = File("/proc/self/fd"),
    private val taskDirectory: File = File("/proc/self/task"),
) {
    data class Snapshot(
        val openFileDescriptorCount: Int?,
        val threadCount: Int?,
    )

    fun sample(): Snapshot = Snapshot(
        openFileDescriptorCount = entryCount(fileDescriptorDirectory),
        threadCount = entryCount(taskDirectory),
    )

    private fun entryCount(directory: File): Int? = runCatching {
        directory.list()?.size?.coerceAtLeast(0)
    }.getOrNull()
}
