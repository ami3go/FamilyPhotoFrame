# API 22 OOM leak investigation (2026-08)

Status: **root cause not yet identified.** The leak is characterised and reproducible;
the retaining structure is still unknown. This document records what is established, what
has been ruled out, and how to resume — including two invalid measurements that should not
be repeated.

Related: [`API22_MEMORY_SOAK_TEST.md`](API22_MEMORY_SOAK_TEST.md),
[`FamilyPhotoFrame_v52.10_API22_Memory_Hardening.md`](FamilyPhotoFrame_v52.10_API22_Memory_Hardening.md).

## Environment

Test frame: `V80 PLUS` (`inet_w`), **Android 5.1 / API 22**, Java heap max **174 MB**
(`largeHeap=true`, `dalvik.vm.heapsize=174m`). On API ≤ 25 bitmap pixel data lives on the
**Java heap**, so decoded photos compete with everything else for the same 174 MB.

## Symptom

`java.lang.OutOfMemoryError` after roughly 14 hours of continuous playback. Two crashes were
captured. The first was severe enough that the runtime could not build a stack trace:

```
FATAL EXCEPTION: main
java.lang.OutOfMemoryError: OutOfMemoryError thrown while trying to throw OutOfMemoryError;
no stack available
```

The second was caught by the app's own `CrashEnvelopeStore`:

```
exceptionClass    OutOfMemoryError        mainThread true
heapUsedKb        176805 / 178176         (99.2% full)
nativeHeapKb      81434     pssKb 146937
decodedBitmapCount 1        activeDecodedBytes 1375864   (~1.3 MB)
imageCacheKb      0
memoryProtectionLevel CRITICAL
surface PLAYING  engineState PLAYING_PRIMARY  sourceKind SMB  layout SINGLE
elapsedRealtimeMs 133004663   (~37 h process uptime)
```

The crash site itself (a Compose clock tick in `Overlays.kt`) is incidental — at 12 bytes
free, any allocation would have thrown.

**The important contrast:** the heap is full, but the app's own bitmap accounting sees only
1.3 MB. The retained memory is not the photo pipeline.

## Established characterisation

Heap growth tracks **playback activity**, not wall-clock time:

| session    | playing?        | heap growth                    | slides/h | KB/slide |
|------------|-----------------|--------------------------------|----------|----------|
| `58b2a3f3` | yes             | 11.88 MB/h                     | 141      | **86**   |
| `2c33a47a` | yes             | 11.75 MB/h                     | —        | —        |
| `d1fa2f1c` | yes             | 10.08 MB/h → **OOM at 14.2 h** | —        | —        |
| `323005da` | no (night mode) | flat across 8.7 h              | 2.6      | —        |

**Roughly 86 KB is retained per photo displayed.** At ~141 slides/h that reproduces the
observed ~11.8 MB/h, and ~14 h of playback fills the 174 MB heap. An idle frame never crashes,
which is why the fault only appears on frames left running all day.

## Ruled out

| Candidate | Why rejected |
|---|---|
| Bitmap pipeline | Tracked bitmaps never exceed 5.5 MB. `PreparedSlideMemory.kt` already handles the API 21–25 bitmap-on-Java-heap case via `LegacyBitmapReclaimer`. |
| Coil memory cache | Bounded to 6 MB by `ImageMemoryBudget`; reports 0 KB in samples. |
| Diagnostics ring buffer | `DiagnosticsLog` bounded at 1000 entries. |
| `DiagnosticOperationTracker` / `DiagnosticRateController` | Both bounded, trimmed, and expiry-checked. |
| Web server registries | `BoundedHttpAsyncRunner.running` is removed in `closed()`. |
| jcifs SMB context | A single lazily-created shared `CIFSContext`, not per-request. |
| `java.util.regex.Matcher` churn | **Investigated and rejected.** Counts grew 610 → 25 254 over 32 min, but a reference-graph walk showed only 2 694 had *any* inbound reference — the rest were unreachable garbage, not retention. |

