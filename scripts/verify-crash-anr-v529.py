#!/usr/bin/env python3
"""Focused Phase-3 contracts for crash, ANR, and process-exit evidence."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/com/example/familyphotoframe"
APP = (BASE / "App.kt").read_text(encoding="utf-8")
STORE = (BASE / "data/diagnostics/CrashEnvelopeStore.kt").read_text(encoding="utf-8")
DETECTOR = (BASE / "data/diagnostics/MainThreadStallDetector.kt").read_text(encoding="utf-8")
WATCHDOG = (BASE / "data/diagnostics/MainThreadStallWatchdog.kt").read_text(encoding="utf-8")
EXIT = (BASE / "data/diagnostics/ProcessExitInspector.kt").read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []

    def need(text: str, value: str, label: str) -> None:
        if value not in text:
            failures.append(f"missing {label}: {value}")

    need(STORE, "const val MAX_BYTES = 16 * 1024", "16 KiB crash cap")
    need(STORE, "CRC32", "torn-write checksum")
    need(STORE, "MAX_FRAMES = 8", "bounded stack")
    need(STORE, "MAX_CAUSES = 8", "bounded cause chain")
    need(STORE, "clearIfUnchanged", "newer-evidence-safe consume")
    need(APP, ".commit()", "synchronous private-preference write")
    need(APP, "finally {", "platform handler delegation guard")
    need(APP, "previous.uncaughtException(thread, throwable)", "default handler delegation")
    need(APP, "CrashEnvelopeFactory.crash", "bounded crash capture")
    need(APP, '"PREVIOUS_CRASH_EVIDENCE"', "recovered crash event")
    need(APP, '"PREVIOUS_ANR_EVIDENCE"', "recovered ANR event")
    need(APP, "flushSucceeded()", "durable consume gate")
    if ".message" in STORE or "throwable.message" in APP or "error.message" in APP:
        failures.append("exception message is referenced by crash-envelope code")

    need(DETECTOR, "HEARTBEAT_INTERVAL_MS = 2_000L", "two-second heartbeat")
    need(DETECTOR, "WARNING_THRESHOLD_MS = 5_000L", "five-second warning")
    need(DETECTOR, "ESCALATION_THRESHOLD_MS = 10_000L", "ten-second escalation")
    need(DETECTOR, "CAPTURE_COOLDOWN_MS = 60_000L", "60-second capture cooldown")
    for code in (
        "MAIN_THREAD_STALL_STARTED", "MAIN_THREAD_STALL_ESCALATED",
        "MAIN_THREAD_STALL_RECOVERED",
    ):
        need(WATCHDOG, f'"{code}"', f"watchdog event {code}")
    need(WATCHDOG, "Debug::isDebuggerConnected", "debugger suppression")
    need(WATCHDOG, 'Thread(::watch, "fpf-main-watchdog")', "off-main watchdog")

    need(EXIT, "if (Build.VERSION.SDK_INT < 30) return null", "API guard")
    need(EXIT, "@RequiresApi(30)", "API-30 isolation")
    need(EXIT, "getHistoricalProcessExitReasons", "platform exit evidence")
    need(EXIT, "KEY_LAST_FINGERPRINT", "exit deduplication")
    if "exit.description" in APP or '"description" to' in APP:
        failures.append("raw ApplicationExitInfo description reaches diagnostics")
    need(APP, '"descriptionCode"', "mapped exit description")
    need(APP, '"PROCESS_EXIT_RECORDED"', "process-exit event")

    backup = (ROOT / "app/src/main/res/xml/backup_rules.xml").read_text(encoding="utf-8")
    need(backup, "diagnostics_crash_envelope.xml", "crash-envelope backup exclusion")
    need(backup, "diagnostics_process_exit.xml", "exit-dedup backup exclusion")

    if failures:
        print("V52.9 CRASH/ANR CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("  note: crash envelope, watchdog thresholds, API guard, and exit dedup verified")
    print("  v52.9 crash/ANR/process-exit contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
