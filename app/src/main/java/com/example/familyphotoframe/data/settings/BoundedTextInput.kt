package com.example.familyphotoframe.data.settings

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ImportTooLargeException(val maximumBytes: Int) :
    IOException("Configuration exceeds the $maximumBytes-byte limit")

/** Incremental UTF-8 reader used by every file-based settings import. */
object BoundedTextInput {
    const val MAX_IMPORT_BYTES: Int = 4 * 1024 * 1024
    private const val BUFFER_BYTES = 16 * 1024

    fun readUtf8(input: InputStream, maximumBytes: Int = MAX_IMPORT_BYTES): String {
        require(maximumBytes > 0)
        val output = ByteArrayOutputStream(minOf(maximumBytes, BUFFER_BYTES))
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (total > maximumBytes - count) throw ImportTooLargeException(maximumBytes)
            output.write(buffer, 0, count)
            total += count
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
