#!/bin/bash
# Heap dumps during ACTIVE playback, over wireless adb.
SCRATCH="${OUT_DIR:-$(pwd)/heap-soak}"
OUT="$SCRATCH"
mkdir -p "$OUT"
PKG=com.example.familyphotoframe
D="${FRAME_ADB:?set FRAME_ADB, e.g. export FRAME_ADB=<frame-ip>:5555}"
CONV="$HOME/Android/Sdk/platform-tools/hprof-conv"

for i in $(seq 1 8); do
  ts=$(date +%H:%M)
  # record heap sample + cumulative slide count at this instant
  adb -s $D shell "run-as $PKG cat files/diagnostics/diagnostics.jsonl" 2>/dev/null \
    | grep HEAP_SAMPLE | tail -1 >> "$OUT/heap.jsonl"
  slides=$(adb -s $D shell "run-as $PKG cat files/diagnostics-slides/diagnostics.jsonl" 2>/dev/null \
    | grep -c SLIDE_SELECTED)
  echo "$ts dump=$i slides_cum=$slides" >> "$OUT/log.txt"

  adb -s $D shell "am dumpheap $PKG /data/local/tmp/p$i.hprof" >/dev/null 2>&1
  sleep 20
  if adb -s $D pull "/data/local/tmp/p$i.hprof" "$OUT/p$i.raw" >/dev/null 2>&1; then
    "$CONV" "$OUT/p$i.raw" "$OUT/p$i.hprof" >/dev/null 2>&1 && rm -f "$OUT/p$i.raw"
    echo "  dump $i ok $(stat -c%s "$OUT/p$i.hprof" 2>/dev/null)" >> "$OUT/log.txt"
  else
    echo "  dump $i FAILED" >> "$OUT/log.txt"
  fi
  adb -s $D shell "rm -f /data/local/tmp/p$i.hprof" >/dev/null 2>&1
  sleep 1200
done
echo "$(date +%H:%M) play-watch complete" >> "$OUT/log.txt"
