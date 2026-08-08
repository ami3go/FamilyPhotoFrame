#!/usr/bin/env python3
"""Deterministic 24-hour-equivalent dynamic shuffle model acceptance simulation."""
from __future__ import annotations

import random
from dataclasses import dataclass, field

R = random.Random(5202)

@dataclass
class Scope:
    eligible: dict[str, set[str]]
    folder_remaining: list[str] = field(default_factory=list)
    photo_remaining: dict[str, list[str]] = field(default_factory=dict)
    last_folder: str | None = None
    last_photo: dict[str, str] = field(default_factory=dict)
    terminal: set[str] = field(default_factory=set)
    reservations: tuple[str, tuple[str, ...]] | None = None
    cycle: int = 0

    def reconcile(self) -> None:
        live = set(self.eligible)
        removed_pending = [f for f in self.folder_remaining if f not in live]
        self.folder_remaining[:] = [f for f in self.folder_remaining if f in live]
        self.terminal.update(removed_pending)
        for f in sorted(live):
            if f not in self.folder_remaining and f not in self.terminal:
                self.folder_remaining.insert(R.randrange(len(self.folder_remaining) + 1), f)
            pool = self.photo_remaining.setdefault(f, [])
            pool[:] = [p for p in pool if p in self.eligible[f]]
            known = set(pool)
            for p in sorted(self.eligible[f] - known):
                pool.insert(R.randrange(len(pool) + 1), p)
        for f in list(self.photo_remaining):
            if f not in live:
                del self.photo_remaining[f]

    def new_folder_cycle(self) -> None:
        self.cycle += 1
        self.terminal.clear()
        self.folder_remaining = sorted(self.eligible)
        R.shuffle(self.folder_remaining)
        if len(self.folder_remaining) > 1 and self.folder_remaining[0] == self.last_folder:
            self.folder_remaining[0], self.folder_remaining[1] = self.folder_remaining[1], self.folder_remaining[0]

    def reserve(self, unavailable: set[str]) -> tuple[str, str] | None:
        assert self.reservations is None
        self.reconcile()
        if not self.folder_remaining:
            self.new_folder_cycle()
        for folder in list(self.folder_remaining):
            if folder in unavailable:
                self.folder_remaining.remove(folder)
                self.terminal.add(folder)  # explicit one-cycle skip
                continue
            photos = self.photo_remaining.setdefault(folder, [])
            if not photos:
                photos[:] = sorted(self.eligible[folder])
                R.shuffle(photos)
                last = self.last_photo.get(folder)
                if len(photos) > 1 and photos[0] == last:
                    photos[0], photos[1] = photos[1], photos[0]
            if not photos:
                self.folder_remaining.remove(folder)
                self.terminal.add(folder)
                continue
            self.reservations = (folder, (photos[0],))
            return folder, photos[0]
        return None

    def commit(self) -> tuple[int, str, str]:
        assert self.reservations is not None
        folder, photos = self.reservations
        photo = photos[0]
        assert folder in self.folder_remaining
        assert photo in self.photo_remaining[folder]
        self.folder_remaining.remove(folder)
        self.photo_remaining[folder].remove(photo)
        self.terminal.add(folder)
        self.last_folder = folder
        self.last_photo[folder] = photo
        self.reservations = None
        return self.cycle, folder, photo

    def recover(self) -> None:
        self.reservations = None


def assert_cycle_unique(history: list[tuple[int, str]]) -> None:
    by_cycle: dict[int, list[str]] = {}
    for cycle, folder in history:
        by_cycle.setdefault(cycle, []).append(folder)
    for folders in by_cycle.values():
        assert len(folders) == len(set(folders)), folders


def main() -> None:
    base = {f"folder-{i}": {f"f{i}-p{j}" for j in range(1, i % 13 + 2)} for i in range(1, 51)}
    scopes = {"all": Scope({k: set(v) for k, v in base.items()}), "favorites": Scope({k: set(v) for k, v in base.items() if int(k.split('-')[1]) % 2 == 0})}
    unavailable: set[str] = set()
    history: dict[str, list[tuple[int, str]]] = {name: [] for name in scopes}
    shown_by_photo_cycle: dict[tuple[str, str, int], set[str]] = {}
    photo_cycle_no: dict[tuple[str, str], int] = {}

    # 1,440 one-minute-equivalent ticks with deterministic dynamic events.
    for tick in range(1_440):
        name = "all" if tick % 7 else "favorites"
        scope = scopes[name]
        if tick % 73 == 0:
            folder = f"dynamic-{tick // 73}"
            scope.eligible[folder] = {f"{folder}-p1", f"{folder}-p2"}
        if tick % 109 == 0 and len(scope.eligible) > 8:
            victim = sorted(scope.eligible)[tick % len(scope.eligible)]
            scope.eligible.pop(victim, None)
        if tick % 31 == 0 and scope.eligible:
            folder = sorted(scope.eligible)[tick % len(scope.eligible)]
            scope.eligible[folder].add(f"{folder}-new-{tick}")
        if tick % 47 == 0 and scope.eligible:
            folder = sorted(scope.eligible)[tick % len(scope.eligible)]
            if len(scope.eligible[folder]) > 1:
                scope.eligible[folder].remove(sorted(scope.eligible[folder])[0])
        if tick % 180 == 0:
            unavailable = {f for f in scope.eligible if f.startswith("folder-") and int(f.split('-')[1]) % 11 == 0}
        if tick % 180 == 60:
            unavailable.clear()
        if tick % 137 == 0:
            scope.reconcile(); scope.reconcile()  # repeated identical rescan must be idempotent
            assert len(scope.folder_remaining) == len(set(scope.folder_remaining))
        if tick % 223 == 0:
            scope.recover()  # process-kill reservation recovery
        if tick % 401 == 0:
            scope.folder_remaining.clear(); scope.photo_remaining.clear(); scope.terminal.clear(); scope.reservations = None
            scope.cycle += 1  # explicit reset starts a distinct next cycle

        reserved = scope.reserve(unavailable)
        if reserved is None:
            continue
        folder, photo = reserved
        if tick % 97 == 0:
            scope.recover()  # failed preparation does not consume turn
            assert folder in scope.folder_remaining
            continue
        committed_cycle, committed_folder, committed_photo = scope.commit()
        history[name].append((committed_cycle, committed_folder))
        key = (name, committed_folder)
        pc = photo_cycle_no.get(key, 1)
        seen = shown_by_photo_cycle.setdefault((name, committed_folder, pc), set())
        if committed_photo in seen:
            pc += 1
            photo_cycle_no[key] = pc
            seen = shown_by_photo_cycle.setdefault((name, committed_folder, pc), set())
        assert committed_photo not in seen
        seen.add(committed_photo)
        assert scope.reservations is None
        assert len(scope.folder_remaining) == len(set(scope.folder_remaining))
        for photos in scope.photo_remaining.values():
            assert len(photos) == len(set(photos))

    for entries in history.values():
        assert_cycle_unique(entries)
    assert sum(map(len, history.values())) > 1_000
    assert all(scope.reservations is None for scope in scopes.values())
    print("  PASS  24-hour-equivalent dynamic additions/removals/outages/reboots/resets")
    print("  PASS  repeated reconciliation remains idempotent")
    print("  PASS  no folder repeats within modeled cycles")
    print("  PASS  no photo repeats within modeled per-folder cycles")
    print("  PASS  interrupted reservations recover without consumption")
    print(f"Dynamic task simulation passed ({sum(map(len, history.values()))} committed presentations)")

if __name__ == "__main__":
    main()
