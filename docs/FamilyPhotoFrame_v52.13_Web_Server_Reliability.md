# v52.13 web-server reliability

Status: prerelease; source correction complete, Android build and physical-device qualification pending.

Version: `0.12.11-prerelease` (`versionCode 31`), web revision `v52130`.

This increment corrects the empty Web Control pages introduced by v52.12 while preserving
its NAS recovery, transactional factory reset, upload admission, lifecycle serialization,
diagnostics, API-22 memory policy, portrait-only collages, shuffle, preview, and Web UI layout.

## Confirmed cause

NanoHTTPD assigns one `ClientHandler` to an entire HTTP/1.1 keep-alive connection. v52.12
replaced its unbounded runner with four connection workers and an eight-socket queue. A browser
loads HTML, CSS, JavaScript, and several API resources in parallel, then leaves connections
available for reuse. Idle keep-alive connections could occupy all four workers while an API
request needed to populate the tab pages remained queued. The page shell loaded, but every tab
stayed empty.

The v52.12 saturation test blocked artificial tasks and proved only the numerical worker/queue
limit. It did not model NanoHTTPD's persistent connection loop, so it could not detect this
starvation mode.

## Correction

- The four-worker/eight-queue bound and HTTP 503 saturation response are retained.
- `WebConfigServer` calls `response.closeConnection(true)` for every response.
- Workers are therefore request-scoped: after the body is sent, NanoHTTPD closes the socket and
  releases the worker instead of waiting for another request on an idle connection.
- Host/origin rejections now pass through the same response finalization path, so they also close
  cleanly and receive the standard security/cache headers.
- Web assets use revision `v52130`, preventing a browser from reusing older embedded JavaScript.

Opening a short TCP connection for each request costs little at the panel's polling rate and is
safer on the approximately 100 MiB API-22 target than either unbounded threads or connection
workers pinned for the 30-second socket timeout.

## Regression evidence

`WebConfigServerSecurityTest.browserKeepAliveSocketsCannotStarveBoundedWorkers` drives the real
server over raw HTTP/1.1. It sends more keep-alive connections than the complete worker-plus-queue
capacity, requires a complete response and EOF for every socket, verifies `Connection: close`,
then pairs and reads `/api/v1/status`. Without the connection-close policy, the test blocks on the
first idle connection or starves a later request.

The source verifier `scripts/verify-web-server-regression-v5213.py` also requires the bounded
runner, universal connection finalization, the real-socket test, current version/cache metadata,
and the browser's parallel boot request pattern.

## Verification boundary

Required before promotion from prerelease:

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug installDebug`
- Load the Web Control page on the physical tablet and open every tab.
- Leave the page open through at least ten preview/status polling cycles.
- Open a second browser simultaneously and confirm both remain responsive.
- Run the six-hour API-22 memory soak in `API22_MEMORY_SOAK_TEST.md`.

The delivery environment cannot download Gradle 8.9, so the Android Gradle tasks are not claimed
here. The real `BoundedHttpAsyncRunnerTest` and `WebConfigServerSecurityTest` sources were compiled
and executed in an isolated JVM harness against NanoHTTPD 2.3.1 (21 tests passed), including the
new raw-socket keep-alive regression. Android compilation, installation, and physical-device
qualification remain pending.
