#!/usr/bin/env python3
"""
Static cross-file consistency checks.

Why this exists: a full ./gradlew build is unavailable offline (see BUILD_NOTES.md), and
`verify-pure-logic.sh` only catches *parse* errors plus pure-logic bugs. Between those two
sits a class of mistake that is invisible to both, easy to make when editing many files,
and expensive to find late:

  - an `R.string.x` with no matching entry in strings.xml  -> compile error
  - a `vm::foo` callback with no such ViewModel function   -> compile error
  - a Room entity column with no migration                 -> crash on launch after upgrade
  - a gap in the migration chain                           -> crash on launch after upgrade

None of these need the Android SDK to detect. This is not a substitute for a real build;
it is the cheapest possible guard on the invariants most likely to be broken silently.

Usage:  python3 scripts/check-consistency.py     (exit 1 on failure)
"""
import glob
import os
import re
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/main")
KT = glob.glob(os.path.join(MAIN, "java/**/*.kt"), recursive=True)

failures = []
notes = []


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def check_resources():
    xml = read(os.path.join(MAIN, "res/values/strings.xml"))
    string_names = re.findall(r'<string name="([^"]+)"', xml)
    array_names = set(re.findall(r'<string-array name="([^"]+)"', xml))
    names = set(string_names)

    for name, count in Counter(string_names).items():
        if count > 1:
            failures.append("duplicate string resource: %s (x%d)" % (name, count))

    used = set()
    for path in KT:
        text = read(path)
        base = os.path.basename(path)
        for m in re.findall(r"R\.string\.([A-Za-z0-9_]+)", text):
            used.add(m)
            if m not in names:
                failures.append("R.string.%s referenced in %s but not defined" % (m, base))
        for m in re.findall(r"R\.array\.([A-Za-z0-9_]+)", text):
            if m not in array_names:
                failures.append("R.array.%s referenced in %s but not defined" % (m, base))

    unused = sorted(names - used)
    if unused:
        notes.append("%d string resource(s) defined but unreferenced in Kotlin "
                     "(may be used from XML/menus): %s" % (len(unused), ", ".join(unused[:8])))


