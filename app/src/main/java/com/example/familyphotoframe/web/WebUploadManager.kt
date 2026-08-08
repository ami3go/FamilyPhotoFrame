package com.example.familyphotoframe.web

import android.graphics.BitmapFactory
import android.os.SystemClock
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.index.IndexProgress
import com.example.familyphotoframe.data.index.Indexer
import com.example.familyphotoframe.data.index.ScanDiagnosticContext
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.data.settings.UploadDuplicatePolicy
import com.example.familyphotoframe.data.source.LocalUploadPhotoSource
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.util.toHexString
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Bounded, app-private bulk upload service. One HTTP request carries one file; the
 * complete file is never buffered in memory. Files become visible only after validation
 * and an atomic rename inside the local upload library.
 */
class WebUploadManager(
    private val source: LocalUploadPhotoSource,
    private val settings: SettingsRepository,
    private val indexer: Indexer,
    private val diagnostics: DiagnosticsLog,
    private val io: CoroutineDispatcher,
) {
    private data class FileStatus(
        val clientId: String,
        val name: String,
        val size: Long,
        val state: String,
        val message: String = "",
    )

    private data class Session(
        val id: String,
        val ownerKey: String,
        val expectedFiles: Int,
        val expectedBytes: Long,
        val duplicatePolicy: UploadDuplicatePolicy,
        val createdAtMs: Long = System.currentTimeMillis(),
        @Volatile var cancelled: Boolean = false,
        @Volatile var completed: Boolean = false,
        @Volatile var finishedAtMs: Long = 0L,
        var reservedFiles: Int = 0,
        var reservedBytes: Long = 0L,
        val files: MutableMap<String, FileStatus> = ConcurrentHashMap(),
        val parts: MutableSet<File> = java.util.Collections.synchronizedSet(linkedSetOf()),
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    /** Makes purge/count/insert one admission transaction across concurrent requests. */
    private val admissionLock = Any()
    @Volatile private var acceptingSessions = true
    private val writeMutex = Mutex()
    private val indexMutex = Mutex()
    private val uploadSlots = Semaphore(MAX_CONCURRENT_UPLOADS)

    suspend fun create(ownerToken: String, fileCount: Int, totalBytes: Long, policyName: String?): JsonObject {
        val cfg = settings.settings.first().webUpload.normalized()
        require(cfg.enabled) { "Web uploads are disabled on the frame" }
        require(fileCount in 1..cfg.maxBatchFiles) { "Batch must contain 1-${cfg.maxBatchFiles} files" }
        require(totalBytes in 1..MAX_BATCH_BYTES) { "Batch is too large or empty" }
        val ownerKey = ownerKey(ownerToken)
        val root = source.ensureDirectory()
        val reserve = totalBytes + totalBytes / 10L + STORAGE_RESERVE_BYTES
        require(root.usableSpace >= reserve) { "Not enough free storage for this batch" }
        val policy = policyName?.let { runCatching { UploadDuplicatePolicy.valueOf(it) }.getOrNull() }
            ?: cfg.duplicatePolicy
        val id = UUID.randomUUID().toString().replace("-", "")
        val session = Session(id, ownerKey, fileCount, totalBytes, policy)
        synchronized(admissionLock) {
            require(acceptingSessions) { "Web uploads are temporarily unavailable" }
            purgeExpiredLocked()
            trimFinishedSessionsLocked()
            require(sessions.values.count { !it.cancelled && !it.completed } < MAX_ACTIVE_SESSIONS) {
                "Too many active upload sessions"
            }
            require(sessions.values.count {
                it.ownerKey == ownerKey && !it.cancelled && !it.completed
            } < MAX_SESSIONS_PER_OWNER) {
                "Too many active upload sessions for this browser"
            }
            sessions[id] = session
        }
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "WEB_UPLOAD_SESSION_CREATED",
            "sessionToken" to diagnosticToken(id, "upload_session"),
            "fileCount" to fileCount.toString(),
            "sizeBytes" to totalBytes.toString(),
        )
        return status(ownerToken, id)
    }

    suspend fun status(ownerToken: String, id: String): JsonObject {
        val session = ownedSession(ownerToken, id)
        return buildJsonObject {
            put("sessionId", session.id)
            put("expectedFiles", session.expectedFiles)
            put("expectedBytes", session.expectedBytes)
            put("cancelled", session.cancelled)
            put("completed", session.completed)
            put("createdAtEpochMs", session.createdAtMs)
            put("files", buildJsonArray {
                session.files.values.sortedBy { it.clientId }.forEach { f ->
                    add(buildJsonObject {
                        put("clientId", f.clientId)
                        put("name", f.name)
                        put("size", f.size)
                        put("state", f.state)
                        put("message", f.message)
                    })
                }
            })
        }
    }

    suspend fun upload(
        ownerToken: String,
        sessionId: String,
        clientId: String,
        encodedName: String,
        declaredSize: Long,
        input: InputStream,
    ): JsonObject = uploadSlots.withPermit {
        withContext(io) {
            val session = ownedSession(ownerToken, sessionId)
            require(!session.cancelled) { "Upload session was cancelled" }
            val cfg = settings.settings.first().webUpload.normalized()
            require(cfg.enabled) { "Web uploads are disabled on the frame" }
            require(clientId.isNotBlank() && clientId.length <= 80) { "Invalid upload file id" }

            var reservationError: String? = null
            synchronized(session) {
                require(session.files[clientId] == null) { "This file was already submitted" }
                require(session.reservedFiles < session.expectedFiles) { "Upload session file count exceeded" }
                session.reservedFiles += 1
                if (declaredSize <= 0L) {
                    reservationError = "File size must be positive"
                } else if (declaredSize > session.expectedBytes - session.reservedBytes) {
                    reservationError = "Upload session byte total exceeded"
                } else {
                    session.reservedBytes += declaredSize
                }
                session.files[clientId] = FileStatus(
                    clientId = clientId,
                    name = encodedName.take(180),
                    size = declaredSize.coerceAtLeast(0L),
                    state = "UPLOADING",
                )
            }

            var part: File? = null
            var safeName = encodedName.take(180)
            try {
                require(reservationError == null) { reservationError.orEmpty() }
                require(declaredSize in 1..cfg.maxFileBytes) { "File exceeds the configured size limit" }
                safeName = sanitizeName(encodedName)
                require(isSupportedName(safeName)) { "Unsupported image format" }

                val root = source.ensureDirectory()
                require(root.usableSpace >= declaredSize + STORAGE_RESERVE_BYTES) { "Storage reserve would be exceeded" }
                val incoming = File(root, ".incoming")
                require(incoming.isDirectory || incoming.mkdirs()) { "Could not create upload staging directory" }
                val staged = File(incoming, "${UUID.randomUUID()}.part")
                part = staged
                session.parts.add(staged)
                session.files[clientId] = FileStatus(clientId, safeName, declaredSize, "UPLOADING")
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    "WEB_UPLOAD_FILE_STARTED",
                    "photoToken" to diagnosticToken(safeName, "photo"),
                    "sizeBytes" to declaredSize.toString(),
                )

                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                staged.outputStream().buffered(STREAM_BUFFER_BYTES).use { out ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        if (session.cancelled) throw IllegalStateException("Upload session was cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > declaredSize || written > cfg.maxFileBytes) {
                            throw IllegalArgumentException("Upload body exceeded declared or configured size")
                        }
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
                require(written == declaredSize) { "Upload ended before the declared size" }
                validateImage(staged, safeName)
                val sha = digest.digest().toHexString()
                val final = writeMutex.withLock { chooseDestination(root, safeName, staged, sha, session.duplicatePolicy) }
                session.parts.remove(staged)
                val state = if (final == null) "DUPLICATE" else "COMPLETED"
                session.files[clientId] = FileStatus(clientId, safeName, written, state)
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (final == null) "WEB_UPLOAD_FILE_DUPLICATE" else "WEB_UPLOAD_FILE_COMPLETED",
                    "photoToken" to diagnosticToken(safeName, "photo"),
                    "sizeBytes" to written.toString(),
                )
                status(ownerToken, sessionId)
            } catch (e: Exception) {
                part?.delete()
                part?.let { session.parts.remove(it) }
                session.files[clientId] = FileStatus(
                    clientId,
                    safeName,
                    declaredSize.coerceAtLeast(0L),
                    if (session.cancelled) "CANCELLED" else "FAILED",
                    e.message.orEmpty().take(160),
                )
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (session.cancelled) "WEB_UPLOAD_FILE_CANCELLED" else "WEB_UPLOAD_FILE_REJECTED",
                    "photoToken" to diagnosticToken(safeName.ifBlank { clientId }, "photo"),
                    "errorClass" to e.javaClass.simpleName,
                )
                throw e
            }
        }
    }

    suspend fun complete(ownerToken: String, id: String): JsonObject = indexMutex.withLock {
        val session = ownedSession(ownerToken, id)
        require(!session.cancelled) { "Upload session was cancelled" }
        if (session.completed) return@withLock status(ownerToken, id)
        val finished = session.files.values.count { it.state in setOf("COMPLETED", "DUPLICATE", "FAILED", "CANCELLED") }
        require(finished >= session.expectedFiles) { "Not all files in the batch have finished" }
        val filter = settings.settings.first().filters
        val refreshOperation = diagnostics.operations.start("SOURCE_REFRESH", DiagnosticOrigin.WEB_UI)
        val scanOperation = diagnostics.operations.start(
            "SCAN", DiagnosticOrigin.WEB_UI, refreshOperation.operationId,
        )
        val configRevision = diagnosticToken(filter.toString(), "config")
        val startedElapsedMs = SystemClock.elapsedRealtime()
        val refreshFields = mapOf(
            "sourceKind" to "LOCAL_UPLOADS",
            "trigger" to SourceRefreshTrigger.LOCAL_UPLOAD_CHANGED.name,
            "configRevision" to configRevision,
            "refreshToken" to diagnosticToken(id, "refresh"),
        )
        diagnostics.logEvent(
            "SOURCE_REFRESH_REQUESTED",
            refreshFields + mapOf("stage" to "REQUESTED", "durationMs" to "0"),
            refreshOperation.context(),
        )
        diagnostics.logEvent(
            "SOURCE_APPLY_QUEUED",
            refreshFields + mapOf("stage" to "QUEUED", "durationMs" to "0"),
            refreshOperation.context(),
        )
        try {
            diagnostics.operations.update(refreshOperation.operationId, "UPLOAD_INDEX")
            diagnostics.logEvent(
                "SOURCE_APPLY_STARTED",
                refreshFields + mapOf("stage" to "UPLOAD_INDEX", "durationMs" to "0"),
                refreshOperation.context(),
            )
            indexer.index(
                source,
                ScanOptions(
                    includeSubfolders = filter.includeSubfolders,
                    includeGlobs = filter.cleanIncludes,
                    excludeGlobs = filter.cleanExcludes,
                    excludeFolders = filter.cleanExcludeFolders,
                ),
                ScanDiagnosticContext(
                    sourceKind = "LOCAL_UPLOADS",
                    trigger = SourceRefreshTrigger.LOCAL_UPLOAD_CHANGED,
                    configRevision = configRevision,
                    context = scanOperation.context(),
                ),
            ).collect { progress -> if (progress is IndexProgress.Completed) Unit }
            val duration = (SystemClock.elapsedRealtime() - startedElapsedMs).coerceAtLeast(0L).toString()
            diagnostics.logEvent(
                "SOURCE_POOL_CONFIGURED",
                refreshFields + mapOf(
                    "stage" to "UPLOAD_INDEXED",
                    "durationMs" to duration,
                    "outcome" to "INDEX_UPDATED",
                ),
                refreshOperation.context(),
            )
            diagnostics.logEvent(
                "SOURCE_REFRESH_COMPLETED",
                refreshFields + mapOf(
                    "stage" to "TERMINAL",
                    "outcome" to "COMPLETED",
                    "durationMs" to duration,
                ),
                refreshOperation.context(),
            )
        } catch (cancelled: CancellationException) {
            diagnostics.logEvent(
                "SOURCE_REFRESH_CANCELLED",
                refreshFields + mapOf(
                    "stage" to "TERMINAL",
                    "outcome" to "CANCELLED",
                    "cancellationReason" to "CALLER_CANCELLED",
                    "durationMs" to (SystemClock.elapsedRealtime() - startedElapsedMs)
                        .coerceAtLeast(0L).toString(),
                ),
                refreshOperation.context(),
            )
            throw cancelled
        } catch (error: Exception) {
            diagnostics.logEvent(
                "SOURCE_REFRESH_FAILED",
                refreshFields + mapOf(
                    "stage" to "TERMINAL",
                    "outcome" to "FAILED",
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "UPLOAD_INDEX_EXCEPTION",
                    "durationMs" to (SystemClock.elapsedRealtime() - startedElapsedMs)
                        .coerceAtLeast(0L).toString(),
                ),
                refreshOperation.context(),
            )
            throw error
        } finally {
            diagnostics.operations.finish(scanOperation.operationId)
            diagnostics.operations.finish(refreshOperation.operationId)
        }
        session.completed = true
        session.finishedAtMs = System.currentTimeMillis()
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "WEB_UPLOAD_INDEX_COMPLETED",
            "sessionToken" to diagnosticToken(id, "upload_session"),
        )
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "WEB_UPLOAD_SESSION_COMPLETED",
            "sessionToken" to diagnosticToken(id, "upload_session"),
        )
        status(ownerToken, id)
    }

    suspend fun cancel(ownerToken: String, id: String): JsonObject {
        val session = ownedSession(ownerToken, id)
        require(!session.completed) { "Upload session is already complete" }
        session.cancelled = true
        session.finishedAtMs = System.currentTimeMillis()
        deleteParts(session)
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "WEB_UPLOAD_SESSION_CANCELLED",
            "sessionToken" to diagnosticToken(id, "upload_session"),
        )
        return status(ownerToken, id)
    }

    suspend fun cleanupStale() = withContext(io) {
        synchronized(admissionLock) { purgeExpiredLocked() }
        source.ensureDirectory().resolve(".incoming").listFiles()?.forEach { file ->
            if (file.name.endsWith(".part") && System.currentTimeMillis() - file.lastModified() > SESSION_TTL_MS) file.delete()
        }
    }

    /**
     * Stop admitting work, cancel active transfers, await both transfer slots and the
     * indexer, then remove only staging/session state. Completed uploaded photos remain.
     */
    suspend fun quiesceForFactoryReset() {
        val now = System.currentTimeMillis()
        synchronized(admissionLock) {
            acceptingSessions = false
            sessions.values.forEach { session ->
                if (!session.completed) {
                    session.cancelled = true
                    session.finishedAtMs = now
                }
            }
        }

        repeat(MAX_CONCURRENT_UPLOADS) { uploadSlots.acquire() }
        try {
            indexMutex.withLock {
                val abandoned = synchronized(admissionLock) {
                    sessions.values.toList().also { sessions.clear() }
                }
                abandoned.forEach(::deleteParts)
                withContext(io) {
                    val root = source.ensureDirectory()
                    root.resolve(".incoming").listFiles()?.forEach { it.delete() }
                    root.listFiles()?.filter {
                        it.isFile && (it.name.startsWith(".commit-") || it.name.startsWith(".rollback-"))
                    }?.forEach { it.delete() }
                }
            }
        } finally {
            repeat(MAX_CONCURRENT_UPLOADS) { uploadSlots.release() }
        }
        diagnostics.log(DiagnosticsLog.Category.APP, "WEB_UPLOADS_QUIESCED_FOR_FACTORY_RESET")
    }

    /** Caller must hold [admissionLock]. */
    private fun purgeExpiredLocked() {
        val now = System.currentTimeMillis()
        sessions.entries.toList().forEach { entry ->
            val terminal = entry.value.cancelled || entry.value.completed
            val reference = entry.value.finishedAtMs.takeIf { it > 0L } ?: entry.value.createdAtMs
            val ttl = if (terminal) FINISHED_SESSION_TTL_MS else SESSION_TTL_MS
            if (now - reference > ttl && sessions.remove(entry.key, entry.value)) {
                entry.value.cancelled = true
                deleteParts(entry.value)
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    "WEB_UPLOAD_SESSION_EXPIRED",
                    "sessionToken" to diagnosticToken(entry.key, "upload_session"),
                )
            }
        }
    }

    /** Caller must hold [admissionLock]. */
    private fun trimFinishedSessionsLocked() {
        if (sessions.size < MAX_RETAINED_SESSIONS) return
        sessions.entries
            .filter { it.value.cancelled || it.value.completed }
            .sortedBy { it.value.finishedAtMs.takeIf { time -> time > 0L } ?: it.value.createdAtMs }
            .take((sessions.size - MAX_RETAINED_SESSIONS + 1).coerceAtLeast(0))
            .forEach { entry ->
                if (sessions.remove(entry.key, entry.value)) deleteParts(entry.value)
            }
    }

    private fun deleteParts(session: Session) {
        val parts = synchronized(session.parts) { session.parts.toList().also { session.parts.clear() } }
        parts.forEach { it.delete() }
    }

    private fun ownedSession(ownerToken: String, id: String): Session {
        val session = sessions[id] ?: throw IllegalArgumentException("Unknown or expired upload session")
        require(session.ownerKey == ownerKey(ownerToken)) { "Upload session belongs to another browser session" }
        return session
    }

    private fun ownerKey(token: String): String {
        require(token.isNotBlank()) { "Missing authenticated browser session" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .toHexString()
    }

    private fun chooseDestination(
        root: File,
        name: String,
        part: File,
        sha: String,
        policy: UploadDuplicatePolicy,
    ): File? {
        var target = File(root, name)
        if (target.exists()) {
            val same = target.length() == part.length() && sha256(target) == sha
            if (same && policy == UploadDuplicatePolicy.SKIP) {
                part.delete()
                return null
            }
            when (policy) {
                UploadDuplicatePolicy.SKIP -> {
                    part.delete()
                    return null
                }
                UploadDuplicatePolicy.KEEP_BOTH -> target = keepBothTarget(root, name)
                UploadDuplicatePolicy.REPLACE_LOCAL -> Unit
            }
        }
        val commit = File(root, ".commit-${UUID.randomUUID()}.part")
        require(part.renameTo(commit) || part.copyTo(commit, overwrite = true).let { part.delete(); true }) {
            "Could not stage uploaded file"
        }
        if (target.exists() && policy == UploadDuplicatePolicy.REPLACE_LOCAL) {
            val backup = File(root, ".rollback-${UUID.randomUUID()}.part")
            require(target.renameTo(backup)) { "Could not prepare replacement" }
            if (!commit.renameTo(target)) {
                backup.renameTo(target)
                commit.delete()
                throw IllegalStateException("Could not commit replacement")
            }
            backup.delete()
        } else {
            require(commit.renameTo(target)) { commit.delete(); "Could not commit uploaded file" }
        }
        return target
    }

    private fun keepBothTarget(root: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (n in 2..10_000) {
            val candidate = File(root, "$base ($n)$ext")
            if (!candidate.exists()) return candidate
        }
        throw IllegalStateException("Could not allocate a unique file name")
    }

    private fun sanitizeName(raw: String): String {
        val decoded = java.net.URLDecoder.decode(raw, "UTF-8")
        require(decoded.isNotBlank() && decoded.length <= 180) { "Invalid file name" }
        require(decoded == File(decoded).name && !decoded.contains("..")) { "Unsafe file name" }
        require(decoded.none { it.code < 32 || it == '/' || it == '\\' || it == '\u0000' }) { "Unsafe file name" }
        return decoded.trim()
    }

    private fun isSupportedName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "webp")

    private fun validateImage(file: File, name: String) {
        val header = ByteArray(12)
        val count = file.inputStream().use { it.read(header) }
        require(count >= 8) { "Image file is truncated" }
        val jpeg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
        val png = header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val webp = String(header.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(header.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP"
        require(jpeg || png || webp) { "File signature does not match a supported image" }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "Image header cannot be decoded" }
        require(opts.outWidth <= MAX_DIMENSION && opts.outHeight <= MAX_DIMENSION) { "Image dimensions are too large" }
        require(opts.outWidth.toLong() * opts.outHeight.toLong() <= MAX_PIXELS) { "Image pixel count is too large" }
        val ext = name.substringAfterLast('.', "").lowercase()
        require((jpeg && ext in setOf("jpg", "jpeg")) || (png && ext == "png") || (webp && ext == "webp")) {
            "File extension does not match image content"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    companion object {
        private const val STREAM_BUFFER_BYTES = 64 * 1024
        private const val MAX_ACTIVE_SESSIONS = 4
        private const val MAX_SESSIONS_PER_OWNER = 2
        private const val MAX_RETAINED_SESSIONS = 32
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val SESSION_TTL_MS = 60L * 60_000L
        private const val FINISHED_SESSION_TTL_MS = 10L * 60_000L
        private const val STORAGE_RESERVE_BYTES = 250L * 1024L * 1024L
        private const val MAX_BATCH_BYTES = 20L * 1024L * 1024L * 1024L
        private const val MAX_DIMENSION = 50_000
        private const val MAX_PIXELS = 200_000_000L
    }
}
