#!/usr/bin/env python3
"""
Structural checks the Kotlin *parser* does not catch.

`scripts/verify-pure-logic.sh --syntax-only` parses every source file, but parsing only
proves a file is grammatically valid Kotlin — not that it means what was intended. A
statement accidentally joined to the end of the previous one, e.g.

    Text(
        "label",
    )    Column(...) {

still parses (the compiler reads `Column` as part of the preceding expression) and is only
rejected later by the type checker. On a machine that cannot run the Android/Compose
compiler that error stays invisible until a real Gradle build.

This script targets that specific failure mode, which is easy to introduce with scripted
line edits and hard to spot by eye.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIRS = ["app/src/main/java", "app/src/test/java", "app/src/androidTest/java"]

# `)` or `}` followed on the same line by something that starts a new statement:
# an identifier beginning with a capital (a composable or constructor call), or a
# keyword that can only begin a statement.
JOINED = re.compile(r"[)\}]\s{2,}(?:[A-Z][A-Za-z0-9_]*\s*[({]|val\s|var\s|return\s|if\s*\()")

# Continuations that legitimately follow a closing bracket on the same line.
ALLOWED_PREFIX = re.compile(r"^\s*(\}|\)|//|/\*|\*)")


def strip_strings_and_comments(line: str) -> str:
    """Blank out string literals so their contents cannot trigger a match."""
    out = re.sub(r'"""', "", line)
    out = re.sub(r'"(?:\\.|[^"\\])*"', '""', out)
    out = re.sub(r"'(?:\\.|[^'\\])*'", "''", out)
    out = re.sub(r"//.*$", "", out)
    return out


def main() -> int:
    problems: list[str] = []
    checked = 0

    for source_dir in SOURCE_DIRS:
        base = ROOT / source_dir
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.kt")):
            checked += 1
            text = path.read_text(encoding="utf-8", errors="replace")
            in_block_comment = False
            for number, raw in enumerate(text.splitlines(), start=1):
                stripped = raw.strip()
                if in_block_comment:
                    if "*/" in stripped:
                        in_block_comment = False
                    continue
                if stripped.startswith("/*"):
                    if "*/" not in stripped:
                        in_block_comment = True
                    continue
                if ALLOWED_PREFIX.match(raw) and not raw.lstrip().startswith(")"):
                    continue
                line = strip_strings_and_comments(raw)
                if JOINED.search(line):
                    rel = path.relative_to(ROOT)
                    problems.append(f"  {rel}:{number}: statement joined to previous line\n      {stripped[:110]}")

            # Whole-file bracket balance. A scripted edit that eats a brace usually still
            # parses inside one function but unbalances the file.
            body = re.sub(r'"""[\s\S]*?"""', '""', text)
            body = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', body)
            body = re.sub(r"//[^\n]*", "", body)
            body = re.sub(r"/\*[\s\S]*?\*/", "", body)
            if body.count("{") != body.count("}"):
                rel = path.relative_to(ROOT)
                problems.append(
                    f"  {rel}: unbalanced braces ({body.count('{')} open, {body.count('}')} close)"
                )

    if problems:
        print("STRUCTURE CHECK FAILED")
        for problem in problems:
            print(problem)
        return 1

    print(f"  note: {checked} Kotlin file(s) checked for joined statements and brace balance")
    print("  all Kotlin structure checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