def check_viewmodel_callbacks():
    vm_path = os.path.join(MAIN, "java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
    vm_funs = set(re.findall(r"\bfun\s+([A-Za-z0-9_]+)\s*\(", read(vm_path)))
    for path in glob.glob(os.path.join(MAIN, "java/com/example/familyphotoframe/ui/**/*.kt"), recursive=True):
        text = read(path)
        base = os.path.basename(path)
        for m in re.findall(r"vm::([A-Za-z0-9_]+)", text):
            if m not in vm_funs:
                failures.append("vm::%s used in %s but SlideshowViewModel has no such function" % (m, base))


def check_room_schema():
    """Every entity column must be reachable by an upgrading install, and the migration
    chain must be contiguous up to the declared database version."""
    ent = read(os.path.join(MAIN, "java/com/example/familyphotoframe/data/db/PhotoItemEntity.kt"))
    body = ent[ent.index("data class PhotoItemEntity"):]
    fields = re.findall(r"val\s+([A-Za-z0-9_]+)\s*:", body)

    mig = read(os.path.join(MAIN, "java/com/example/familyphotoframe/data/db/Migrations.kt"))
    altered = set(re.findall(r"ALTER TABLE `photos` ADD COLUMN `([A-Za-z0-9_]+)`", mig))
    defined = set(re.findall(r"val (MIGRATION_(\d+)_(\d+))", mig))
    pairs = sorted((int(a), int(b)) for _, a, b in defined)

    db = read(os.path.join(MAIN, "java/com/example/familyphotoframe/data/db/AppDatabase.kt"))
    version = int(re.search(r"version\s*=\s*(\d+)", db).group(1))
    registered = set(re.findall(r"MIGRATION_\d+_\d+", re.search(r"addMigrations\(([^)]*)\)", db).group(1)))

    # Contiguous 1..version chain.
    expected = [(v, v + 1) for v in range(1, version)]
    for step in expected:
        if step not in pairs:
            failures.append("no MIGRATION_%d_%d defined, but database version is %d" % (step[0], step[1], version))
    for name, a, b in defined:
        if name not in registered:
            failures.append("%s is defined but not passed to addMigrations()" % name)

    # Columns present in the v1 baseline are fine; anything added later needs an ALTER.
    # We can't see the v1 baseline directly, so flag only columns we know were added
    # after v1 by checking the entity's own "v_ (…) additions" comment markers.
    later = re.findall(r"//\s*----\s*v(\d+).*?----(.*?)(?=//\s*----|\Z)", body, re.S)
    for ver, chunk in later:
        for col in re.findall(r"val\s+([A-Za-z0-9_]+)\s*:", chunk):
            if col not in altered:
                failures.append(
                    "PhotoItemEntity.%s is marked as a v%s addition but no migration "
                    "adds that column — an upgrading install would crash" % (col, ver))

    notes.append("Room: db version %d, migrations %s, %d entity columns, %d added by migration"
                 % (version, ",".join("%d->%d" % p for p in pairs), len(fields), len(altered)))


def has_top_level_else(block):
    """True if `else ->` appears at brace depth 0 within `block`.

    Depth-aware on purpose: a `when` whose branches contain their own `when ... else`
    is still non-exhaustive itself, and treating the inner `else` as the outer one hides
    precisely the bug this check exists to find.
    """
    depth = 0
    i = 0
    while i < len(block):
        c = block[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        elif depth == 0 and block.startswith("else", i):
            before_ok = i == 0 or not (block[i - 1].isalnum() or block[i - 1] == "_")
            rest = block[i + 4:]
            if before_ok and re.match(r"\s*->", rest):
                return True
        i += 1
    return False


def check_exhaustive_when():
    """Non-exhaustive `when` over an enum used as an expression is a compile error, but
    only at the site of use — adding an enum case does not by itself surface it. This
    caught v34 too late (in a real build on the user's machine); it should catch the
    next one here.

    Reports a `when` block that references two or more values of an enum by name and does
    not have an `else`, and is missing at least one value. Two-or-more is the heuristic
    for "actually a switch over this enum," so an unrelated `when` that happens to name
    one enum constant in a branch does not produce noise."""
    def enum_values(path, enum_name):
        text = re.sub(r"//.*", "", read(path))
        match = re.search(
            r"enum\s+class\s+%s(?:\s*\([^)]*\))?\s*\{([^}]*)\}" % enum_name,
            text,
            re.S,
        )
        if not match:
            failures.append("could not parse enum %s from %s" % (enum_name, os.path.basename(path)))
            return []
        values = []
        for part in match.group(1).split(","):
            name = re.match(r"\s*([A-Z][A-Z0-9_]*)", part)
            if name:
                values.append(name.group(1))
        return values

    enums = {
        "ActiveSourceKind": enum_values(
            os.path.join(MAIN, "java/com/example/familyphotoframe/data/settings/AppSettings.kt"),
            "ActiveSourceKind",
        ),
        "SourceType": enum_values(
            os.path.join(MAIN, "java/com/example/familyphotoframe/data/source/PhotoSource.kt"),
            "SourceType",
        ),
    }
    for path in KT:
        text = read(path)
        base = os.path.basename(path)
        for m in re.finditer(r"when\s*\([^)]+\)\s*\{", text):
            start = m.end()
            depth = 1
            i = start
            while i < len(text) and depth:
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                i += 1
            block = text[start:i - 1]
            # `else` must be found at the *top* level of this block. A nested `when` with
            # its own `else ->` used to satisfy this test and silence the check -- exactly
            # how the missing WEBDAV branch in testSavedSource() reached a real build.
            if has_top_level_else(block):
                continue
            for enum, values in enums.items():
                hits = [v for v in values if re.search(rf"\b{enum}\.{v}\b", block)]
                if len(hits) < 2:
                    continue
                missing = [v for v in values if v not in hits]
                if missing:
                    line = text[:m.start()].count("\n") + 1
                    failures.append(
                        "%s:%d — non-exhaustive `when` over %s (missing: %s). "
                        "Add the branch or an `else`." % (base, line, enum, ", ".join(missing))
                    )
                break


def check_capabilities():
    """Verify every row of CAPABILITIES.md still points at real code.

    Narrative docs in this repo have repeatedly claimed shipped features were missing.
    A table that the build actually verifies cannot drift the same way.
    """
    man = os.path.join(ROOT, "CAPABILITIES.md")
    if not os.path.exists(man):
        notes.append("CAPABILITIES.md not present; skipping capability check")
        return
    text = open(man, encoding="utf-8").read()
    body = text.split("<!-- CAPABILITIES:BEGIN -->")[-1].split("<!-- CAPABILITIES:END -->")[0]
    rows = 0
    for line in body.splitlines():
        line = line.strip()
        if not line.startswith("|") or line.startswith("| ---") or line.startswith("| Capability"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) < 2:
            continue
        name, evidence = cells[0], cells[1].strip("`")
        if "::" in evidence:
            path, symbol = evidence.split("::", 1)
        else:
            path, symbol = evidence, None
        full = os.path.join(ROOT, path)
        if not os.path.exists(full):
            failures.append("capability '%s' claims %s but that file does not exist" % (name, path))
            continue
        if symbol:
            src = open(full, encoding="utf-8", errors="replace").read()
            if symbol not in src:
                failures.append("capability '%s' claims %s::%s but that symbol is absent"
                                % (name, path, symbol))
        rows += 1
    notes.append("capabilities: %d verified against source" % rows)



def check_compose_scope_imports():
    """Catch imports of Compose scope-member modifiers as top-level symbols.

    With the Compose version used by this project, RowScope.weight is resolved from
    the Row receiver. Importing androidx.compose.foundation.layout.weight can bind to
    an internal parent-data property and fail Kotlin compilation.
    """
    forbidden = {
        "import androidx.compose.foundation.layout.weight":
            "Remove the import; call Modifier.weight(...) inside a Row/Column scope.",
    }
    for path in KT:
        text = read(path)
        for line, advice in forbidden.items():
            if line in text:
                failures.append("%s contains forbidden Compose import `%s`. %s" %
                                (os.path.basename(path), line, advice))


def check_source_refresh_wiring():
    """Guard the Synology -> SMB rebuild regression.

    Rebuild used to call onSettings(lastSettings), which could still contain the failed
    Synology source while DataStore already held the newly saved SMB configuration.
    Source I/O also ran inline in the settings collector, preventing that newer value
    from being observed until the old health check returned.
    """
    path = os.path.join(
        MAIN,
        "java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt",
    )
    text = read(path)
    rebuild = re.search(r"fun rebuildIndex\(\)\s*\{(.*?)\n    \}", text, re.S)
    if not rebuild:
        failures.append("could not locate SlideshowViewModel.rebuildIndex()")
    else:
        body = rebuild.group(1)
        if "requestSourceRefresh(" not in body or "REBUILD_ANDROID_UI" not in body:
            failures.append("rebuildIndex() must refresh from current persisted settings")
        if "currentSettingsSnapshot" in body or "onSettings(" in body:
            failures.append("rebuildIndex() must not re-apply a stale UI settings snapshot")

    required = [
        "services.settings.settings.first()",
        ".collectLatest(::applySourceRequest)",
        "SourceRuntimeSignature.of(settings)",
        "cancelObsoleteIndexWork(request.operation.operationId)",
    ]
    for marker in required:
        if marker not in text:
            failures.append("source refresh coordinator is missing `%s`" % marker)
    if "appliedSourceSig" in text:
        failures.append("legacy appliedSourceSig gate can reintroduce stale-source rebuilds")

def main():
    check_resources()
    check_compose_scope_imports()
    check_viewmodel_callbacks()
    check_room_schema()
    check_exhaustive_when()
    check_capabilities()
    check_source_refresh_wiring()

    for n in notes:
        print("  note: %s" % n)
    if failures:
        print("\nFAILED (%d):" % len(failures))
        for f in failures:
            print("  - %s" % f)
        return 1
    print("  all consistency checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
