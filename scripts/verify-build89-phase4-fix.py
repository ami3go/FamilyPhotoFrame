#!/usr/bin/env python3
"""Static contracts for Build 89's remote-hash and retention fixes."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    build = (ROOT / "app/build.gradle.kts").read_text()
    cache = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/cache/MediaCache.kt").read_text()
    vm = (ROOT / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt").read_text()
    sink = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/FileDiagnosticsSink.kt").read_text()
    bundle = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsBundle.kt").read_text()
    catalog = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt").read_text()
    build_number = re.search(r"val buildNumber = (\d+)", build)

    checks = {
        "install-distinct versionCode at least 90": bool(
            build_number and int(build_number.group(1)) >= 90
        ),
        "cache exposes bounded hash updates": "val contentHashUpdates: SharedFlow<String>" in cache,
        "cache hashes verified local file": "scheduleLocalContentHash(item, target)" in cache,
        "remote display avoids direct backfill": "if (!current.needsCache && lastHashPhotoId != current.id)" in vm,
        "hash reconciliation is batched": "CONTENT_HASH_RECONCILE_BATCH_SIZE = 32" in vm,
        "deferred remote hash event is registered": '"REMOTE_CONTENT_HASH_DEFERRED_TO_CACHE"' in catalog,
        "reservation recovery fields are retained": all(
            field in catalog for field in ('"reservations"', '"ageMs"')
        ),
        "actual evidence eviction is tracked": "evictedGenerations" in sink,
        "bundle partial status uses eviction": "stream.evictedGenerations > 0L" in bundle,
    }
    failures = [label for label, passed in checks.items() if not passed]
    if failures:
        print("BUILD 89 PHASE 4 FIX CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("Build 89 Phase 4 fix contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
