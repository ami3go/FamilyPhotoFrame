package com.example.familyphotoframe

import android.content.Intent
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.familyphotoframe.ui.settings.SettingsPage
import com.example.familyphotoframe.ui.settings.SettingsScreen
import com.example.familyphotoframe.ui.slideshow.SlideshowScreen
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel
import com.example.familyphotoframe.ui.theme.FamilyPhotoFrameTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single Activity hosting the slideshow and settings (spec §10, §12).
 *
 *  - Keeps the screen on while visible via FLAG_KEEP_SCREEN_ON (spec §4 Phase 0:
 *    "keep screen on while the Activity is visible" — no WAKE_LOCK permission).
 *  - Runs edge-to-edge / immersive so photos fill the panel (spec §10.1).
 *  - Owns the SAF ACTION_OPEN_DOCUMENT_TREE picker and persists the grant
 *    (takePersistableUriPermission) so the folder survives reboot (spec §7.1).
 *  - Delegates the D-pad map to the ViewModel (spec §12.1). On the settings screen
 *    the system handles focus traversal; we only intercept Back to return.
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    private enum class Screen { SLIDESHOW, SETTINGS }

    private val services: ServiceLocator get() = (application as App).services

    private val vm: SlideshowViewModel by viewModels {
        SlideshowViewModel.Factory(services, applicationContext)
    }

    private var screen by mutableStateOf(Screen.SLIDESHOW)
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val lightSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private var settingsPage by mutableStateOf(SettingsPage.ROOT)
    private var latestUiState: SlideshowUiState? = null
    private val activityDiagnostics by lazy { ActivityDiagnosticsController(this, services) }

    private val immersiveMode by lazy {
        ImmersiveModeController(
            activity = this,
            diagnostics = services.diagnostics,
            activityToken = activityDiagnostics.activityToken,
            activityState = { activityDiagnostics.activityStateCode },
        )
    }

    private val openTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            vm.onFolderPicked(null, null)
            immersiveMode.recover("FOLDER_PICKER_RETURN")
            return@registerForActivityResult
        }
        // Persist the read grant so it survives process death / reboot.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some providers don't allow persistence; the source health check will
            // surface a recovery path if the grant is later unavailable.
        }
        val name = runCatching { DocumentFile.fromTreeUri(this, uri)?.name }.getOrNull()
        vm.onFolderPicked(uri, name)
        immersiveMode.recover("FOLDER_PICKER_RETURN")
    }

    /** Which export the user started, so the create-document callback knows what to write. */
    private var pendingExport: SlideshowViewModel.FileOp = SlideshowViewModel.FileOp.EXPORT_CONFIG

    private val createConfigDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        vm.onExportTargetChosen(uri, pendingExport)
        immersiveMode.recover("DOCUMENT_EXPORT_RETURN")
    }

    private val createSupportDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri ->
        vm.onExportTargetChosen(uri, pendingExport)
        immersiveMode.recover("DOCUMENT_EXPORT_RETURN")
    }

    private val createBundleDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        vm.onExportTargetChosen(uri, pendingExport)
        immersiveMode.recover("DOCUMENT_EXPORT_RETURN")
    }

    /** Which import the user started, so the open-document callback knows how to read it. */
    private var pendingImport: SlideshowViewModel.FileOp = SlideshowViewModel.FileOp.IMPORT_CONFIG

    private val openConfigDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        vm.onImportSourceChosen(uri, pendingImport)
        immersiveMode.recover("DOCUMENT_IMPORT_RETURN")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            vm.onAmbientLight(event.values.firstOrNull(), true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityDiagnostics.onCreated(resources.configuration)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersiveMode.install()
        immersiveMode.recover("ACTIVITY_CREATED")

        // When the UI asks to pick a folder, launch the system picker.
        lifecycleScope.launch {
            vm.pickFolderRequests.collectLatest { openTree.launch(null) }
        }

        // Dim the window during quiet hours (spec §20). This is a per-window brightness
        // override, not a system setting, so it needs no permission and is released
        // automatically when the Activity goes away.
        lifecycleScope.launch {
            vm.state.collect { ui ->
                latestUiState = ui
                val lp = window.attributes
                val wanted = ui.screenBrightness.coerceIn(0.01f, 1f)
                if (lp.screenBrightness != wanted) {
                    lp.screenBrightness = wanted
                    window.attributes = lp
                }
                activityDiagnostics.recordUiState(ui, screen == Screen.SETTINGS, "VIEW_MODEL_STATE")
            }
        }

        // Config backup/restore and support-bundle export, all through SAF documents
        // so no storage permission is ever required (spec §7.0).
        lifecycleScope.launch {
            vm.fileRequests.collectLatest { req ->
                pendingExport = req.op
                when (req.op) {
                    SlideshowViewModel.FileOp.EXPORT_CONFIG -> createConfigDoc.launch(req.suggestedName)
                    SlideshowViewModel.FileOp.EXPORT_SUPPORT -> createSupportDoc.launch(req.suggestedName)
                    SlideshowViewModel.FileOp.EXPORT_ENCRYPTED -> createBundleDoc.launch(req.suggestedName)
                    SlideshowViewModel.FileOp.IMPORT_CONFIG,
                    SlideshowViewModel.FileOp.IMPORT_ENCRYPTED -> {
                        pendingImport = req.op
                        openConfigDoc.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }
            }
        }

        setContent {
            FamilyPhotoFrameTheme {
                BackHandler(enabled = screen == Screen.SETTINGS) {
                    navigateBackFromSettings()
                }
                // SlideshowScreen stays composed underneath Settings instead of being torn
                // down and rebuilt on every trip. Disposing it used to clear the prepared
                // slide registry (already-decoded current/next bitmaps), forcing a full
                // re-decode — or, for remote sources, a full network re-fetch — on return,
                // which showed up as a black screen for a few seconds. Settings is fully
                // opaque and full-bleed on every page, so nothing bleeds through and it
                // captures all touch while shown; D-pad routing is unaffected since that's
                // driven by the `screen` field in onKeyDown, not by composition presence.
                Box(Modifier.fillMaxSize()) {
                    SlideshowScreen(
                        vm = vm,
                        imageLoader = services.imageLoader,
                        onOpenSettings = ::openSettings,
                        onOpenPhotoSources = ::openPhotoSources,
                        localThumbnailCache = services.localThumbnailCache,
                    )
                    if (screen == Screen.SETTINGS) {
                        SettingsScreen(
                            vm = vm,
                            diagnostics = services.diagnostics,
                            page = settingsPage,
                            onOpenPage = {
                                settingsPage = it
                                immersiveMode.recover("SETTINGS_PAGE_CHANGED")
                            },
                            onBack = {
                                settingsPage = if (settingsPage == SettingsPage.FOLDERS) {
                                    SettingsPage.PHOTOS
                                } else {
                                    SettingsPage.ROOT
                                }
                                immersiveMode.recover("SETTINGS_PAGE_BACK")
                            },
                            onBackToSlideshow = ::returnToSlideshow,
                        )
                    }
                }
            }
        }
    }

    private fun openSettings() {
        settingsPage = SettingsPage.ROOT
        screen = Screen.SETTINGS
        latestUiState?.let { activityDiagnostics.recordUiState(it, true, "OPEN_SETTINGS") }
        immersiveMode.recover("OPEN_SETTINGS")
    }

    /** Open source setup directly from first-run and recovery surfaces. */
    private fun openPhotoSources() {
        settingsPage = SettingsPage.PHOTOS
        screen = Screen.SETTINGS
        latestUiState?.let { activityDiagnostics.recordUiState(it, true, "OPEN_PHOTO_SOURCES") }
        immersiveMode.recover("OPEN_PHOTO_SOURCES")
    }

    private fun returnToSlideshow() {
        settingsPage = SettingsPage.ROOT
        screen = Screen.SLIDESHOW
        latestUiState?.let { activityDiagnostics.recordUiState(it, false, "RETURN_TO_SLIDESHOW") }
        immersiveMode.recover("RETURN_TO_SLIDESHOW")
    }

    private fun navigateBackFromSettings() {
        if (settingsPage == SettingsPage.ROOT) {
            returnToSlideshow()
        } else {
            settingsPage = if (settingsPage == SettingsPage.FOLDERS) {
                SettingsPage.PHOTOS
            } else {
                SettingsPage.ROOT
            }
            immersiveMode.recover("SETTINGS_PAGE_BACK")
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onHostStarted()
        activityDiagnostics.onStarted()
    }

    override fun onResume() {
        super.onResume()
        activityDiagnostics.onResumed()
        val sensor = lightSensor
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            vm.onAmbientLight(null, true)
        } else {
            vm.onAmbientLight(null, false)
        }
        immersiveMode.recover("ACTIVITY_RESUMED")
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        activityDiagnostics.onPaused()
        super.onPause()
    }

    override fun onStop() {
        vm.onHostStopped()
        activityDiagnostics.onStopped()
        super.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        immersiveMode.recover("POST_RESUME")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        immersiveMode.onWindowFocusChanged(hasFocus)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activityDiagnostics.onConfigurationChanged(newConfig)
        immersiveMode.recover("CONFIGURATION_CHANGED")
    }

    override fun onDestroy() {
        activityDiagnostics.onDestroyed()
        immersiveMode.release()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // On settings, let the framework drive focus traversal. Back first closes a
        // submenu, then returns from the group list to the slideshow.
        if (screen == Screen.SETTINGS) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                navigateBackFromSettings()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> { vm.onNext(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { vm.onPrevious(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.onTogglePause("key"); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_INFO -> { vm.onShowInfo(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.onHideInfo(); true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                openSettings()
                true
            }
            // Curation (spec §9.4). The D-pad cross and OK are all taken by navigation,
            // so these ride on keys a TV remote and a keyboard both have. Hide is
            // deliberately not on a bare-remote key: it is reversible but disruptive,
            // and an accidental press should be hard rather than one D-pad slip away.
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_BOOKMARK -> { vm.toggleFavorite(); true }
            KeyEvent.KEYCODE_H -> { vm.hideCurrentPhoto(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        // Long-press OK opens settings (spec §12.1 menu/longSelect=settings).
        if (screen == Screen.SLIDESHOW &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            openSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

}
