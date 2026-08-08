package com.example.familyphotoframe.data.source

/**
 * Stable source identifiers shared by persistence, engine selection, cache routing,
 * diagnostics, and web status. Keeping them in the data layer prevents individual
 * callers from re-encoding the same string switch differently.
 */
object BuiltInSourceIds {
    const val LOCAL_SAF = "local_saf"
    const val FALLBACK = "fallback"
    const val SMB = "smb"
    const val SYNOLOGY = "synology"
    const val WEBDAV = "webdav"
    const val LOCAL_UPLOADS = "local_uploads"

    /** Remote sources must be materialized through [com.example.familyphotoframe.data.cache.MediaCache]. */
    fun requiresMediaCache(sourceId: String): Boolean =
        sourceId == SMB || sourceId == SYNOLOGY || sourceId == WEBDAV
}
