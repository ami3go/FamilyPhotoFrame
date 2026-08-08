package com.example.familyphotoframe.data.index

/**
 * Canonical direct-directory identity shared by indexing, shuffle keys, folder UI,
 * migrations, and exact folder queries. Sources already provide a normalized relative
 * path; this helper only normalizes separators and extracts its direct parent.
 */
object CanonicalPhotoPath {
    const val ROOT_DIRECTORY = "@root"

    fun normalize(relativePath: String): String = relativePath
        .replace('\\', '/')
        .trim()
        .trimStart('/')
        .replace(Regex("/{2,}"), "/")

    fun directDirectory(relativePath: String): String {
        val normalized = normalize(relativePath)
        return normalized.substringBeforeLast('/', "").ifBlank { ROOT_DIRECTORY }
    }
}
