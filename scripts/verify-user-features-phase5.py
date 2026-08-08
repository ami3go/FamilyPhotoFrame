#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
manager = (root / 'app/src/main/java/com/example/familyphotoframe/web/WebUploadManager.kt').read_text()
server = (root / 'app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt').read_text()
controller = ''.join((root / 'app/src/main/java/com/example/familyphotoframe/web' / n).read_text() for n in ['WebServerController.kt', 'WebSettingsPatchApplier.kt'])
web_dir = root / 'app/src/main/java/com/example/familyphotoframe/web'
assets = ''.join((web_dir / n).read_text() for n in ['WebUiAssets.kt', 'WebUiCss.kt', 'WebUiScript.kt'])
settings_dir = root / 'app/src/main/java/com/example/familyphotoframe/ui/settings'
settings = ''.join(p.read_text() for p in sorted(settings_dir.glob('Settings*.kt')))
locator = (root / 'app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt').read_text()
source = (root / 'app/src/main/java/com/example/familyphotoframe/data/source/LocalUploadPhotoSource.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()

checks = {
    'API 21 remains minimum': 'minSdk = 21' in gradle,
    'version 0.12.13': 'versionCode = 33' in gradle and '0.12.13-prerelease' in gradle,
    'local upload source': 'class LocalUploadPhotoSource' in source and 'Local uploads' in source,
    'manager wired': 'WebUploadManager(localUploadSource' in locator and 'webUploadManager,' in locator,
    'one file streaming': 'InputStream' in manager and 'ByteArray(STREAM_BUFFER_BYTES)' in manager,
    'no multipart parser for file route': 'session.inputStream' in server and 'application/octet-stream' in server,
    'length before body': 'Content-Length is required' in server and 'length <= 0L' in server,
    'chunked rejected': 'TRANSFER_ENCODING_REJECTED' in server,
    'paired csrf writes': '-> guarded(token, csrf)' in server and '/api/v1/uploads' in server,
    'owner bound sessions': 'ownerKey' in manager and 'MAX_SESSIONS_PER_OWNER = 2' in manager,
    'bounded global sessions': 'MAX_ACTIVE_SESSIONS = 4' in manager,
    'two concurrent uploads': 'Semaphore(MAX_CONCURRENT_UPLOADS)' in manager and 'MAX_CONCURRENT_UPLOADS = 2' in manager,
    'storage reserve': 'STORAGE_RESERVE_BYTES' in manager and 'usableSpace' in manager,
    'safe temporary staging': '".incoming"' in manager and 'UUID.randomUUID()' in manager,
    'path traversal blocked': 'decoded == File(decoded).name' in manager and '!decoded.contains("..")' in manager,
    'hash duplicate detection': 'MessageDigest.getInstance("SHA-256")' in manager and 'UploadDuplicatePolicy.KEEP_BOTH' in manager,
    'magic bytes and dimensions': 'validateImage' in manager and 'BitmapFactory.Options' in manager and 'MAX_PIXELS' in manager,
    'atomic replacement rollback': '".rollback-' in manager and 'backup.renameTo(target)' in manager,
    'failed files finish batch': 'if (session.cancelled) "CANCELLED" else "FAILED"' in manager,
    'single indexing worker': 'indexMutex.withLock' in manager,
    'completed sessions reusable capacity': '!it.cancelled && !it.completed' in manager and 'FINISHED_SESSION_TTL_MS' in manager,
    'own parts cancellation': 'deleteParts(session)' in manager,
    'web upload UI': 'Upload to frame' in assets and 'startBulkUpload' in assets and 'XMLHttpRequest' in assets,
    'drag and drop upload': 'uploadDrop' in assets and "addEventListener('drop'" in assets and 'dataTransfer' in assets,
    'pause resume queue': all(x in assets for x in ['pauseBulkUpload', 'resumeBulkUpload', 'waitForUploadResume', "a==='pause-upload'", "a==='resume-upload'"]),
    'retry and clear queue': all(x in assets for x in ['retryFailedUploads', 'clearUploadQueue', "a==='retry-upload'", "a==='clear-upload'"]),
    'upload cancel enabled during active work': 'state.uploadActive' in assets and 'Cancelling upload' in assets,
    'upload maximum size setting': 'webUploadMaxFileBytes' in assets and 'next.webUpload.copy(maxFileBytes = value)' in controller,
    'upload playback policy': 'webUploadAllowWhilePlaying' in assets and 'uploadSettings.allowWhilePlaying || !playing' in controller,
    'two browser workers': 'Promise.all([worker(),worker()])' in assets,
    'trusted LAN warning': 'plain HTTP in this version' in assets,
    'android upload settings': 'WebUploadSettingsSection' in settings,
    'playlist management complete': all(x in controller for x in ['"rename"', '"duplicate"', '"toggle"', '"set_default"', '"move"']),
    'schedule date/day/priority': all(x in controller for x in ['daysOfWeek = days', 'startDateIso', 'endDateIso', 'priority =']),
    'health/brightness/playlist web UI': all(x in assets for x in ['Frame status', 'Automatic brightness and night mode', 'Slideshow playlists', 'Scheduled playlist switching']),
    'cache-busted assets': 'const val REVISION: String = "v52150"' in assets,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('FAIL:\n  - ' + '\n  - '.join(failed))
print(f'Phase 5 bulk-upload/hardening contract passed ({len(checks)} checks)')
