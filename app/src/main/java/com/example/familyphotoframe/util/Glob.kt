package com.example.familyphotoframe.util

import java.util.Locale

/**
 * Minimal, allocation-light glob matcher for include/exclude rules in the config
 * schema (spec §20: includeGlobs / excludeGlobs such as "*.jpg", ".*", "Thumbs.db",
 * or thumbnail subtrees like the "@eaDir" folder). Supports `*` (any run within a
 * path segment), `**` (any run across segments) and `?` (single char). Matching is
 * case-insensitive on the file name.
 *
 * Compiled patterns are cached: a scan tests every file against the same handful of
 * globs, so compiling each pattern once (not once per file) removes the hot-path cost.
 */
object Glob {

    private val cache = object : LinkedHashMap<String, Regex>(CACHE_CAPACITY, 0.75f, true) {}
    private val neverMatches = Regex("(?!)")

    fun matches(pattern: String, name: String): Boolean =
        regexFor(pattern).matches(name.lowercase(Locale.ROOT))

    /** True if [name] is allowed: matches an include (or includes empty) and no exclude. */
    fun isAllowed(name: String, includeGlobs: List<String>, excludeGlobs: List<String>): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        val included = includeGlobs.isEmpty() || includeGlobs.any { regexFor(it).matches(lower) }
        if (!included) return false
        return excludeGlobs.none { regexFor(it).matches(lower) }
    }

    private fun regexFor(pattern: String): Regex {
        if (pattern.length > MAX_PATTERN_LENGTH) return neverMatches
        return synchronized(cache) {
            cache[pattern] ?: compile(pattern).also { compiled ->
                cache[pattern] = compiled
                while (cache.size > CACHE_CAPACITY) cache.remove(cache.keys.first())
            }
        }
    }

    private fun compile(pattern: String): Regex {
        val p = pattern.lowercase(Locale.ROOT)
        val sb = StringBuilder("^")
        var i = 0
        while (i < p.length) {
            val c = p[i]
            when (c) {
                '*' -> {
                    if (i + 1 < p.length && p[i + 1] == '*') {
                        sb.append(".*"); i++   // ** crosses path separators
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                    sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        return Regex(sb.toString())
    }

    private const val CACHE_CAPACITY = 128
    const val MAX_PATTERN_LENGTH = 256
}
