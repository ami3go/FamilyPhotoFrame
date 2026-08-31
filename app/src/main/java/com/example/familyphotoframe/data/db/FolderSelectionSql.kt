package com.example.familyphotoframe.data.db

/**
 * Encodes an arbitrary number of selected folder keys into one SQLite parameter.
 *
 * Room expands every `List<String>` occurrence into one bind variable per value. The
 * folder predicate appears twice (canonical key and legacy folder name), which exceeded
 * Android 5.x's 999-variable SQLite limit at roughly 500 folders. A control-character
 * framed value keeps exact membership semantics while using one bounded parameter.
 */
object FolderSelectionSql {
    private const val SEPARATOR = '\u001e'

    fun encode(folders: Collection<String>): String = folders.asSequence()
        .filter { it.isNotEmpty() && SEPARATOR !in it }
        .distinct()
        .joinToString(separator = SEPARATOR.toString(), prefix = SEPARATOR.toString(), postfix = SEPARATOR.toString())
}
