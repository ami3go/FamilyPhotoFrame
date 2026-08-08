fun runScanMergePolicyChecks() {
    println("-- scan merge policy --")
    fun row(stable: String) = PhotoItemEntity(
        stableId = stable,
        sourceId = "smb",
        normalizedPath = "album/photo.heic",
        folderName = "album",
        fileName = "photo.heic",
        mimeType = "image/heic",
        sizeBytes = 1,
        fileModifiedEpochMs = 1,
        openToken = "token",
        indexedAtEpochMs = 2,
    )
    val previous = row("same").copy(
        id = 42,
        isHidden = true,
        isFavorite = true,
        decodeFailureCount = 1_000_000,
        cacheKey = "same",
        width = 4032,
        caption = "kept",
        exifScannedAtEpochMs = 7,
    )
    val same = ScanMergePolicy.merge(row("same"), previous)
    check("same bytes preserve suppression", 1_000_000, same.decodeFailureCount)
    check("same bytes preserve cache", "same", same.cacheKey)
    check("same bytes preserve EXIF", 4032, same.width)
    check("same path preserves curation", true, same.isFavorite && same.isHidden)

    val replaced = ScanMergePolicy.merge(row("new"), previous)
    check("new bytes reset suppression", 0, replaced.decodeFailureCount)
    check("new bytes clear cache", null, replaced.cacheKey)
    check("new bytes clear stale EXIF", null, replaced.width)
    check("new bytes keep path curation", true, replaced.isFavorite && replaced.isHidden)
}
