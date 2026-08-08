#!/usr/bin/env python3
"""Validate every Room @Query against a real SQLite engine.

Room's annotation processor is the only thing that normally checks these SQL strings,
and it cannot run here: it comes from dl.google.com, which this environment blocks. That
left every `@Query` in the project completely unverified -- a typo, an unknown column, or
an `IN ()` that SQLite cannot parse would all have survived until someone ran Gradle.

Python ships SQLite, so the queries can be checked directly. This script derives a schema
from the `@Entity` declarations the same way Room does, then asks SQLite to *prepare*
each query. Preparation is the useful step: it resolves table and column names and parses
the syntax, without needing any data.

What this does NOT check: Room's own extra rules (return-type/projection agreement,
`@Insert`/`@Update` correctness), and whether the entity schema matches the migration
chain -- `verify-migrations.py` covers the latter.

Exit code 1 on any failure.
"""
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_DIR = os.path.join(ROOT, "app/src/main/java/com/example/familyphotoframe/data/db")

# Kotlin -> SQLite affinity, mirroring Room's mapping.
TYPES = {
    "Long": "INTEGER", "Int": "INTEGER", "Boolean": "INTEGER",
    "String": "TEXT", "Double": "REAL", "Float": "REAL", "ByteArray": "BLOB",
}

failures = []
notes = []


def kotlin_sources():
    for name in sorted(os.listdir(DB_DIR)):
        if name.endswith(".kt"):
            yield os.path.join(DB_DIR, name)


def parse_entities():
    """Return {table: [(column, sql_type, not_null)]} from the @Entity data classes."""
    tables = {}
    for path in kotlin_sources():
        src = open(path, encoding="utf-8").read()
        # Each @Entity(...) is followed by its data class; scan annotation then body.
        for m in re.finditer(r"@Entity\s*\((.*?)\)\s*data class\s+(\w+)\s*\((.*?)\n\)",
                             src, re.S):
            ann, cls, body = m.group(1), m.group(2), m.group(3)
            tm = re.search(r'tableName\s*=\s*"([^"]+)"', ann)
            table = tm.group(1) if tm else cls
            tables[table] = parse_columns(body)
        # @Entity with no arguments.
        for m in re.finditer(r"@Entity\s*\n\s*data class\s+(\w+)\s*\((.*?)\n\)", src, re.S):
            tables.setdefault(m.group(1), parse_columns(m.group(2)))
    return tables


def parse_columns(body):
    cols = []
    # Strip comments so commented-out properties are not mistaken for columns.
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    for line in body.split("\n"):
        line = line.strip()
        if not line or line.startswith("@Entity"):
            continue
        m = re.search(r"val\s+(\w+)\s*:\s*([\w<>?]+)", line)
        if not m:
            continue
        name, ktype = m.group(1), m.group(2)
        nullable = ktype.endswith("?")
        base = ktype.rstrip("?")
        sql_type = TYPES.get(base, "TEXT")
        # Honour an explicit @ColumnInfo(name = "...") rename.
        cm = re.search(r'ColumnInfo\([^)]*name\s*=\s*"([^"]+)"', line)
        if cm:
            name = cm.group(1)
        cols.append((name, sql_type, not nullable))
    return cols


def build_db(tables):
    con = sqlite3.connect(":memory:")
    for table, cols in tables.items():
        if not cols:
            continue
        defs = []
        for name, sql_type, not_null in cols:
            defs.append('"%s" %s%s' % (name, sql_type, " NOT NULL" if not_null else ""))
        con.execute('CREATE TABLE IF NOT EXISTS "%s" (%s)' % (table, ", ".join(defs)))
    return con


def extract_queries():
    """Yield (file, line, sql) for every @Query in the db package."""
    for path in kotlin_sources():
        src = open(path, encoding="utf-8").read()
        for m in re.finditer(r'@Query\(\s*"""(.*?)"""\s*\)', src, re.S):
            yield path, src[:m.start()].count("\n") + 1, m.group(1)
        for m in re.finditer(r'@Query\(\s*"((?:[^"\\]|\\.)*)"\s*\)', src):
            yield path, src[:m.start()].count("\n") + 1, m.group(1)


def normalize(sql):
    """Turn a Room query into something SQLite can prepare.

    Room expands a collection parameter into a parenthesised list at runtime, so
    `IN (:ids)` is rewritten here to a single placeholder. That substitution is exactly
    why an empty collection is dangerous -- it would produce `IN ()`, which SQLite
    rejects -- and is the reason the folder-filter queries carry a companion flag and a
    sentinel value rather than an empty list.
    """
    sql = sql.replace("\\n", " ").replace('\\"', '"')
    sql = re.sub(r"IN\s*\(\s*:(\w+)\s*\)", "IN (?)", sql, flags=re.I)
    sql = re.sub(r":\w+", "?", sql)
    return sql.strip()


def main():
    tables = parse_entities()
    if not tables:
        print("  no @Entity declarations found -- cannot validate SQL")
        return 1
    con = build_db(tables)
    notes.append("schema: " + ", ".join(
        "%s(%d cols)" % (t, len(c)) for t, c in sorted(tables.items()) if c))

    checked = 0
    for path, line, raw in extract_queries():
        sql = normalize(raw)
        if not sql:
            continue
        try:
            # EXPLAIN compiles the statement without running it, but SQLite still wants
            # a binding per placeholder; the values are irrelevant since nothing executes.
            con.execute("EXPLAIN " + sql, (None,) * sql.count("?"))
            checked += 1
        except sqlite3.Error as e:
            failures.append("%s:%d %s\n      query: %s" % (
                os.path.basename(path), line, e, " ".join(sql.split())[:160]))

    notes.append("queries: %d prepared successfully against SQLite" % checked)

    for n in notes:
        print("  note: %s" % n)
    if failures:
        print("\nSQL VALIDATION FAILED (%d):" % len(failures))
        for f in failures:
            print("  - %s" % f)
        return 1
    print("  all SQL checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
