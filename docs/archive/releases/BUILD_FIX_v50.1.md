# FamilyPhotoFrame v50.1 — Kotlin compile fix

Corrected three Kotlin/Coil type mismatches introduced in v50 reliability diagnostics:

- Convert Coil `MemoryCache.size` (`Int`) to `Long` before division for KiB diagnostics.
- Convert Coil `MemoryCache.maxSize` (`Int`) to `Long` before division for KiB diagnostics.
- Define the 32 MiB `IMAGE_MEMORY_CACHE_BYTES` constant as `Int`, matching Coil's `maxSizeBytes(Int)` API.

The cache limit remains exactly 33,554,432 bytes (32 MiB). No functional settings or database behavior changed.
