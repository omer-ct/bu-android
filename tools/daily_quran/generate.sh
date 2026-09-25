#!/usr/bin/env bash
# Generate Daily Quran JPEGs on a connected device and pull them here.
# Usage:
#   ./tools/daily_quran/generate.sh [fromSurah] [toSurah]
#   ./tools/daily_quran/generate.sh 2 2 282   # single ayah (longest: 2:282)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FROM="${1:-1}"
TO="${2:-$FROM}"
AYAH="${3:-}"
PKG="com.codefixr.beummati"
OUT="$ROOT/tools/daily_quran/out"
REMOTE_EXPORT="/sdcard/Android/data/$PKG/files/daily_quran_export"

echo "→ Building & installing debug…"
(cd "$ROOT" && ./gradlew :app:installDebug)

echo "→ Clearing previous export…"
adb shell "rm -rf '$REMOTE_EXPORT'" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

EXTRA=(--ei from "$FROM" --ei to "$TO")
if [[ -n "$AYAH" ]]; then
  EXTRA+=(--ei ayah "$AYAH")
  echo "→ Starting generator $FROM:$AYAH…"
else
  echo "→ Starting generator surah $FROM–$TO…"
fi
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -n "$PKG/.GenerateDailyQuranActivity" "${EXTRA[@]}"

echo "→ Waiting for export…"
for i in $(seq 1 900); do
  count=$(adb shell "ls '$REMOTE_EXPORT' 2>/dev/null | wc -l" | tr -d '[:space:]' || echo 0)
  done_line=$(adb logcat -d -s GenerateDailyQuran:I 2>/dev/null | grep "Done written=" | tail -1 || true)
  if [[ -n "$done_line" ]]; then
    echo "   $done_line"
    sleep 1
    break
  fi
  sleep 1
  if (( i % 20 == 0 )); then
    echo "   …still rendering (${i}s), files so far: ${count:-0}"
  fi
done

mkdir -p "$OUT"
echo "→ Pulling → $OUT"
adb pull "$REMOTE_EXPORT/." "$OUT/"
echo "✓ $(find "$OUT" -type f | wc -l | tr -d ' ') files in $OUT"
