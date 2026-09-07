package com.example.familyphotoframe.data.cache

/** Naming boundary for resumable media-cache files. */
internal object PartialCachePolicy {
    private const val CACHE_KEY_HEX_LENGTH = 32
    const val FILE_SUFFIX = ".part"
    private val fileName = Regex("^[0-9a-fA-F]{$CACHE_KEY_HEX_LENGTH}\\.part$")

    fun ownsFileName(name: String): Boolean = fileName.matches(name)
}
