# Build 78: decode quality and pressure-aware bitmap reuse

Build 77 proved that an 8 MiB, six-entry legacy bitmap pool improved reuse, but the V80
entered memory protection early and a collage tile was visibly soft. The evidence showed
no reuse rejection or ownership imbalance. The quality loss instead came from multiplying
the V80's static low-tier decode scale by the runtime protection scale.

Build 78 treats those values as independent ceilings. In NORMAL, the V80 uses its existing
1.5-megapixel device-tier budget. Under protection, the stricter runtime ceiling applies
once; it is no longer multiplied by the device ceiling.

The expanded pool is also conditional. NORMAL may retain up to six buffers / 8 MiB. Any
non-NORMAL protection state immediately trims retained buffers to at most 4 MiB. Returning
to NORMAL restores only the budget and does not allocate buffers. Heap-sample telemetry
records the active budget and pressure-trim count so the hardware run can verify both
quality and memory behavior.

No collage frequency, source selection, transition, image-cache, ownership, transport,
diagnostic, recovery, or validation threshold was weakened.
