package com.example.familyphotoframe.data.settings

/**
 * Stable identity of the settings that materially affect source activation and indexing.
 *
 * Only sources that currently participate in playback are included. This keeps an edit
 * to an unused NAS form from interrupting a working slideshow, while still detecting a
 * configuration change to a merged co-primary source. Secret values are never present
 * in [AppSettings]; credential references are sufficient to distinguish secret scopes.
 */
object SourceRuntimeSignature {

    fun of(settings: AppSettings): String {
        val source = settings.source
        val playingKinds = (listOf(source.kind) + source.alsoPlay)
            .distinct()
            .sortedBy { it.name }

        val sourcePart = playingKinds.joinToString(separator = ";") { kind ->
            signatureFor(kind, source)
        }
        val filters = settings.filters
        val filterPart = buildString {
            append("recursive=").append(filters.includeSubfolders)
            append("|include=").append(filters.cleanIncludes.joinToString(","))
            append("|exclude=").append(filters.cleanExcludes.joinToString(","))
            append("|excludeFolders=").append(filters.cleanExcludeFolders.joinToString(","))
        }
        val playlistSources = settings.playlists.activePlaylist().sourceIds
            .sorted()
            .joinToString(",")

        return "$sourcePart|$filterPart|playlistSources=$playlistSources" +
            "|unreachable=${settings.onUnreachable.name}"
    }

    private fun signatureFor(kind: ActiveSourceKind, source: ActiveSource): String = when (kind) {
        ActiveSourceKind.NONE -> "none"
        ActiveSourceKind.SAMPLES -> "samples"
        ActiveSourceKind.LOCAL_SAF -> "saf:${source.treeUri.orEmpty()}"
        ActiveSourceKind.SMB -> source.smb?.let { smb ->
            "smb:${smb.host}/${smb.share}/${smb.path}/${smb.domain}/${smb.user}/${smb.credentialRef}"
        } ?: "smb:missing"
        ActiveSourceKind.SYNOLOGY -> source.synology?.let { syn ->
            "syn:${syn.baseUrl}/${syn.folderPath}/${syn.user}/${syn.credentialRef}" +
                "/${syn.useThumbnails}/${syn.thumbnailSize}/${syn.pinnedCertSha256}"
        } ?: "syn:missing"
        ActiveSourceKind.WEBDAV -> source.webdav?.let { dav ->
            "dav:${dav.baseUrl}/${dav.rootPath}/${dav.folderPath}/${dav.user}" +
                "/${dav.credentialRef}/${dav.pinnedCertSha256}"
        } ?: "dav:missing"
    }
}
