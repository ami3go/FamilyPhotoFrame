package com.example.familyphotoframe.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.familyphotoframe.util.Glob
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Local folder source backed by a persisted SAF tree URI (spec §7.1).
 *
 * Traversal uses [DocumentsContract] child-document queries with an explicit work
 * stack rather than DocumentFile.listFiles(), because DocumentFile allocates an
 * object per node and is too slow on large trees (spec §7.11 performance rules).
 * No raw filesystem paths are used. All work runs on [io]; nothing touches the
 * main thread (Contract Rule 2).
 */
class SafPhotoSource(
    override val id: SourceId,
    private val treeUri: Uri,
    private val context: Context,
    private val io: CoroutineDispatcher,
) : PhotoSource {

    override val type: SourceType = SourceType.LOCAL_SAF_FOLDER

    private val resolver get() = context.contentResolver

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth {
        // 1. Is the persisted read grant still held? (lost after reboot/restore/revoke)
        val stillGranted = resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!stillGranted) return SourceHealth.NeedsPermission

        // 2. Is the tree still queryable? (moved/renamed/deleted/unmounted)
        return try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { SourceHealth.Ok }
                ?: SourceHealth.ProviderError("null cursor on root")
        } catch (se: SecurityException) {
            SourceHealth.NeedsPermission
        } catch (e: IllegalArgumentException) {
            // Provider rejected the tree (e.g. blocked location such as a storage root).
            SourceHealth.Missing
        } catch (e: Exception) {
            SourceHealth.ProviderError(e.javaClass.simpleName)
        }
    }

    private data class Dir(val docId: String, val relPath: String, val folderName: String)

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = queryDisplayName(rootDocId) ?: "Folder"
        val stack = ArrayDeque<Dir>()
        stack.addLast(Dir(rootDocId, "", rootName))
        var scanned = 0L

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive() // cooperative cancellation (spec §5.2)
            val dir = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dir.docId)

            val cursor = try {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null, null, null,
                )
            } catch (se: SecurityException) {
                emit(ScanEvent.Error(dir.relPath, SourceError.PermissionRevoked)); return@flow
            } catch (e: Exception) {
                null
            }
            if (cursor == null) {
                emit(ScanEvent.Error(dir.relPath, SourceError.ProviderError)); continue
            }

            cursor.use { c ->
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    coroutineContext.ensureActive()
                    val childId = if (idCol >= 0) c.getString(idCol) else null ?: continue
                    // Providers may return null name/size/modified (spec §7.11).
                    val name = (if (nameCol >= 0) c.getString(nameCol) else null) ?: childId.substringAfterLast('/')
                    val mime = if (mimeCol >= 0) c.getString(mimeCol) else null
                    val size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L

                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isDir) {
                        // Hidden dirs and @eaDir are handled by the shared filter policy
                        // (default excludeGlobs ".*" and excludeFolders "@eaDir").
                        if (options.allowsFolder(name)) {
                            val childRel = if (dir.relPath.isEmpty()) name else "${dir.relPath}/$name"
                            stack.addLast(Dir(childId, childRel, name))
                        }
                        continue
                    }

                    if (!options.allowsFile(name)) continue
                    if (!SupportedFormats.isSupported(name, mime)) continue

                    val relPath = if (dir.relPath.isEmpty()) name else "${dir.relPath}/$name"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val item = PhotoItem(
                        stableId = StableId.of(id.value, relPath, size, modified),
                        sourceId = id,
                        normalizedPath = relPath,
                        folderName = dir.folderName,
                        fileName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        fileModifiedEpochMs = modified,
                        openToken = docUri.toString(),
                    )
                    emit(ScanEvent.FileFound(item))
                    scanned++
                    if (scanned % 200 == 0L) emit(ScanEvent.DirectoryProgress(dir.relPath, scanned))
                }
            }
        }
        emit(ScanEvent.Finished(ScanCursor(token = System.currentTimeMillis().toString())))
    }.flowOn(io)

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream {
        val uri = Uri.parse(item.openToken)
        return resolver.openInputStream(uri)
            ?: throw FileNotFoundException("Provider returned no stream for ${item.fileName}")
    }

    private fun queryDisplayName(docId: String): String? = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }
}
