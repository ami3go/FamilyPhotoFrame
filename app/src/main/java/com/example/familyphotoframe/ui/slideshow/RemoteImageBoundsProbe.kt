package com.example.familyphotoframe.ui.slideshow

import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream

internal const val REMOTE_COLLAGE_PROBE_BYTE_LIMIT = 256L * 1024L
internal const val REMOTE_COLLAGE_PROBE_TIMEOUT_MS = 4_000L

/** Hard byte ceiling around an unreliable remote stream. */
internal class CappedInputStream(
    input: InputStream,
    maxBytes: Long,
) : FilterInputStream(input) {
    private var remaining = maxBytes.coerceAtLeast(0L)

    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0L) return -1
        val allowed = minOf(length.toLong(), remaining).toInt()
        val count = super.read(buffer, offset, allowed)
        if (count > 0) remaining -= count.toLong()
        return count
    }

    override fun skip(byteCount: Long): Long {
        if (remaining <= 0L) return 0L
        val skipped = super.skip(minOf(byteCount, remaining))
        if (skipped > 0L) remaining -= skipped
        return skipped
    }
}

internal object RemoteImageBoundsProbe {
    /** Owns and closes [input]. Returns null if dimensions are not discoverable within the cap. */
    fun decode(
        input: InputStream,
        maxBytes: Long = REMOTE_COLLAGE_PROBE_BYTE_LIMIT,
    ): Pair<Int, Int>? = useRemoteProbeStream(input, maxBytes) { bounded ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(bounded, null, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else null
    }
}

/**
 * Gives the decoder a buffered, byte-capped stream and closes it as soon as decoding
 * returns. Closing deliberately does not drain the remaining cap: bounds probes should
 * consume only the image metadata the decoder needs, not up to 256 KiB per candidate.
 */
internal fun <T> useRemoteProbeStream(
    input: InputStream,
    maxBytes: Long,
    decode: (InputStream) -> T,
): T = BufferedInputStream(CappedInputStream(input, maxBytes), 32 * 1024).use(decode)
