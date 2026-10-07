# Build 88: unavailable-source fallback selection fix

Build 88 fixes one playback regression observed directly on the V80 after its configured
remote source became unavailable.

The effective pool was fallback-only (`primaryCount=0`), but folder-balanced playback still
probed the unavailable primary. That probe switched the shared shuffle scope and restored the
newest primary history item. The following fallback probe switched the scope back, producing a
deterministic stale-primary / bundled-default alternation.

Primary selection now runs only when the configured primary source list is non-empty. This does
not weaken stale-cache playback: when cached rows are available, the ViewModel explicitly places
the remote source in the primary list. Unavailable-source bookkeeping and folder reconciliation
remain unchanged.

The regression contract covers fallback-only, stale-cache, and healthy-primary configurations.
All Phase 4 memory, diagnostics, source-recovery, and scenario thresholds remain unchanged. Build
87 evidence is preserved but cannot close Build 88.
