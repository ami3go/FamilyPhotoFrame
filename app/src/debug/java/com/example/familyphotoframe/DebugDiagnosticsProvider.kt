package com.example.familyphotoframe

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.familyphotoframe.data.diagnostics.DiagnosticsBundleContext

/**
 * ADB-readable diagnostics export for debug validation builds.
 *
 * The provider exists only in the debug source set and requires Android's privileged DUMP
 * permission, which the shell UID owns. It does not weaken the paired web API or ship in a
 * release APK. Streaming the application's real durable bundle gives the hardware collector
 * authoritative writer health plus the terminal bundleEnd marker without exposing credentials.
 */
class DebugDiagnosticsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/x-ndjson"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "read only" }
        require(uri.pathSegments == listOf("bundle")) { "unknown diagnostics path" }
        val app = requireNotNull(context).applicationContext as App
        val pipe = ParcelFileDescriptor.createPipe()
        Thread(
            {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    app.services.diagnostics.openDurableBundle(
                        DiagnosticsBundleContext(
                            appVersion = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE.toLong(),
                            buildType = BuildConfig.BUILD_TYPE,
                            sdkInt = Build.VERSION.SDK_INT,
                            deviceModel = Build.MODEL,
                            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                            runtime = app.services.diagnosticRuntimeState.snapshot(),
                        ),
                    ).use { input -> input.copyTo(output) }
                }
            },
            "debug-diagnostics-export",
        ).apply {
            isDaemon = true
            start()
        }
        return pipe[0]
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
