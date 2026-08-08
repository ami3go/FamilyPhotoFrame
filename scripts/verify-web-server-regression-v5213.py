#!/usr/bin/env python3
"""Contracts for the v52.13 bounded-web-server keep-alive starvation fix."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
server = read("app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt")
runner = read("app/src/main/java/com/example/familyphotoframe/web/BoundedHttpAsyncRunner.kt")
server_test = read("app/src/test/java/com/example/familyphotoframe/web/WebConfigServerSecurityTest.kt")
ui = read("app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt")
release_notes = read("docs/FamilyPhotoFrame_v52.13_Web_Server_Reliability.md")

close_policy = server.index("response.closeConnection(true)")
body_policy = server.index("closeIfBodyUnread(session, response)")
security_headers = server.index('response.addHeader("X-Content-Type-Options"')

checks = {
    "v52.13 behavior retained in current metadata":
        "versionCode = 33" in build and "0.12.13-prerelease" in build and
        'REVISION: String = "v52150"' in assets,
    "bounded worker and queue protection retained":
        "ThreadPoolExecutor" in runner and "ArrayBlockingQueue" in runner and
        "DEFAULT_WORKERS = 4" in runner and "DEFAULT_QUEUE_CAPACITY = 8" in runner and
        "setAsyncRunner(BoundedHttpAsyncRunner" in server,
    "every routed response closes its connection":
        body_policy < close_policy < security_headers and
        "return closeIfBodyUnread" not in server,
    "keep-alive starvation rationale is recorded":
        "idle browser sockets" in server and "starve the web UI" in server and
        "idle HTTP keep-alive socket" in runner,
    "real HTTP regression exercises more than full capacity":
        "browserKeepAliveSocketsCannotStarveBoundedWorkers" in server_test and
        "rawKeepAliveGet" in server_test and
        "Connection: keep-alive" in server_test and
        "DEFAULT_WORKERS + BoundedHttpAsyncRunner.DEFAULT_QUEUE_CAPACITY + 2" in server_test and
        "Connection: close" in server_test and
        'request("/api/v1/status"' in server_test,
    "browser boot still exercises parallel data requests":
        "Promise.all([loadSettings(),loadStatus(),loadPresentation()])" in ui,
    "release notes document cause, behavior and boundary":
        all(text in release_notes for text in [
            "keep-alive", "four connection workers", "response.closeConnection(true)",
            "WebConfigServerSecurityTest", "Gradle 8.9", "API-22",
        ]),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
if failed:
    print(f"v52.13 web-server regression verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)
print(f"v52.13 web-server regression verification: PASS ({len(checks)} checks)")
