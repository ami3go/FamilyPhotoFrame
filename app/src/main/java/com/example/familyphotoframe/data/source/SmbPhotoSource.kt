package com.example.familyphotoframe.data.source

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
import java.io.InputStream
import java.util.Properties
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
) : PhotoSource {

    override val type: SourceType = SourceType.SMB_SOURCE

    private val shareBase = "smb://${conn.host}/${conn.share}/"

    // Guards contextDelegate and [closed] together so a context can never be created
    // after close() has already decided there was nothing to release (the delegate's
    // own SYNCHRONIZED mode only protects its single initialization, not this check).
    // NONE is safe here because every access — including first initialization — now
    // goes through requireOpenContext() under this same lock.
    private val closeLock = Any()
    @Volatile private var closed = false

    // Held as the delegate, not just the value, so [close] can tell an unused source
    // (nothing to release) from one that actually opened a connection. Touching the
    // value to find out would create the very context we are trying to avoid.
    private val contextDelegate = lazy(LazyThreadSafetyMode.NONE) {
        val props = Properties().apply {
            put("jcifs.smb.client.minVersion", "SMB202")   // SMB2+ only (no SMB1)
            put("jcifs.smb.client.maxVersion", "SMB311")
            put("jcifs.smb.client.connTimeout", conn.connectionTimeoutMs.toString())
            put("jcifs.smb.client.soTimeout", conn.readTimeoutMs.toString())
            put("jcifs.smb.client.responseTimeout", conn.readTimeoutMs.toString())
            put("jcifs.smb.client.dfs.disabled", "true")   // simple NAS: skip DFS lookups
        }
        val base: CIFSContext = BaseContext(PropertyConfiguration(props))
        base.withCredentials(
            NtlmPasswordAuthenticator(credentials.domain, credentials.user, credentials.password)
        )
    }

    /**
     * Every call site that needs the context goes through here instead of touching
     * [contextDelegate] directly, so "closed" and "materialize the context" can never
     * interleave: without this, close() could see nothing initialized yet and return,
     * while a concurrent caller (e.g. a backfill job racing a teardown) initializes a
     * fresh context a moment later that nothing will ever close again.
     */
    private fun requireOpenContext(): CIFSContext = synchronized(closeLock) {
        check(!closed) { "SmbPhotoSource(${id.value}) used after close()" }
        contextDelegate.value
    }

    /**
     * Closes the jcifs context, releasing its transport pool and buffer cache, and
     * permanently marks this source closed so it cannot be silently reopened by a
     * caller racing this call (see [requireOpenContext]).
     *
     * On API 22 those buffers live on the same ~174 MB Java heap as decoded bitmaps, so a
     * context that is dropped without being closed costs roughly 130 KB that no GC can
     * reclaim while the transport is still registered.
     */
    override fun close() {
        synchronized(closeLock) {
            if (closed) return
            closed = true
            if (contextDelegate.isInitialized()) {
                runCatching { contextDelegate.value.close() }
            }
        }
    }

    private fun rootUrl(): String {
        val p = conn.path.trim('/')
        return if (p.isEmpty()) shareBase else "$shareBase$p/"
    }

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val root = SmbFile(rootUrl(), requireOpenContext())
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
    }

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val stack = ArrayDeque<SmbFile>()
        stack.addLast(SmbFile(rootUrl(), requireOpenContext()))
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
    }.flowOn(io)

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream =
        withContext(io) { SmbFile(item.openToken, requireOpenContext()).inputStream }

    /** Path within the share, relative to the configured root. */
    private fun relPath(f: SmbFile): String = f.path.removePrefix(shareBase).trimEnd('/')
}
