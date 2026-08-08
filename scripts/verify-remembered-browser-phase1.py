#!/usr/bin/env python3
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def has(path, *needles):
    text=(root/path).read_text()
    for n in needles:
        checks.append((f"{path}: {n}", n in text))

has(Path('app/src/main/java/com/example/familyphotoframe/data/db/AppDatabase.kt'),
    'RememberedBrowserEntity::class', 'version = 8', 'rememberedBrowserDao()', 'MIGRATION_5_6', 'MIGRATION_6_7')
has(Path('app/src/main/java/com/example/familyphotoframe/data/db/Migrations.kt'),
    'CREATE TABLE IF NOT EXISTS `remembered_browsers`', '`currentTokenHash` BLOB NOT NULL', '`retiredTokenHash` BLOB')
has(Path('app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt'),
    'enum class RememberExpiryMode', 'data class RememberedBrowserPolicy', 'val rememberedBrowsers: RememberedBrowserPolicy')
has(Path('app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserManager.kt'),
    'HmacSHA256', 'REMEMBERED_BROWSER_KEY_LOST', 'CLOCK_ROLLBACK_TOLERANCE_MS', 'retiredTokenHash')
has(Path('app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserExpiry.kt'),
    'Calendar.MONTH', 'Calendar.YEAR', 'MIN_CUSTOM_MS', 'HARD_MAX_MS')
failed=[name for name,ok in checks if not ok]
if failed:
    print('PHASE 1 FAILED')
    for f in failed: print(' -',f)
    sys.exit(1)
print(f'Phase 1 remembered-browser foundation passed ({len(checks)} checks)')
