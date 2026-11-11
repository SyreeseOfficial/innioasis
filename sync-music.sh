#!/bin/bash
# Copy ~/Music to the Y1 SD card, skipping files already there with same size. Safe to re-run/resume.
SRC=~/Music; DST=/storage/sdcard1/Music
q() { printf "'%s'" "${1//\'/\'\\\'\'}"; }  # single-quote for the device shell
cd "$SRC" || exit 1
find . -type f \( -iname '*.flac' -o -iname '*.mp3' -o -iname '*.m4a' \) -print0 | sort -z > /tmp/y1-files.lst
while IFS= read -r -d '' f <&3; do
  f=${f#./}; sz=$(stat -c %s "$f")
  rs=$(adb shell "stat -c %s $(q "$DST/$f") 2>/dev/null" </dev/null | tr -d '\r')
  [ "$rs" = "$sz" ] && continue
  adb shell "mkdir -p $(q "$DST/$(dirname "$f")")" </dev/null
  adb push "$f" "$DST/$f" >/dev/null 2>&1 </dev/null || { echo "FAIL $f"; sleep 5; }
  echo "$(date +%T) $f"
done 3< /tmp/y1-files.lst
echo SYNC_DONE
