package com.example.familyphotoframe.data.source

import com.example.familyphotoframe.util.Glob
import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/** Stable identifier for a configured source. */
@JvmInline
value class SourceId(val value: String)

/** Storage source taxonomy (spec §7.0). Phase 0 implements the first two. */
enum class SourceType(val isLocal: Boolean) {
    LOCAL_SAF_FOLDER(true),
    APP_PRIVATE_BUILTIN(true),
    APP_PRIVATE_UPLOADS(true),
    SMB_SOURCE(false),            // Phase 1
    SYNOLOGY_FILE_STATION(false), // Phase 2 (ROADMAP.md: network photo-app sources, step 1)
    WEBDAV(false),                // Nextcloud/ownCloud/Apache and most NAS WebDAV mounts
}

/** Role in the slideshow pool (spec §9.3). */
enum class SourceRole { PRIMARY, FALLBACK }

/** Health of a source after a check (spec §7.11, §8). */
sealed interface SourceHealth {
    data object Ok : SourceHealth
    data object NeedsPermission : SourceHealth
    data object Missing : SourceHealth
    data class ProviderError(val detail: String) : SourceHealth
    data object Unavailable : SourceHealth
}

/**
 * Typed source errors (spec §8.2). The full SMB taxonomy is declared now so the
 * engine, diagnostics and UI strings can be written against stable codes; Phase 0
 * uses the SAF-relevant subset. The HTTP photo-app cases were added alongside the
 * Synology File Station source (ROADMAP.md "Network photo-app sources").
 */
enum class SourceError {
    AuthFailed,
    // ---- HTTP photo-app sources (Synology File Station, Phase 2 increment 10) ----
    /** Account requires a 2FA one-time code, or the supplied code was wrong. */
    TwoFactorRequired,
    /** TLS handshake rejected — typically a self-signed DSM certificate. */
    CertUntrusted,
    /** The session id expired or was invalidated; re-auth and retry. */
    SessionExpired,
    /** QuickConnect relay could not be resolved or refused the connection. */
    QuickConnectUnavailable,
    HostUnreachable,
    ShareNotFound,
    PermissionDenied,
    Timeout,
    ProtocolError,
    FileGone,
    CorruptImage,
    Cancelled,
    PermissionRevoked,
    FolderMissing,
    FolderBlocked,
    ProviderError,
    Unknown,
}

/** Opaque, source-defined cursor for resumable scans (spec §5.2). */
data class ScanCursor(val token: String)

data class ScanOptions(
    val includeSubfolders: Boolean = true,
    val includeGlobs: List<String> = SupportedFormats.defaultIncludeGlobs,
    val excludeGlobs: List<String> = listOf(".*", "Thumbs.db", "@eaDir/**"),
    /**
     * Folder names to prune from traversal entirely (spec §20). Distinct from
     * [excludeGlobs]: matching here skips the whole subtree rather than filtering files
     * one at a time, which on a NAS is the difference between not listing a folder and
     * listing every file in it only to discard each one.
     */
    val excludeFolders: List<String> = listOf("@eaDir"),
) {
    /**
     * Whether traversal should descend into a folder. Every source asks this rather than
     * re-deriving the rule, so a filter change cannot apply to SMB but not to WebDAV.
     */
    fun allowsFolder(name: String): Boolean {
        if (!includeSubfolders) return false
        if (excludeFolders.any { it.equals(name, ignoreCase = true) }) return false
        return Glob.isAllowed(name, listOf("*"), excludeGlobs)
    }

    /** Whether a file name passes the include/exclude globs. */
    fun allowsFile(name: String): Boolean = Glob.isAllowed(name, includeGlobs, excludeGlobs)
}

data class OpenOptions(
    val timeoutMs: Long = 8_000,
    /** Force the source to return original file bytes rather than a display thumbnail. */
    val preferOriginal: Boolean = false,
)

/**
 * Indexable photo record produced by a scan (spec §6.1 fields, subset for Phase 0).
 * [openToken] is what the owning source needs to re-open the bytes later — for SAF
 * this is the document content-URI string; for app-private it is the absolute file
 * path within app storage. It never contains a secret.
 */
data class PhotoItem(
    val stableId: String,
    val sourceId: SourceId,
    val normalizedPath: String,
    val folderName: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val fileModifiedEpochMs: Long,
    val openToken: String,
)

/** Streaming scan events (spec §5.2). The scan returns a Flow of these, never a List. */
sealed interface ScanEvent {
    data class FileFound(val item: PhotoItem) : ScanEvent
    data class DirectoryProgress(val path: String, val scannedCount: Long) : ScanEvent
    data class DeletedOrMissing(val stableId: String) : ScanEvent
    data class Error(val path: String, val error: SourceError) : ScanEvent
    data class Finished(val cursor: ScanCursor) : ScanEvent
}

/**
 * Source abstraction (spec §5.2). The slideshow engine never enumerates storage;
 * it asks this layer for displayable bytes. All InputStreams are caller-owned and
 * must be closed with `use {}`. No implementation performs I/O on the main thread.
 */
interface PhotoSource {
    val id: SourceId
    val type: SourceType

    suspend fun healthCheck(timeoutMs: Long): SourceHealth

    fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent>

    /** Re-open bytes for a previously indexed item, using its [PhotoItem.openToken]. */
    suspend fun openStream(item: PhotoItem, options: OpenOptions = OpenOptions()): InputStream

    /**
     * Releases any long-lived transport this source owns.
     *
     * Sources that open a connection per call hold nothing between calls, so the default
     * is a no-op. Implementations that keep a session — notably [SmbPhotoSource], whose
     * `CIFSContext` owns a buffer cache and a transport pool — must release it here, and
     * whoever built the source must call this once it is no longer the active source.
     */
    fun close() {}

    /**
     * Suspended variant used by one-shot operations and orderly application teardown.
     *
     * Most sources release synchronously, so the default delegates to [close]. A source
     * whose shutdown includes network I/O (for example a Synology API logout) overrides
     * this method so callers that already run in a coroutine can wait for the bounded
     * cleanup instead of leaving another session behind.
     */
    suspend fun shutdown() {
        close()
    }
}

/** Convert a public Long timeout to the positive Int range required by java.net APIs. */
internal fun Long.toSocketTimeoutMillis(): Int = coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
