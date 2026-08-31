fun runPlaylistSettingsChecks() {
    println("-- bounded playlist settings --")

    fun largeFolderSet(prefix: String): Set<String> = (0 until 976)
        .map { index -> "$prefix-$index".padEnd(2_048, 'x') }
        .toSet()

    val normalized = PlaylistSettings(
        playlists = listOf(
            SlideshowPlaylist("user_a", "A", folderNames = largeFolderSet("a")),
            SlideshowPlaylist("user_b", "B", folderNames = largeFolderSet("b")),
            SlideshowPlaylist(
                "user_over_budget",
                "Over budget",
                folderNames = setOf("c".padEnd(2_048, 'x'), "d".padEnd(2_048, 'x')),
            ),
        ),
    ).withCurrentDefaults()

    check("first large playlist retained", true, normalized.playlists.any { it.id == "user_a" })
    check("second large playlist retained", true, normalized.playlists.any { it.id == "user_b" })
    check(
        "aggregate folder payload remains bounded",
        false,
        normalized.playlists.any { it.id == "user_over_budget" },
    )
    check(
        "folder payload size respects aggregate limit",
        true,
        normalized.playlists
            .filter { it.id !in PlaylistSettings.BUILT_IN_IDS }
            .flatMap { it.folderNames }
            .sumOf { it.length.toLong() } <= PlaylistSettings.MAX_TOTAL_PLAYLIST_FOLDER_CHARS,
    )
}
