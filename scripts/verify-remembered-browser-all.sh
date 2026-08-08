#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/verify-remembered-browser-phase1.py
./scripts/verify-remembered-browser-phase2.sh
python3 scripts/verify-remembered-browser-phase3.py
python3 scripts/verify-remembered-browser-phase4.py
./scripts/verify-remembered-browser-phase5.sh
python3 scripts/check-consistency.py
python3 scripts/verify-sql.py
python3 scripts/verify-migrations.py
python3 scripts/verify-web-ui-modernisation.py
python3 scripts/verify-fullscreen-settings-back.py
printf '%s\n' 'All remembered-browser phase gates passed'
