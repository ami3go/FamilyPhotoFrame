package com.example.familyphotoframe.data.source

import com.example.familyphotoframe.data.diagnostics.RuntimeResourceTracker
import com.example.familyphotoframe.util.Glob
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/** SMB credentials resolved from the Keystore SecretStore just before use (never stored here as plaintext beyond this object's lifetime). */
data class SmbCredentials(val domain: String, val user: String, val password: String)

/** Connection parameters for an SMB share (mirrors SmbSourceConfigEntity). */
data class SmbConnection(
    val host: String,
    val share: String,
    val path: String,
    val connectionTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 15_000,
    val listTimeoutMs: Long = 15_000,
)

/**
 * SMB/NAS source (spec §8) using jcifs-ng (SMB2/3). Treated as unreliable I/O: every
 * call is off-main, cancellable, and time-bounded, and failures map to the typed
 * [SourceError] taxonomy via [SourceErrorMapper]. The scan streams a Flow of
 * [ScanEvent] and never returns a List (Contract Rule 3). Traversal uses an explicit
 * work-stack, not recursion, to keep deep NAS trees safe.
 *
 * Credentials live only in the [CIFSContext]; the [PhotoItem.openToken] is the plain
 * `smb://host/share/...` URL, which contains no password (Contract Rule 5).
 */
