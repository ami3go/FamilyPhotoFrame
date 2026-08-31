fun runFolderSelectionSqlChecks() {
    println("-- bounded folder SQL selection --")
    val folders = (0 until 2_000).map { "source\u001ffolder-$it" }
    val encoded = FolderSelectionSql.encode(folders)
    check("all folder keys encoded in one value", true, folders.all { "\u001e$it\u001e" in encoded })
    check("duplicates removed", 1, "\u001esource\u001ffolder-1\u001e".toRegex().findAll(encoded).count())
    check(
        "record separator input rejected",
        false,
        "bad\u001efolder" in FolderSelectionSql.encode(listOf("ok", "bad\u001efolder")),
    )
}
