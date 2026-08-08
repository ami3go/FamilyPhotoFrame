package com.example.familyphotoframe.data.source

import android.content.Context
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * App-private built-in source (spec §7.0 `app_private_builtin`). Required fallback
 * capability for devices with a missing/broken SAF picker, and the "Use sample
 * photos" path. Bundled JPEGs ship in assets/fallback and are copied once into
 * app-private storage; no storage/media permission is involved.
 */
class AppPrivateFallbackSource(
    override val id: SourceId,
    private val context: Context,
    private val io: CoroutineDispatcher,
) : PhotoSource {

    override val type: SourceType = SourceType.APP_PRIVATE_BUILTIN

    private val dir: File get() = File(context.filesDir, "fallback")

    /** Copy bundled assets into app-private storage if not present. Idempotent. */
    suspend fun ensureMaterialized() = withContext(io) {
        if (!dir.exists()) dir.mkdirs()
        val assets = context.assets.list("fallback").orEmpty()
        for (asset in assets) {
            val target = File(dir, asset)
            if (target.exists() && target.length() > 0) continue
            context.assets.open("fallback/$asset").use { input ->
                val tmp = File(dir, "$asset.part")
                tmp.outputStream().use { input.copyTo(it) }
                tmp.renameTo(target) // atomic-ish publish
            }
        }
    }

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        ensureMaterialized()
        val any = dir.listFiles()?.any { it.isFile && SupportedFormats.isSupportedExtension(it.name) } == true
        if (any) SourceHealth.Ok else SourceHealth.Unavailable
    }

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        ensureMaterialized()
        val files = dir.listFiles()?.filter { it.isFile && SupportedFormats.isSupportedExtension(it.name) }
            ?.sortedBy { it.name }.orEmpty()
        var scanned = 0L
        for (f in files) {
            coroutineContext.ensureActive()
            val item = PhotoItem(
                stableId = StableId.of(id.value, f.name, f.length(), f.lastModified()),
                sourceId = id,
                normalizedPath = f.name,
                folderName = context.getString(
                    com.example.familyphotoframe.R.string.status_source_samples
                ),
                fileName = f.name,
                mimeType = "image/jpeg",
                sizeBytes = f.length(),
                fileModifiedEpochMs = f.lastModified(),
                openToken = f.absolutePath,
            )
            emit(ScanEvent.FileFound(item))
            scanned++
        }
        emit(ScanEvent.Finished(ScanCursor(token = "fallback-$scanned")))
    }.flowOn(io)

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream =
        File(item.openToken).inputStream()
}
