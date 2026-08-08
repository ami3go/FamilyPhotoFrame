#!/usr/bin/env python3
"""Execute the real migration chain against SQLite and compare the result to the entities.

A wrong migration is the worst failure this app can ship: it does not fail in review or
on a fresh install, only on an *existing user's* device, at launch, after an update --
and Room's response is to throw rather than open the database.

The existing consistency check compares column *names* textually. This goes further: it
runs every `execSQL` from `Migrations.kt` in order against a real SQLite engine, starting
from a reconstructed v1 schema, and then diffs the resulting tables and columns against
what the `@Entity` classes declare. That catches invalid migration SQL, a column added
with the wrong type, and -- most importantly -- an entity column that no migration ever
adds.

Limits: index names and NOT NULL/DEFAULT details are compared only loosely, and Room's
own schema hash is not reproduced. A real `./gradlew` run and an upgrade test on a device
remain the authority.
"""
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_DIR = os.path.join(ROOT, "app/src/main/java/com/example/familyphotoframe/data/db")
MIGRATIONS = os.path.join(DB_DIR, "Migrations.kt")


TYPES = {
    "Long": "INTEGER", "Int": "INTEGER", "Boolean": "INTEGER",
    "String": "TEXT", "Double": "REAL", "Float": "REAL", "ByteArray": "BLOB",
}

failures = []
notes = []

# The v1 (Phase 0) `photos` table, pinned as a literal.
#
# This deliberately is NOT derived from the current entity. An earlier version of this
# script built v1 as "entity columns minus those a migration adds", which made the check
# useless for its single most important case: a newly added entity column with no
# migration was simply absorbed into the reconstructed v1 and never reported. Verified by
# adding such a column and watching the check pass.
#
# v1 shipped; its shape is history and must never be edited to make a check go green.
V1_PHOTOS = [
    "id", "stableId", "sourceId", "normalizedPath", "folderName", "fileName",
    "mimeType", "sizeBytes", "fileModifiedEpochMs", "openToken", "indexedAtEpochMs",
    "isHidden", "lastShownAtEpochMs", "decodeFailureCount", "lastDecodeFailureAtEpochMs",
]


def parse_entities():
    """{table: {column: sql_type}} from the @Entity data classes."""
    tables = {}
    for name in sorted(os.listdir(DB_DIR)):
        if not name.endswith(".kt"):
            continue
        src = open(os.path.join(DB_DIR, name), encoding="utf-8").read()
        for m in re.finditer(r"@Entity\s*\((.*?)\)\s*data class\s+(\w+)\s*\((.*?)\n\)",
                             src, re.S):
            ann, cls, body = m.group(1), m.group(2), m.group(3)
            tm = re.search(r'tableName\s*=\s*"([^"]+)"', ann)
            tables[tm.group(1) if tm else cls] = parse_columns(body)
    return tables


def parse_columns(body):
    cols = {}
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    for line in body.split("\n"):
        m = re.search(r"val\s+(\w+)\s*:\s*([\w<>?]+)", line.strip())
        if not m:
            continue
        name, ktype = m.group(1), m.group(2)
        cm = re.search(r'ColumnInfo\([^)]*name\s*=\s*"([^"]+)"', line)
        if cm:
            name = cm.group(1)
        cols[name] = TYPES.get(ktype.rstrip("?"), "TEXT")
    return cols


def migration_statements():
    """[(from, to, [sql, ...])] in declared order."""
    src = open(MIGRATIONS, encoding="utf-8").read()
    out = []
    blocks = list(re.finditer(r"Migration\((\d+),\s*(\d+)\)", src))
    for i, m in enumerate(blocks):
        start = m.end()
        end = blocks[i + 1].start() if i + 1 < len(blocks) else len(src)
        body = src[start:end]
        # Collect both string forms with their positions, then replay in source order --
        # a CREATE TABLE must run before the CREATE INDEX that references it.
        found = []
        for sm in re.finditer(
                r'execSQL\(\s*"""(.*?)"""(?:\.trimIndent\(\))?\s*,?\s*\)', body, re.S):
            found.append((sm.start(), sm.group(1)))
        for sm in re.finditer(r'execSQL\(\s*"((?:[^"\\]|\\.)*)"\s*\)', body):
            found.append((sm.start(), sm.group(1)))
        found.sort()
        out.append((int(m.group(1)), int(m.group(2)), [f[1] for f in found]))
    return out


def added_columns(chain):
    """{table: {column}} added by ALTER TABLE across the whole chain."""
    added = {}
    for _, _, stmts in chain:
        for s in stmts:
            m = re.search(r"ALTER TABLE\s+`?(\w+)`?\s+ADD COLUMN\s+`?(\w+)`?", s, re.I)
            if m:
                added.setdefault(m.group(1), set()).add(m.group(2))
    return added


def main():
    entities = parse_entities()
    chain = migration_statements()
    if not entities or not chain:
        print("  could not parse entities or migrations")
        return 1

    # Verify the chain is contiguous; a gap means some upgrade path has no route.
    versions = [(f, t) for f, t, _ in chain]
    for i in range(len(versions) - 1):
        if versions[i][1] != versions[i + 1][0]:
            failures.append("migration chain is not contiguous: %s then %s"
                            % (versions[i], versions[i + 1]))

    con = sqlite3.connect(":memory:")
    photos = entities.get("photos", {})
    missing_from_entity = [c for c in V1_PHOTOS if c not in photos]
    if missing_from_entity:
        failures.append("v1 column(s) %s no longer exist on the entity; a column cannot be "
                        "dropped without a rebuild migration" % ", ".join(missing_from_entity))
    con.execute('CREATE TABLE "photos" (%s)' % ", ".join(
        '"%s" %s' % (c, photos.get(c, "TEXT")) for c in V1_PHOTOS))
    notes.append("v1 photos table pinned at %d columns" % len(V1_PHOTOS))

    # Replay every migration statement in order.
    applied = 0
    for frm, to, stmts in chain:
        for s in stmts:
            sql = s.replace("\\n", " ").strip()
            if not sql:
                continue
            try:
                con.execute(sql)
                applied += 1
            except sqlite3.Error as e:
                failures.append("migration %d->%d failed: %s\n      %s"
                                % (frm, to, e, " ".join(sql.split())[:150]))
    notes.append("applied %d migration statements across %d migrations" % (applied, len(chain)))

    # Compare the migrated database with what the entities declare.
    real = {}
    for (name,) in con.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"):
        real[name] = {r[1]: r[2].upper() for r in con.execute('PRAGMA table_info("%s")' % name)}

    for table, cols in sorted(entities.items()):
        if table not in real:
            failures.append("entity table '%s' is never created by any migration "
                            "-- an upgrading install would crash" % table)
            continue
        for col, ktype in sorted(cols.items()):
            if col not in real[table]:
                failures.append("%s.%s exists on the entity but no migration adds it "
                                "-- an upgrading install would crash" % (table, col))
            elif real[table][col] and real[table][col] != ktype:
                failures.append("%s.%s type mismatch: entity says %s, migration created %s"
                                % (table, col, ktype, real[table][col]))
        extra = set(real[table]) - set(cols)
        if extra:
            failures.append("%s has columns the entity does not declare: %s"
                            % (table, ", ".join(sorted(extra))))

    notes.append("verified %d tables against the migrated schema" % len(entities))

    for n in notes:
        print("  note: %s" % n)
    if failures:
        print("\nMIGRATION VALIDATION FAILED (%d):" % len(failures))
        for f in failures:
            print("  - %s" % f)
        return 1
    print("  all migration checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
