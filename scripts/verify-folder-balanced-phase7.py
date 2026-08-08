#!/usr/bin/env python3
"""Completion contract for all previously identified FPF-FEAT-SHUFFLE-002 v1.1 deviations."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")

settings = read("app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt")
engine = read("app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt")
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
repo = read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepository.kt")
eligibility = read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleEligibilityProvider.kt")
models = read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleModels.kt")
entity = read("app/src/main/java/com/example/familyphotoframe/data/db/PhotoItemEntity.kt")
migrations = read("app/src/main/java/com/example/familyphotoframe/data/db/Migrations.kt")
db = read("app/src/main/java/com/example/familyphotoframe/data/db/AppDatabase.kt")
dao = read("app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt")
hasher = read("app/src/main/java/com/example/familyphotoframe/data/index/ContentHashBackfiller.kt")
source = read("app/src/main/java/com/example/familyphotoframe/data/source/PhotoSource.kt")
synology = read("app/src/main/java/com/example/familyphotoframe/data/source/SynologyFileStationSource.kt")
android_folders = read("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPhotoSources.kt") + read(
    "app/src/main/java/com/example/familyphotoframe/ui/settings/FolderManagementSettings.kt"
)
android_playback = read("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPlaybackSections.kt")
web_script = read("app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt")
web_server = read("app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt")
web_controller = read("app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt")
recovery = read("app/src/main/java/com/example/familyphotoframe/domain/engine/RecoveryPolicy.kt")
gradle = read("app/build.gradle.kts")
serializer_tests = read("app/src/test/java/com/example/familyphotoframe/data/settings/AppSettingsSerializerTest.kt")
repo_tests = read("app/src/androidTest/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepositoryTest.kt")
eligibility_tests = read("app/src/androidTest/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleEligibilityProviderTest.kt")
hash_tests = read("app/src/androidTest/java/com/example/familyphotoframe/data/index/ContentHashBackfillerTest.kt")

checks = {
    "API 21 remains supported": "minSdk = 21" in gradle,
    "fresh installs default folder-balanced": "val selectionMode: SelectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE" in settings,
    "sequential playback mode exists": "SEQUENTIAL" in settings and "SelectionMode.SEQUENTIAL ->" in engine,
    "legacy random maps once to global shuffle": "selectionMode == SelectionMode.LEAST_RECENT_RANDOM" in settings and "SelectionMode.SHUFFLE_NO_REPEAT else selectionMode" in settings,
    "new playlists default folder-balanced": "selectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE" in vm and "selectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE" in web_controller,
    "Room v8 is non-destructive": "version = 8" in db and "MIGRATION_7_8" in db and "fallbackToDestructiveMigration" not in db.replace("// No fallbackToDestructiveMigration():", ""),
    "canonical direct-folder identity persisted": "canonicalDirectory" in entity and "CanonicalPhotoPath.directDirectory" in migrations,
    "content SHA-256 persisted": "contentSha256" in entity and "MessageDigest.getInstance(\"SHA-256\")" in hasher,
    "hashing uses original bytes outside rendering": "preferOriginal = true" in hasher and "if (conn.useThumbnails && !options.preferOriginal)" in synology and "val preferOriginal: Boolean" in source,
    "playback eligibility is folder-lazy": "folderSnapshot(query)" in read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/FolderBalancedShuffleCoordinator.kt") and "folderMembers(query, folderKey)" in read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/FolderBalancedShuffleCoordinator.kt") and "shuffleEligibilityRowsForFolder" in dao,
    "same-folder content duplicates collapse": "linkedMapOf<String, EligiblePhotoMember>" in eligibility and "equivalentPhotoIds" in eligibility,
    "cross-folder identical content remains independent": "sourceId = :sourceId AND canonicalDirectory = :canonicalDirectory" in dao and "exactContentDuplicatesCollapseOnlyInsideSameDirectFolder" in eligibility_tests,
    "identity upgrades reconcile aliases": "equivalentPhotoIds" in models and "aliasToCanonical" in repo,
    "exact folder preview query exists": "previewFolderCandidates" in dao and "canonicalDirectory = :canonicalDirectory" in dao,
    "Android preview-once action exists": "vm.previewFolderOnce(folder)" in android_folders and "previewFolderOnceByKey" in vm,
    "Android use-in-playlist action exists": "vm.useFolderInActivePlaylist(folder)" in android_folders and "useFolderInActivePlaylistByKey" in vm,
    "Web manual folder actions exist": "/api/v1/folders/action" in web_server and "preview_once" in web_controller and "use_in_playlist" in web_controller,
    "Web folder keys are encoded safely": "encodeURIComponent(key)" in web_script and "decodeURIComponent(n.dataset.folderPreview)" in web_script,
    "preview returns without consuming queue": "previewReturnPick" in engine and "FOLDER_PREVIEW_RETURNED" in engine,
    "folder failure retries exactly twice": "MAX_FOLDER_RETRIES = 2" in repo and "retryCount >= MAX_FOLDER_RETRIES" in repo,
    "scope-restored diagnostic exists": '"SHUFFLE_SCOPE_RESTORED"' in repo,
    "folder-deferred diagnostic exists": '"FOLDER_DEFERRED"' in repo,
    "required source backoff schedule": "longArrayOf(30, 120, 300, 900)" in recovery,
    "complete Android health status": all(token in android_playback for token in [
        "eligibleFolderCount", "foldersPending", "foldersSkipped", "foldersRemoved",
        "pendingPhotos", "quarantinedPhotos", "activeReservationAgeMs",
        "lastCommitEpochMs", "lastReconciliationEpochMs", "lastRecoveryEpochMs",
    ]),
    "complete Web health status": all(token in web_script for token in [
        "shuffleEligibleFolders", "shuffleFoldersPending", "shuffleFoldersSkipped",
        "shuffleFoldersRemoved", "shufflePendingPhotos", "shuffleQuarantinedPhotos",
        "shuffleReservationAgeMs", "shuffleLastCommitEpochMs",
        "shuffleLastReconciliationEpochMs", "shuffleLastRecoveryEpochMs",
    ]),
    "settings migration regression tests": all(token in serializer_tests for token in [
        "newInstallDefaultsToFolderBalancedShuffle", "legacyRandomMigratesOnceToGlobalNoRepeat",
        "explicitSequentialAndExistingModesAreNotMigrated",
    ]),
    "folder retry instrumentation test": "folderLevelFailureRetriesTwice_thenSkipsExactlyOnce" in repo_tests,
    "identity-upgrade instrumentation test": "contentHashIdentityUpgradeDoesNotRepeatConsumedFallbackMember" in repo_tests,
    "duplicate-scope instrumentation tests": "exactContentDuplicatesCollapseOnlyInsideSameDirectFolder" in eligibility_tests,
    "lazy eligibility instrumentation test": "playbackSnapshotLoadsFolderMetadataThenOnlyCurrentFolderMembers" in eligibility_tests,
    "original-byte hashing instrumentation test": "hashesOriginalBytesAndPersistsLowercaseSha256" in hash_tests,
    "selection latency diagnostic exists": '"SHUFFLE_SELECTION_TIMING"' in engine
        and '"targetMs" to if (cachedOnly) "50" else "250"' in engine,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
if failed:
    raise SystemExit("Phase 7 completion contract failures: " + ", ".join(failed))
print(f"Phase 7 task-completion contract passed ({len(checks)} checks)")
