# Build 81: legacy bitmap-arena page pre-touch

Build 80 proved that a fixed six-identity bitmap arena eliminates physical decode-allocation
churn, ownership loss, and unbounded native/resource growth. Its 19.93-hour V80 run nevertheless
showed a Dalvik-driven total-PSS slope while the arena served more than 9,000 reuse hits without
allocating a seventh identity.

The post-warm-up Dalvik-PSS edge reached roughly 17 MiB, closely matching the fixed arena's
16 MiB backing capacity plus allocator overhead. Native PSS, active bitmap ownership, Activities,
ViewRoots, FDs, threads, SQLite, and transport resources stayed bounded. The total-PSS projection
also decelerated as more of the finite arena was exercised. This identifies delayed private-page
commitment of API-22 Dalvik-backed bitmap pixels rather than an object-ownership leak.

Build 81 keeps the build-80 architecture, dimensions, image quality, and six-slot/16 MiB limits.
The only runtime change is that each new arena slot is cleared across its full capacity on the
background decoder thread before it can be leased. This commits the fixed pixel pages during the
two-hour warm-up instead of allowing later slideshow churn to create an apparent steady-state
PSS slope. Telemetry records `legacyBitmapArenaPretouchedSlots`; it must equal both allocated-slot
and canonical-allocation counts, with all three plateauing at six.