The Matcher result is worth keeping in mind: `DiagnosticPrivacyPolicy.protect()` runs up to
16 regexes per field per event, which is real allocation churn on this device, but it is
**not** the leak.

## Invalid measurements — do not repeat

1. **A 32-minute dump diff at NORMAL pressure showed +1.4 MB.** Too short, and the frame was
   barely cycling photos.
2. **A 6-hour overnight capture (23:23→05:27) showed a completely flat heap.** This was
   worthless: the frame's own brightness schedule blacks the screen 23:00–07:00
   (`brightnessAutomation.periods`, `night` period, `action: BLACK_SCREEN`). The frame was
   asleep by design for the entire window.

**Always confirm slides are advancing before trusting any heap measurement.**

## How to resume

### 1. Connect to the frame

Wireless debugging avoids the flaky USB link. Find the frame's address and connect:

```bash
adb shell ip addr show wlan0 | grep 'inet '   # over USB, once
adb tcpip 5555                                # over USB, once
adb connect <frame-ip>:5555
export FRAME_ADB=<frame-ip>:5555
```

A new workstation has its own adb key, so the tablet will report `unauthorized` until
**Allow USB debugging** is accepted on the device.

### 2. Verify the frame is actually playing — non-negotiable

```bash
adb -s "$FRAME_ADB" shell "run-as com.example.familyphotoframe \
  cat files/diagnostics-slides/diagnostics.jsonl" | grep -c SLIDE_SELECTED
```

Run it twice a few minutes apart. The count must climb (~2/min). If it does not, the frame is
in its night window or the activity is `STOPPED`, and any measurement is meaningless.

### 3. Capture heap dumps during playback

```bash
export FRAME_ADB=<frame-ip>:5555
scripts/heap-soak-capture.sh          # 8 dumps, 20 min apart, into ./heap-soak
```

Each dump is recorded alongside a heap sample and a cumulative slide count, so growth can be
normalised per slide.

### 4. Analyse

```bash
python3 scripts/heap-hprof-histogram.py  dump.hprof              # class histogram
python3 scripts/heap-finalizer-queue.py  dump.hprof              # objects awaiting finalization
python3 scripts/heap-inbound-refs.py     dump.hprof <ClassName>  # inbound refs to a class
```

Diff two histograms, then run `heap-inbound-refs.py` on whatever grew.

`heap-inbound-refs.py` is the decisive tool: it separates **retained** objects from
**uncollected garbage**. That distinction is what invalidated the Matcher hypothesis, and any
future candidate must survive it. A genuine leak shows inbound edges naming the holding class
and field.

The scripts are self-contained hprof parsers (no MAT required). Convert Android dumps first:

```bash
adb -s "$FRAME_ADB" shell am dumpheap com.example.familyphotoframe /data/local/tmp/h.hprof
adb -s "$FRAME_ADB" pull /data/local/tmp/h.hprof raw.hprof
"$ANDROID_HOME/platform-tools/hprof-conv" raw.hprof dump.hprof
```

## Reading the frame's own diagnostics

```bash
adb shell "run-as com.example.familyphotoframe cat files/diagnostics/diagnostics.jsonl"
adb shell "run-as com.example.familyphotoframe cat files/diagnostics-slides/diagnostics.jsonl"
adb shell "run-as com.example.familyphotoframe cat files/datastore/app_settings.json"
```

Both streams rotate into `.1.jsonl` / `.2.jsonl`. A field only reaches the file if it is listed
in that event's `permittedFields` in `DiagnosticEventSpec.kt`.

## Possible unrelated defect (uninvestigated)

At the 07:00 day boundary the frame did not wake on its own. The device was asleep with the
activity `STOPPED`, so `FLAG_KEEP_SCREEN_ON` (`MainActivity.kt:138`) had no effect — it applies
only while the window is visible and cannot wake a sleeping device. A manual wake was required.
If a frame is expected to resume unattended each morning, this is a real defect and is
independent of the OOM.
