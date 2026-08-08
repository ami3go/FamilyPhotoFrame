# Phase 1 evidence: how to run it and send it to me

The Phase 1 gate (spec §22.2) has items that no amount of code review settles — they need
a real NAS being switched off and on, a real reboot, and a real 24 hours. This is the
tooling for capturing that and handing it back for analysis.

## What changed

Diagnostics used to be an in-memory ring buffer of 1000 entries, **lost on every process
death**. That is unusable for this gate: a 24-hour NAS-cycling run, auto-start after
reboot, and "no crash" are all questions about what survives a restart.

Now:

- **Durable JSONL log** in app-private storage (`FileDiagnosticsSink`), rotating at 2 MB
  with two retained generations. JSONL because these runs end abruptly — a truncated file
  still parses line by line, where a truncated JSON document parses as nothing.
- **Session ids per process**, so restarts (expected or not) are visible. Without them a
  24-hour log looks continuous even if the app died repeatedly.
- **Structured fields** rather than free text, so analysis counts facts instead of
  pattern-matching prose.
- **Heap/uptime samples every 5 minutes** (~288/day) for the rolling-6h growth criterion.
- **Evidence events**: `SLIDE_RENDERED` (source, folder, mode), `POOL_CONFIGURED`
  (which sources are merged), `BOOT_AUTOSTART` (with API level), plus the existing source
  up/down transitions.
- **Redaction enforced at the sink**, not left to call sites — see below.

## How to run the test

1. Configure a local folder **and** an SMB share, and merge them: Settings → "Also play
   from". The gate asks for one SAF + one SMB source playing together.
2. Web panel → Diagnostics → **Clear log** (gives a clean baseline).
3. Leave the frame running for **24 hours**. During that window:
   - switch the NAS **off** for a while, then back **on** (ideally twice);
   - **reboot the device** at least once — and if you have a second device on a
     different Android version, run it there too, since auto-start is required on ≥2 API
     levels;
   - use the D-pad a little, and change an overlay setting.
4. Web panel → Diagnostics → **Download full log (JSONL)**.
5. Send me that file.

## What I will do with it

```
python3 scripts/analyze-diagnostics.py familyphotoframe-diagnostics.jsonl
```

It reports each gate criterion as PASS / FAIL / **NO DATA**. That third state matters:
a criterion with no supporting events means the run never exercised it, and calling that
a pass would defeat the point of running the test at all.

Covered by the log: merged SAF+SMB playback, actual randomization, Room-indexed playback
(no per-slide NAS enumeration), primary/fallback transitions, no permanent blank after a
loss, crash-free operation, 24-hour coverage, heap growth, auto-start across API levels,
and absence of secrets.

**Not covered, deliberately:** "overlays configurable", "D-pad operation" and "SMB
credentials encrypted via SecretStore". Those are UI and Keystore properties; inferring
them from event counts would be a guess dressed up as evidence. `MANUAL_TEST_CHECKLIST.md`
is the right instrument.

## Privacy

The bundle is intended to be shared, so redaction is enforced where entries become
durable rather than trusted to each call site. Fields named like credentials
(`password`, `token`, `apikey`, `otp`, …), location (`gps`, `lat`, `lon`) or filesystem
paths (`path`, `uri`) are **dropped entirely**; values are truncated at 200 characters;
control characters and quotes are escaped so no value can break the line format.

This is asserted, not asserted-about: the offline harness feeds in credentials, GPS, a
private path, embedded quotes and newlines, and a 500-character value, and checks each is
handled. The analyser also independently scans the delivered file for credential-shaped
content and fails if it finds any.

Photo *filenames* and *folder names* are included (truncated) — they are needed to tell
sources apart and to see variety. If that is too much for your library, say so and I can
hash them instead.

## Verification status

The analyser was tested against synthetic logs both good and bad: excessive heap growth,
an unexplained restart, a single-source pool, a permanent blank after a loss, and a
planted credential each produce the expected FAIL, and a short log produces NO DATA
rather than a false pass. The app-side code type-checks and its redaction is unit-tested;
as ever, none of it has run on a device, so treat the first bundle partly as a test of
the logging itself.


## A bug this tooling caught before you ran anything

The first end-to-end run of the real pipeline simulated 26 hours and produced a log
covering **9.6**. Rotation had silently discarded the first two thirds — taking the pool
configuration, both NAS outages and the first reboot with it. Worse, the analyser then
reported *"no merged multi-source pool was configured"*, which was false: the pool had
been configured, the record had simply aged out. Following that would have meant a wasted
day and a hunt for a bug that did not exist.

Two fixes:

- **The log is now two streams.** Rare, load-bearing events (sessions, boots, source
  transitions, heap samples) go to their own file that high-frequency slide records can
  never crowd out. At observed volumes the event stream spans months; only slide detail
  can age out.
- **The analyser distinguishes "did not happen" from "rotated away."** It detects
  truncation, says so, and reports NO DATA where a conclusion would be unsafe — including
  refusing to measure a recovery gap across the rotation boundary, which had produced an
  alarming and meaningless "18 hour blank".

`scripts/verify-diagnostics-e2e.sh` now runs both scenarios on every verification pass: a
realistic 2 MB/4 MB budget, which must pass every criterion, and a starved 32 KB budget,
which must still preserve the evidence stream and degrade to NO DATA rather than a
confident wrong answer.

### Log volume in practice

At a 30-second interval, roughly 440 KB/day of slide records and 46 KB/day of events. The
retained budgets (4 MB × 3 for slides, 2 MB × 3 for events) cover about four weeks of
slides and several months of events. At the minimum 3-second interval the slide stream
holds two to three days — enough for this gate, and the analyser warns if it ever matters.
