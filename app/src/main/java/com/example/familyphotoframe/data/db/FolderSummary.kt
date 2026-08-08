package com.example.familyphotoframe.data.db

/** Exact direct-directory projection used by Android and Web Control folder actions. */
data class FolderSummary(
    val sourceId: String,
    val canonicalDirectory: String,
    val name: String,
    val photoCount: Int,
) {
    val selectionKey: String get() = "$sourceId\u001f$canonicalDirectory"
    val displayPath: String get() = if (canonicalDirectory == "@root") "(root)" else canonicalDirectory
}
