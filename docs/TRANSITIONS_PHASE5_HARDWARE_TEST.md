# Transition Phase 5 — Huawei Hardware Endurance Test

## Test target

- Device: Huawei PLK-L01
- Android: 6.0 / API 23
- Build: FamilyPhotoFrame v50.5
- Power: continuous external power
- Photo source: mixed landscape and portrait folders, including two- and three-photo collages

## Configuration

1. Set photo interval to **3 seconds**.
2. Set transition mode to **Ambient Random**.
3. Set transition duration to **Normal / 900 ms**.
4. Keep Reduce motion disabled.
5. Enable diagnostic logging.
6. Include a remote SMB source and, where practical, introduce periods of slow network response.
7. Let the frame complete at least **1,000 transitions**. At a 3-second dwell plus transition time, allow approximately 70–90 minutes depending on effects and loading.

## Manual observations

Record any occurrence of:

- black or uncovered frames;
- collage tiles moving out of synchronization;
- visible UI stalls;
- repeated transition crashes or fallbacks;
- manual Next/Previous leaving a half-transition frame;
- blank screen after network or decode failure;
- excessive image repetition.

## Export and analyse

Download the full diagnostics JSONL bundle from the paired web interface, then run:

```bash
python3 scripts/analyze-transition-diagnostics.py familyphotoframe-diagnostics.jsonl
```

A complete gate should report PASS for:

- at least 1,000 completed transitions;
- at least 95% of measured frame intervals within 33.3 ms;
- no UI stall over 250 ms;
- ready-to-first-frame latency no greater than 100 ms;
- no more than three retained prepared slides;
- unexpected fallback rate below 1%;
- cancellation rate no greater than 5%;
- heap growth no greater than 20 MiB after warm-up;
- no crash, OOM, ANR, renderer-crash, or black-frame marker;
- balanced low-performance-mode entry and exit events.

## Advanced-effect spot test

Because Ambient Random intentionally excludes Soft Reveal and Soft Focus Fade until validation, run each of these as a fixed effect for at least 100 transitions and repeat the diagnostics analysis. Also test Depth Fade and Ken Burns Handoff as fixed effects for at least 50 transitions each.

## Release decision

Do not make Ambient Random the new-installation default unless:

- the full 1,000-transition run passes;
- both excluded advanced effects pass their fixed-effect spot tests;
- no manual black-frame observation is recorded;
- memory remains bounded on the Huawei frame.
