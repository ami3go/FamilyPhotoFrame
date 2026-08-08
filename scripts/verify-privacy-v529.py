#!/usr/bin/env python3
"""Static Phase-5 privacy and installation-key contracts."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/familyphotoframe"


def main() -> int:
    failures: list[str] = []
    key_store = (MAIN / "data/diagnostics/DiagnosticIdentityKeyStore.kt").read_text()
    app = (MAIN / "App.kt").read_text()
    policy = (MAIN / "data/diagnostics/DiagnosticPrivacyPolicy.kt").read_text()
    jsonl = (MAIN / "data/diagnostics/DiagnosticsJsonl.kt").read_text()
    engine = (MAIN / "domain/engine/SlideshowEngine.kt").read_text()
    web = (MAIN / "web/WebServerController.kt").read_text()
    backup = (ROOT / "app/src/main/res/xml/backup_rules.xml").read_text()
    extraction = (ROOT / "app/src/main/res/xml/data_extraction_rules.xml").read_text()

    def need(text: str, value: str, label: str) -> None:
        if value not in text:
            failures.append(f"missing {label}: {value}")

    for value, label in (
        ("SecureRandom", "random installation key"),
        ("KEY_BYTES = 32", "256-bit key"),
        ("diagnostics_identity", "private preference name"),
        ("DiagnosticIdentityHasher.install", "startup key installation"),
        ("HmacSHA256", "HMAC-SHA-256 primitive"),
        ("TOKEN_BYTES = 12", "12-byte token suffix"),
    ):
        need(key_store + app + (MAIN / "data/diagnostics/DiagnosticIdentityHasher.kt").read_text(), value, label)
    need(backup, 'path="diagnostics_identity.xml"', "legacy backup exclusion")
    if extraction.count('path="diagnostics_identity.xml"') != 2:
        failures.append("data extraction must exclude identity key from cloud backup and device transfer")
    for marker in (
        "content|file|smb|https?", "password|passwd|secret", "IPv4", "filename",
        "DiagnosticIdentityHasher.current().token", "protectMessage",
    ):
        # The regex implementation uses descriptive code rather than every prose marker.
        if marker == "IPv4":
            if "25[0-5]" not in policy: failures.append("missing IPv4 value detector")
        elif marker == "filename":
            if "jpe?g|png|gif" not in policy: failures.append("missing photo filename detector")
        else:
            need(policy, marker, "privacy value policy")
    need(jsonl, "DiagnosticPrivacyPolicy.protect(key, rawValue)", "durable-boundary value policy")
    need(engine, '"folderToken"', "tokenized slide folder")
    need(engine, '"photoToken"', "tokenized slide photo")
    if '"folder" to current.folderName' in engine or '"folder" to photo.folderName' in engine:
        failures.append("raw slide folder remains")
    need(web, '"bindCategory" to bindCategory(host)', "coarse web bind category")
    if '"WEB_STARTED", "$host:$port"' in web:
        failures.append("WEB_STARTED still logs host text")

    forbidden_keys = re.compile(
        r'"(?:folder|source|photoId|photoIds|playlist|browserId|session|name|path|host|url|uri|username|share|album|password|gps|lat|lon)"\s+to'
    )
    for source in MAIN.rglob("*.kt"):
        if source.name.startswith("Diagnostic") or source.name == "CrashEnvelopeStore.kt":
            continue
        for line_number, line in enumerate(source.read_text().splitlines(), 1):
            if forbidden_keys.search(line):
                failures.append(f"legacy private diagnostic field at {source.relative_to(ROOT)}:{line_number}")

    if failures:
        print("V52.9 PRIVACY CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("  install-key, value policy, call-site, and backup-exclusion contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
