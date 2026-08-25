#!/usr/bin/env python3
"""Source-level contracts for the crash-free runtime Phase 4 adaptive controller."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


policy = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryPolicy.kt")
guard = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryGuard.kt")
engine = read("app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
locator = read("app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt")
recovery = read("app/src/main/java/com/example/familyphotoframe/domain/engine/MemorySelfRecoveryPolicy.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
checks_file = read("scripts/verify/PlaybackMemoryPolicyChecks.kt")
build = read("app/build.gradle.kts")
notes = read("docs/CRASH_FREE_RUNTIME_PHASE4_ADAPTIVE_STABILITY.md")

render_recorder = policy[
    policy.index("fun renderAckTimeout(") : policy.index("fun systemPressure(")
]

checks = {
    "operational sources join the established memory policy":
        all(source in policy for source in (
            "NATIVE_PSS_GROWTH", "RENDER_ACK_TIMEOUTS", "OVERDUE_TRANSFER",
        )),
    "render timeout window is bounded and wired from the engine":
        "RENDER_TIMEOUT_WINDOW_MS = 5L * 60_000L" in policy and
        "MAX_WINDOW_EVENT_COUNT" in render_recorder and
        "renderTimeoutEvaluatedCount" in policy and
        "onRenderAckTimeout()" in engine and
        "playbackMemoryGuard.recordRenderAckTimeout(" in locator and
        "fun recordRenderAckTimeout(" in guard,
    "fresh transfer/native evidence is evaluated only by full samples":
        "val transferEvidenceRefreshed = activeMediaTransfers != null" in policy and
        "nativePssBytes?.let" in policy and
        "nativePssBytes = sampledMemory.nativePssKb" in app and
        "oldestMediaTransferAgeMs = sampledMemory.oldestMediaTransferAgeMs" in app,
    "native trend needs time, delta, rate, and repeated-window evidence":
        all(term in policy for term in (
            "NATIVE_GROWTH_MIN_WINDOW_MS", "NATIVE_GROWTH_MAX_WINDOW_MS",
            "NATIVE_GROWTH_MIN_BYTES", "NATIVE_GROWTH_MIN_RATE_KB_PER_MIN",
            "NATIVE_GROWTH_CRITICAL_STREAK",
        )),
    "operational pressure never writes the decode circuit deadline":
        "circuitOpenUntilElapsedMs" not in render_recorder and
        "fun decodeOom(" in policy and
        "circuitOpenUntilElapsedMs = nowElapsedMs + cooldown" in policy,
    "selected decode remains enabled outside a real OOM circuit":
        "level != PlaybackMemoryLevel.CIRCUIT_OPEN" in policy and
        "render stalls never block selected decode" in checks_file,
    "explicit GC is globally rate limited across recovery attempts":
        "GC_MIN_INTERVAL_MS = 10L * 60_000L" in recovery and
        "lastGcRequestedAtElapsedMs" in recovery and
        "gcRateLimited" in recovery,
    "Phase 4 evidence survives the diagnostics allowlist":
        all(field in catalog for field in (
            "nativeGrowthKb", "nativeGrowthRateKbPerMin", "nativeGrowthStreak",
            "renderTimeoutWindowCount", "renderTimeoutTotal",
        )),
    "offline checks cover every new signal family":
        all(term in checks_file for term in (
            "repeated render stalls enter guarded mode",
            "stale render window cannot refresh its hold",
            "overdue transfer enters guarded mode",
            "repeated native growth escalates to critical",
            "1,000-sample pressure run has bounded level changes",
        )),
    "Phase 4 has an install-distinct version and qualification notes":
        "val buildNumber = 42" in build and
        "val testingVariant: Int? = 1" in build and
        all(term in notes for term in (
            "26.42.1", "physical-device soak pending", "CIRCUIT_OPEN",
            "Render acknowledgement", "Native PSS",
        )),
}

for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")

failed = [name for name, passed in checks.items() if not passed]
if failed:
    print(f"Phase 4 verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)

print(f"Crash-free runtime Phase 4 contracts passed ({len(checks)} checks)")
