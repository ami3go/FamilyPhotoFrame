#!/usr/bin/env python3
"""Focused source-contract checks for v52.1 shuffle hardening."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
repo = (ROOT / 'app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepository.kt').read_text()
engine = (ROOT / 'app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt').read_text()
vm = (ROOT / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt').read_text()
tests = (ROOT / 'app/src/androidTest/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepositoryTest.kt').read_text()

checks = {
    'commit rejects unreserved photo ids': '"unreserved_photo"' in repo and 'selectedIds.any { it !in reservedIds }' in repo,
    'commit requires reserved anchor first': '"anchor_not_first"' in repo and 'selectedIds.first() != reservedIds.first()' in repo,
    'commit validates persisted reservation rows': '"reservation_rows_changed"' in repo and 'reservationRowsMatch' in repo,
    'commit rejects failed/non-reserved tiles': '"photo_not_reserved"' in repo and 'PhotoEntryState.RESERVED.name' in repo,
    'candidate failure cannot retire anchor': 'decodeLongs(reservation.photoIdsJson).firstOrNull() == failedPhotoId' in repo,
    'failed candidate removed from live candidate list': 'candidatePhotoIds.filterNot { it == failure.photoId }' in engine,
    'startup uses at-most-once recovery': '!startupSelectionPending && newest != null' in engine and 'startupSelectionPending = false' in engine,
    'folder-balanced collage never falls back outside reservation': 'Missing reservation/history metadata is treated as no companions' in vm,
    'explicit empty reservation remains a single photo': 'if (orderedIds.isEmpty()) return emptyList()' in vm,
    'instrumentation covers unreserved companion rejection': 'commitRejectsUnreservedOrFailedCompanions_withoutAdvancing' in tests,
    'instrumentation covers anchor candidate guard': 'candidateFailureApiCannotRetireReservationAnchor' in tests,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
if failed:
    raise SystemExit('Phase 6 contract failures: ' + ', '.join(failed))
print(f'Phase 6 shuffle hardening contract passed ({len(checks)} checks)')
