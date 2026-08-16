package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.ActiveSource
import com.example.familyphotoframe.data.settings.ActiveSourceKind

/** What a configured source is doing for playback right now. */
enum class SourceRole {
    /** The source the user chose; its photos are what they expect to see. */
    PRIMARY,
    /** Merged into the primary pool alongside [PRIMARY] (spec §9.3). */
    ALSO_PLAYING,
    /** Set up, but not part of the current pool. */
    CONFIGURED_IDLE,
    /** No usable connection settings yet. */
    NOT_CONFIGURED,
    /** Bundled samples, played only when nothing else is reachable. */
    FALLBACK,
}

/** Whether the frame can actually read from a source right now. */
enum class SourceAvailability {
    /** In the pool and answering. */
    OK,
    /** In the pool but unreachable; cached bytes are carrying playback (spec §9.3). */
    USING_CACHE,
    /** In the pool but unreachable, and dropped from it. */
    UNAVAILABLE,
    /** Not in the pool, so nothing has health-checked it recently. */
    UNKNOWN,
}

/** One row of the "which source is in use" indicator. */
data class SourceStatus(
    val kind: ActiveSourceKind,
    /** Stable pool id, or null for kinds that never join the pool (samples). */
    val sourceId: String?,
    val role: SourceRole,
    val availability: SourceAvailability,
    /** Non-secret connection summary (host/share, folder name); blank when unset. */
    val detail: String,
    val indexedPhotos: Int,
    val canBecomePrimary: Boolean,
    val canAlsoPlay: Boolean,
) {
    val configured: Boolean get() = role != SourceRole.NOT_CONFIGURED
    val inPlaybackPool: Boolean get() =
        role == SourceRole.PRIMARY || role == SourceRole.ALSO_PLAYING
}

/**
 * Turns the stored source configuration plus live pool membership into the rows the
 * settings UI and the web config render.
 *
 * Pure and total, in the same spirit as [SourcePoolPolicy]: the ViewModel owns the Room
 * counts and the health loop, this owns the rules about what they mean. Never carries a
 * secret — only the non-secret connection fields already shown in the setup forms.
 */
object SourceStatusPolicy {

    /** Kinds are listed in setup order so the card does not reshuffle as roles change. */
    val orderedKinds: List<ActiveSourceKind> = listOf(
        ActiveSourceKind.LOCAL_SAF,
        ActiveSourceKind.SMB,
        ActiveSourceKind.SYNOLOGY,
        ActiveSourceKind.WEBDAV,
        ActiveSourceKind.SAMPLES,
    )

    fun isConfigured(kind: ActiveSourceKind, source: ActiveSource): Boolean = when (kind) {
        ActiveSourceKind.LOCAL_SAF -> !source.treeUri.isNullOrBlank()
        ActiveSourceKind.SMB ->
            source.smb?.host?.isNotBlank() == true && source.smb?.share?.isNotBlank() == true
        ActiveSourceKind.SYNOLOGY -> source.synology?.baseUrl?.isNotBlank() == true
        ActiveSourceKind.WEBDAV -> source.webdav?.baseUrl?.isNotBlank() == true
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> false
    }

    /** Non-secret one-line summary of where the photos come from. */
    fun detail(kind: ActiveSourceKind, source: ActiveSource): String = when (kind) {
        ActiveSourceKind.LOCAL_SAF -> source.displayName
            .takeIf { it.isNotBlank() && source.kind == ActiveSourceKind.LOCAL_SAF }
            .orEmpty()
        ActiveSourceKind.SMB -> source.smb?.let { smb ->
            listOfNotNull(
                smb.host.takeIf { it.isNotBlank() },
                smb.share.takeIf { it.isNotBlank() },
                smb.path.takeIf { it.isNotBlank() },
            ).joinToString("/")
        }.orEmpty()
        ActiveSourceKind.SYNOLOGY -> source.synology?.let { syn ->
            listOfNotNull(
                syn.baseUrl.takeIf { it.isNotBlank() },
                syn.folderPath.takeIf { it.isNotBlank() },
            ).joinToString(" ")
        }.orEmpty()
        ActiveSourceKind.WEBDAV -> source.webdav?.let { dav ->
            listOfNotNull(
                dav.baseUrl.takeIf { it.isNotBlank() },
                dav.folderPath.takeIf { it.isNotBlank() },
            ).joinToString(" ")
        }.orEmpty()
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> ""
    }

    /**
     * @param unavailableSourceIds pool members the health loop has demoted.
     * @param stalePlayback true while cached bytes stand in for an unreachable primary.
     * @param indexedPhotos indexed row count per source id; missing means zero.
     */
    fun statuses(
        source: ActiveSource,
        unavailableSourceIds: Set<String>,
        stalePlayback: Boolean,
        indexedPhotos: Map<String, Int>,
        sourceIdFor: (ActiveSourceKind) -> String?,
        fallbackSourceId: String,
    ): List<SourceStatus> = orderedKinds.map { kind ->
        val sourceId = sourceIdFor(kind)
        val configured = isConfigured(kind, source)
        val role = when {
            kind == ActiveSourceKind.SAMPLES && source.kind == ActiveSourceKind.SAMPLES ->
                SourceRole.PRIMARY
            kind == ActiveSourceKind.SAMPLES -> SourceRole.FALLBACK
            !configured -> SourceRole.NOT_CONFIGURED
            kind == source.kind -> SourceRole.PRIMARY
            kind in source.alsoPlay -> SourceRole.ALSO_PLAYING
            else -> SourceRole.CONFIGURED_IDLE
        }
        val availability = when {
            kind == ActiveSourceKind.SAMPLES -> SourceAvailability.OK
            role == SourceRole.PRIMARY || role == SourceRole.ALSO_PLAYING -> when {
                sourceId == null || sourceId !in unavailableSourceIds -> SourceAvailability.OK
                // Stale playback is a whole-frame last resort, so it only describes the
                // chosen source: a demoted co-primary is simply dropped from the pool.
                stalePlayback && role == SourceRole.PRIMARY -> SourceAvailability.USING_CACHE
                else -> SourceAvailability.UNAVAILABLE
            }
            else -> SourceAvailability.UNKNOWN
        }
        SourceStatus(
            kind = kind,
            sourceId = if (kind == ActiveSourceKind.SAMPLES) fallbackSourceId else sourceId,
            role = role,
            availability = availability,
            detail = detail(kind, source),
            indexedPhotos = indexedPhotos[
                if (kind == ActiveSourceKind.SAMPLES) fallbackSourceId else sourceId
            ] ?: 0,
            canBecomePrimary = role != SourceRole.PRIMARY &&
                (configured || kind == ActiveSourceKind.SAMPLES),
            // Samples are always the fallback pool, never a co-primary (spec §9.3).
            canAlsoPlay = configured && kind != ActiveSourceKind.SAMPLES && kind != source.kind,
        )
    }
}
