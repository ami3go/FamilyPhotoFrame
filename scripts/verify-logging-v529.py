#!/usr/bin/env python3
"""Static release contracts for the v52.9 diagnostics implementation."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java"
CATALOG = MAIN / "com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt"


def call_bodies(text: str, pattern: re.Pattern[str]):
    for match in pattern.finditer(text):
        index, depth, quote, escaped = match.end(), 1, None, False
        while index < len(text) and depth:
            char = text[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
            else:
                if char in "\"'":
                    quote = char
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
            index += 1
        yield match.start(), text[match.end():index - 1]


def main() -> int:
    failures: list[str] = []
    catalog_text = CATALOG.read_text(encoding="utf-8")
    registered = set(re.findall(r'"([A-Z][A-Z0-9_]{2,})"', catalog_text))
    emitted: set[str] = set()
    dynamic: list[str] = []
    log_pattern = re.compile(
        r"\b(?:diagnostics|diag|services\.diagnostics)\.log\s*\(|"
        r"\bonEvent\s*\(|\bonCollageEvent\s*\("
    )
    adapters = {
        "web/WebServerController.kt": ("code, \"\", fields",),
        "ui/slideshow/SlideshowViewModel.kt": ("event.code", "event,"),
    }

    for path in MAIN.rglob("*.kt"):
        relative = path.relative_to(MAIN / "com/example/familyphotoframe").as_posix()
        text = path.read_text(encoding="utf-8")
        for start, body in call_bodies(text, log_pattern):
            if re.search(r"\bfun\s+$", text[max(0, start - 12):start]):
                continue
            literals = re.findall(r'"([A-Z][A-Z0-9_]{2,})"', body)
            emitted.update(literals)
            if not literals and not any(marker in body for marker in adapters.get(relative, ())):
                line = text.count("\n", 0, start) + 1
                dynamic.append(f"{relative}:{line}: {body[:80].replace(chr(10), ' ')}")

    transition_text = (MAIN / "com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt").read_text()
    emitted.update(re.findall(r'event\("(TRANSITION_[A-Z0-9_]+)"', transition_text))

    categorical_values = {
        "LOCAL_SAF", "SMB", "SYNOLOGY", "WEBDAV", "SAMPLES", "NONE", "UNKNOWN",
        "SAMPLING", "PASS", "FAIL", "SERVER_BUSY",
    }
    missing = sorted(emitted - registered - categorical_values)
    if missing:
        failures.append("unregistered emitted codes: " + ", ".join(missing))
    if dynamic:
        failures.append("untyped dynamic event calls:\n    " + "\n    ".join(dynamic))

    all_main = "\n".join(path.read_text(encoding="utf-8") for path in MAIN.rglob("*.kt"))
    if re.search(r'"\$\{[^}]+\}_[A-Z][A-Z0-9_]+"', all_main):
        failures.append("dynamic string-interpolated event code remains")
    if "severityFor(" in all_main:
        failures.append("severityFor(code) inference remains in production")
    diagnostics_log = (MAIN / "com/example/familyphotoframe/data/diagnostics/DiagnosticsLog.kt").read_text()
    if "isHighVolume(" in diagnostics_log:
        failures.append("hard-coded high-volume routing remains")

    jsonl = (MAIN / "com/example/familyphotoframe/data/diagnostics/DiagnosticsJsonl.kt").read_text()
    for field in (
        "schemaVersion", "sequence", "atEpochMs", "elapsedRealtimeMs", "sessionId",
        "severity", "category", "code", "origin", "operationId", "parentOperationId", "fields",
    ):
        if field not in jsonl:
            failures.append(f"schema-v2 field is not encoded: {field}")

    analyzer = (ROOT / "scripts/diagnostics_analysis.py").read_text()
    if "SUPPORTED_SCHEMAS = {1, 2}" not in analyzer or 'raw.get("schemaVersion", 1)' not in analyzer:
        failures.append("analyzer does not retain v1/v2 compatibility")

    if failures:
        print("V52.9 LOGGING CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print(f"  note: {len(registered)} catalog codes; {len(emitted)} emitted literal codes verified")
    print("  v52.9 schema/catalog contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
