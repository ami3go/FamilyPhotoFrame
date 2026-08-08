package com.example.familyphotoframe.util

/**
 * Lowercase, unseparated hex encoding for digest bytes (SHA-256 stable ids, cache
 * keys, credential references, scope keys).
 *
 * Every call site that turns a digest into a key previously reimplemented this
 * independently — most via `"%02x".format(it)`, one by hand with `toString(16)`.
 * `String.format` is locale-sensitive in general (it consults the default locale's
 * `DecimalFormatSymbols` for some conversions) and boxes each byte; a fixed lookup
 * table sidesteps both, so this is provably locale-independent with no `Locale`
 * argument needed, not just a lucky case where nobody has hit it yet.
 */
private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHexString(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_CHARS[v ushr 4]
        out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(out)
}
