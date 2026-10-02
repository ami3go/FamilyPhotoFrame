# Build 80: fixed bitmap arena for API 21–25

Build 78's 11.16-hour V80 window proved that logical bitmap ownership was balanced while
old ART's committed Dalvik floor continued to rise. The decoder made 5,424 reuse requests,
missed 1,925 times, retired 3,592 of 3,594 tracked allocations, and cycled 4.37 GB of
decoded pixels. Native PSS, file descriptors, threads, ownership, and active bitmap counts
were bounded. Build 79 canonicalized miss capacities, but it still permitted new slots to
be created whenever every retained buffer was in active use.

Build 80 replaces the elastic API-21..25 reuse pool with a fixed six-slot pixel arena:

- four standard slots with 2 MiB capacity;
- two large slots with 4 MiB capacity;
- lazy allocation, so unused slots consume no memory;
- stable identities for the process lifetime;
- coroutine suspension when all compatible slots are leased;
- no exact-size or seventh-allocation fallback;
- delayed display-list retirement returns the same identity to the arena.

The six slots are sufficient for outgoing and incoming collage presentations. Standard
requests may spill into an unused large slot, which also provides bounded scratch capacity
for EXIF orientation. If all compatible slots are active, preparation waits without
blocking a thread until transition retirement returns one. Existing selected-presentation
watchdogs and cancellation remain authoritative, so a broken retirement path fails as a
timeout instead of silently expanding memory.

Decoder sizing now reserves density-scaled output storage rather than the larger
pre-density intermediate. The fixed class ceiling supplies rounding headroom. The decoded
dimensions requested from BitmapFactory, image quality, collage policy, transition rules,
diagnostics, and Phase 4 thresholds are unchanged.

This engine is enabled only for the validated low-memory API-21..25 tier. Modern Android
continues to use the normal Coil decoder.
