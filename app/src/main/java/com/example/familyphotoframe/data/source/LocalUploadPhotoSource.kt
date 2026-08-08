package com.example.familyphotoframe.data.source

import android.content.Context
import android.os.Environment
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** App-managed local photo library populated by the paired web upload feature. */
class LocalUploadPhotoSource(
    override val id: SourceId,
    private val context: Context,
    private val io: CoroutineDispatcher,
) : PhotoSource {
    override val type: SourceType = SourceType.APP_PRIVATE_UPLOADS

    val directory: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.let { File(it, "FamilyPhotoFrame") }
            ?: File(context.filesDir, "local-photo-library")

    suspend fun ensureDirectory(): File = withContext(io) {
        directory.apply { if (!exists() && !mkdirs()) error("Cannot create upload library") }
    }

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        runCatching { ensureDirectory(); SourceHealth.Ok }.getOrElse {
            SourceHealth.ProviderError(it.javaClass.simpleName)
        }
    }

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val root = ensureDirectory()
        var count = 0L
        root.walkTopDown().maxDepth(8).filter { it.isFile }.sortedBy { it.absolutePath }.forEach { file ->
            coroutineContext.ensureActive()
            val rel = file.relativeTo(root).invariantSeparatorsPath
            if (!options.allowsFile(file.name) || !SupportedFormats.isSupportedExtension(file.name)) return@forEach
            emit(
                ScanEvent.FileFound(
                    PhotoItem(
                        stableId = StableId.of(id.value, rel, file.length(), file.lastModified()),
                        sourceId = id,
                        normalizedPath = rel,
                        folderName = file.parentFile?.relativeTo(root)?.invariantSeparatorsPath
                            ?.takeIf { it.isNotBlank() && it != "." } ?: "Local uploads",
                        fileName = file.name,
                        mimeType = mimeFor(file.name),
                        sizeBytes = file.length(),
                        fileModifiedEpochMs = file.lastModified(),
                        openToken = file.absolutePath,
                    )
                )
            )
            count++
        }
        emit(ScanEvent.Finished(ScanCursor("local-uploads-$count")))
    }.flowOn(io)

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream =
        File(item.openToken).inputStream()

    private fun mimeFor(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> null
    }
}