class SmbPhotoSource(
    override val id: SourceId,
    private val conn: SmbConnection,
    credentials: SmbCredentials,
    private val io: CoroutineDispatcher,
    private val resourceTracker: RuntimeResourceTracker = RuntimeResourceTracker(),
) : PhotoSource {

    override val type: SourceType = SourceType.SMB_SOURCE

    private val shareBase = "smb://${conn.host}/${conn.share}/"

    private data class TrackedContext(
        val context: CIFSContext,
        val resourceLease: RuntimeResourceTracker.Lease,
    )

    /**
     * One CIFS context is shared by every operation on this source. A lease spans the
     * whole blocking operation (or the returned stream's lifetime), so close() can mark
     * the source permanently closed without destroying a transport that is still serving
     * a scan, image decode, cache fill, EXIF read, or dimension probe.
     */
    private val contextOwner = DeferredCloseResource(
        factory = {
            val props = Properties().apply {
                put("jcifs.smb.client.minVersion", "SMB202")   // SMB2+ only (no SMB1)
                put("jcifs.smb.client.maxVersion", "SMB311")
                put("jcifs.smb.client.connTimeout", conn.connectionTimeoutMs.toString())
                put("jcifs.smb.client.soTimeout", conn.readTimeoutMs.toString())
                // Directory enumeration is a request/response operation rather than a
                // streaming read, so give it the independently configured list bound.
                put("jcifs.smb.client.responseTimeout", conn.listTimeoutMs.toString())
                put("jcifs.smb.client.dfs.disabled", "true")   // simple NAS: skip DFS lookups
            }
            val resourceLease = resourceTracker.openSmbContext()
            var base: CIFSContext? = null
            try {
                val createdBase: CIFSContext = BaseContext(PropertyConfiguration(props))
                base = createdBase
                TrackedContext(
                    context = createdBase.withCredentials(
                        NtlmPasswordAuthenticator(
                            credentials.domain,
                            credentials.user,
                            credentials.password,
                        )
                    ),
                    resourceLease = resourceLease,
                )
            } catch (error: Throwable) {
                // BaseContext owns shared transport/buffer services even when the
                // credential wrapper cannot be created. Do not strand that partial root.
                runCatching { base?.close() }
                resourceLease.close()
                throw error
            }
        },
        closer = { tracked ->
            try {
                runCatching { tracked.context.close() }
            } finally {
                tracked.resourceLease.close()
            }
        },
    )

    /**
     * Permanently rejects new work. The jcifs context and its transport pool are closed
     * immediately when idle, or by the final in-flight operation/stream when one is
     * still using them. This avoids both reopening-after-close and mid-request teardown.
     *
     * On API 22 those buffers live on the same ~174 MB Java heap as decoded bitmaps, so a
     * context that is dropped without being closed costs roughly 130 KB that no GC can
     * reclaim while the transport is still registered.
     */
    override fun close() {
        contextOwner.close()
    }

    private fun rootUrl(): String {
        val p = conn.path.trim('/')
        return if (p.isEmpty()) shareBase else "$shareBase$p/"
    }

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        val lease = contextOwner.acquire()
        try {
            withTimeoutOrNull(timeoutMs) {
                try {
                    val root = SmbFile(rootUrl(), lease.value.context)
                    when {
                        !root.exists() -> SourceHealth.Missing
                        !root.isDirectory -> SourceHealth.ProviderError("not_a_directory")
                        else -> SourceHealth.Ok
                    }
                } catch (e: SmbAuthException) {
                    SourceHealth.NeedsPermission
                } catch (e: Exception) {
                    SourceErrorMapper.toHealth(e)
                }
            } ?: SourceHealth.Unavailable
        } finally {
            lease.close()
        }
    }

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val lease = contextOwner.acquire()
        try {
            val stack = ArrayDeque<SmbFile>()
            stack.addLast(SmbFile(rootUrl(), lease.value.context))
            var scanned = 0L

            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.removeLast()
                val children = try {
                    dir.listFiles()
                } catch (e: SmbAuthException) {
                    emit(ScanEvent.Error(relPath(dir), SourceError.AuthFailed)); return@flow
                } catch (e: Exception) {
                    emit(ScanEvent.Error(relPath(dir), SourceErrorMapper.map(e))); continue
                } ?: continue

                for (child in children) {
                    coroutineContext.ensureActive()
                    val rawName = child.name
                    val name = rawName.trimEnd('/')
                    val isDir = try { child.isDirectory } catch (e: Exception) { false }

                    if (isDir) {
                        if (options.allowsFolder(name)) {
                            stack.addLast(child)
                        }
                        continue
                    }
                    if (!options.allowsFile(name)) continue
                    if (!SupportedFormats.isSupported(name, null)) continue

                    val size = try { child.length() } catch (e: Exception) { 0L }
                    val modified = try { child.lastModified } catch (e: Exception) { 0L }
                    val normalized = relPath(child)
                    emit(
                        ScanEvent.FileFound(
                            PhotoItem(
                                stableId = StableId.of(id.value, normalized, size, modified),
                                sourceId = id,
                                normalizedPath = normalized,
                                folderName = normalized.substringBeforeLast('/', "").substringAfterLast('/'),
                                fileName = name,
                                mimeType = null,
                                sizeBytes = size,
                                fileModifiedEpochMs = modified,
                                openToken = child.path,   // smb:// URL; no credentials in it
                            )
                        )
                    )
                    scanned++
                    if (scanned % 100 == 0L) emit(ScanEvent.DirectoryProgress(relPath(dir), scanned))
                }
            }
            emit(ScanEvent.Finished(ScanCursor("smb:${System.currentTimeMillis()}")))
        } finally {
            lease.close()
        }
    }.flowOn(io)

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream = withContext(io) {
        val lease = contextOwner.acquire()
        var input: InputStream? = null
        var resourceLease: RuntimeResourceTracker.Lease? = null
        try {
            val openedInput = SmbFile(item.openToken, lease.value.context).inputStream
            input = openedInput
            val openedResourceLease = resourceTracker.openSmbStream(
                purpose = options.purpose.toTrackerPurpose(),
                deadlineMs = options.timeoutMs,
            )
            resourceLease = openedResourceLease
            DeadlineInputStream(
                LeaseReleasingInputStream(openedInput, lease, openedResourceLease),
                options.timeoutMs,
                onDeadlineExpired = openedResourceLease::markDeadlineExpired,
            )
        } catch (error: Throwable) {
            runCatching { input?.close() }
            resourceLease?.close()
            lease.close()
            throw error
        }
    }

    private class LeaseReleasingInputStream(
        input: InputStream,
        private val lease: DeferredCloseResource.Lease<TrackedContext>,
        private val resourceLease: RuntimeResourceTracker.Lease,
    ) : FilterInputStream(input) {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                super.close()
            } finally {
                try {
                    lease.close()
                } finally {
                    resourceLease.close()
                }
            }
        }
    }

    /** Path within the share, relative to the configured root. */
    private fun relPath(f: SmbFile): String = f.path.removePrefix(shareBase).trimEnd('/')

    private fun OpenPurpose.toTrackerPurpose(): RuntimeResourceTracker.SmbStreamPurpose = when (this) {
        OpenPurpose.DISPLAY_CACHE -> RuntimeResourceTracker.SmbStreamPurpose.DISPLAY_CACHE
        OpenPurpose.COLLAGE_BOUNDS -> RuntimeResourceTracker.SmbStreamPurpose.COLLAGE_BOUNDS
        OpenPurpose.EXIF_METADATA -> RuntimeResourceTracker.SmbStreamPurpose.EXIF_METADATA
        OpenPurpose.CONTENT_HASH -> RuntimeResourceTracker.SmbStreamPurpose.CONTENT_HASH
        OpenPurpose.INDEX_METADATA -> RuntimeResourceTracker.SmbStreamPurpose.INDEX_METADATA
        OpenPurpose.OTHER -> RuntimeResourceTracker.SmbStreamPurpose.OTHER
    }
}
