# Build 79: canonical legacy bitmap allocation ring

Build 78 completed 11.16 continuous hours on the V80 but failed Java-heap growth at
+6.21 MiB/6 h and total-PSS projection at 29.75 MiB/24 h. Native PSS, ownership,
resources, controller stability, and failure freedom passed.

The final pool counters rule out decoder incompatibility and application ownership leaks:
5,424 reuse requests produced 3,499 hits, 1,925 misses, and zero decoder reuse rejects;
3,592 of 3,594 tracked presentation allocations were retired. The process cycled 4.37 GB
of decoded pixels. Old ART repeatedly retained the committed Dalvik high-water even though
maintenance collections reclaimed unreachable objects.

Build 79 changes allocation capacity, not decoded image dimensions. When a legacy pool
request misses, it supplies BitmapFactory with a mutable backing bitmap rounded up to a
fixed 512 KiB allocation class. BitmapFactory reconfigures that backing store to the exact
requested output dimensions. Retired buffers therefore return as a small stable set of
capacity classes instead of thousands of slightly different allocations. This is the
bounded ring-buffer behavior suggested by the observed workload, without preallocating or
retaining photo, drawable, Compose, or source objects.

The build-78 pool changed between 8 MiB NORMAL and 4 MiB pressure budgets multiple times.
On API 22, restoring the larger budget let ART recommit pages after earlier pressure trims.
Build 79 makes the first pressure contraction sticky for the process lifetime: the session
starts with the 8 MiB warm-up ceiling, contracts to 4 MiB at first protection, and never
re-expands. A new process starts cleanly at 8 MiB again.

Telemetry adds canonical allocation/failure counts and the sticky-pressure flag. Pool
count, byte limits, visible decode dimensions, collage policy, image quality, ownership
grace, GC cadence, diagnostics, and all validation thresholds remain unchanged.
