#!/usr/bin/env python3
"""Source contracts for the v52.12 remediation of the v52.11 stability audit."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
coordinator = read("app/src/main/java/com/example/familyphotoframe/domain/engine/SourceRecoveryCoordinator.kt")
coordinator_test = read("app/src/test/java/com/example/familyphotoframe/domain/engine/SourceRecoveryCoordinatorTest.kt")
reset = read("app/src/main/java/com/example/familyphotoframe/maintenance/FactoryResetCoordinator.kt")
server = read("app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt")
controller = read("app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt")
runner = read("app/src/main/java/com/example/familyphotoframe/web/BoundedHttpAsyncRunner.kt")
runner_test = read("app/src/test/java/com/example/familyphotoframe/web/BoundedHttpAsyncRunnerTest.kt")
uploads = read("app/src/main/java/com/example/familyphotoframe/web/WebUploadManager.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
browser = read("scripts/verify-web-ui-v529.cjs")
ui = read("app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt")
readme = read("README.md")
limitations = read("KNOWN_LIMITATIONS.md")

checks = {
    "v52.12 remediation retained in current metadata":
        "versionCode = 33" in build and "0.12.13-prerelease" in build and
        'REVISION: String = "v52150"' in assets,
    "full diagnostics UUID":
        "UUID.randomUUID().toString()" in app and ".take(8)" not in app,
    "health checks reconcile actual playback pool":
        "actualPrimaryActive" in coordinator and "failureGeneration" in coordinator and
        "sourceId in primaryPoolIds && sourceId !in unavailablePoolIds" in vm,
    "playback failures invalidate and wake recovery":
        "markPlaybackUnavailable()" in vm and "runtime.wake.trySend(Unit)" in vm and
        "SOURCE_HEALTH_RESULT_SUPERSEDED" in vm,
    "post-index promotion is revalidated":
        "promotionStillValid(check)" in vm and "SOURCE_RECOVERY_PROMOTION_ABORTED" in vm and
        "RECOVERY_INDEX_FAILED" in vm and "nextWaitMs = RecoveryPolicy.waitMs" in vm,
    "source recovery race has executable tests":
        "playbackFailureIsPromotedExactlyOnceByNextSuccessfulCheck" in coordinator_test and
        "newerPlaybackFailureInvalidatesInFlightHealthResult" in coordinator_test and
        "failedPromotionCanBeRolledBackAndRetried" in coordinator_test,
    "factory reset is application-owned and awaited":
        "CoroutineScope(SupervisorJob() + io)" in reset and "withContext(NonCancellable)" in reset and
        "services.factoryResetCoordinator.reset()" in vm,
    "factory reset quiesces and clears full Room state":
        "quiesceForFactoryReset()" in reset and "database.clearAllTables()" in reset and
        "settings.update { AppSettings() }" in reset and "quiesceForFactoryReset()" in vm,
    "reset UI states upload preservation":
        "uploaded photo files are preserved" in ui.lower(),
    "NanoHTTPD workers and queue are bounded":
        "ArrayBlockingQueue" in runner and "ThreadPoolExecutor.AbortPolicy" in runner and
        "setAsyncRunner(BoundedHttpAsyncRunner" in server and "503 Service Unavailable" in server,
    "bounded runner saturation has a JVM test":
        "saturatedRunnerRejectsWithoutExceedingWorkerAndQueueBounds" in runner_test,
    "new stability events are typed in diagnostics":
        all(code in catalog for code in [
            "WEB_CONNECTION_REJECTED", "WEB_UPLOADS_QUIESCED_FOR_FACTORY_RESET",
            "WEB_FACTORY_RESET_STARTED", "WEB_FACTORY_RESET_COMPLETED",
            "WEB_FACTORY_RESET_FAILED", "SOURCE_HEALTH_RESULT_SUPERSEDED",
            "SOURCE_RECOVERY_PROMOTION_ABORTED",
        ]),
    "upload session admission is atomic":
        "synchronized(admissionLock)" in uploads and
        uploads.index("synchronized(admissionLock)") < uploads.index("sessions[id] = session") and
        uploads.index("sessions.values.count") < uploads.index("sessions[id] = session"),
    "source fallback preserves cancellation":
        "catch (cancelled: CancellationException)" in vm and
        vm.count("cancellableOrDefault") >= 3,
    "web lifecycle is serialized and versioned":
        "lifecycleLock" in controller and "lifecycleGeneration" in controller and
        "applyDesiredStateLocked()" in controller,
    "browser gate tests hidden and visible controls":
        "node.getClientRects().length > 0" in browser and
        "remembered:width===1440" in browser and "forgetVisible" in browser,
    "current documentation is not stale":
        "current v0.12.13" in readme and "current v0.12.13" in limitations and
        "The recovery loop has no automated test" not in limitations,
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
if failed:
    print(f"v52.12 stability remediation failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)
print(f"v52.12 stability remediation: PASS ({len(checks)} checks)")
