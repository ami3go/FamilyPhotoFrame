enum class ActiveSourceKind { NONE, LOCAL_SAF, SAMPLES, SMB, SYNOLOGY, WEBDAV }
enum class UnreachablePolicy { FALLBACK_SAMPLES, STALE_CACHE }

data class ActiveSource(
    val kind: ActiveSourceKind = ActiveSourceKind.NONE,
    val alsoPlay: Set<ActiveSourceKind> = emptySet(),
    val treeUri: String? = null,
    val smb: SmbSettings? = null,
    val synology: SynologySettings? = null,
    val webdav: WebDavSettings? = null,
)

data class SourceSignaturePlaylist(val sourceIds: Set<String> = emptySet())

data class SourceSignaturePlaylists(
    val active: SourceSignaturePlaylist = SourceSignaturePlaylist(),
) {
    fun activePlaylist(): SourceSignaturePlaylist = active
}

data class AppSettings(
    val source: ActiveSource = ActiveSource(),
    val filters: FilterSettings = FilterSettings(),
    val playlists: SourceSignaturePlaylists = SourceSignaturePlaylists(),
    val onUnreachable: UnreachablePolicy = UnreachablePolicy.FALLBACK_SAMPLES,
)
