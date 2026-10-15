#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android platform-tools and ensure adb is on PATH." >&2
  exit 1
fi

DEVICE_SERIAL="${DEVICE_SERIAL:-}"

if [ -z "$DEVICE_SERIAL" ]; then
  DEVICE_SERIAL="$(adb devices -l | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
fi

if [ -z "$DEVICE_SERIAL" ]; then
  echo "No physical device detected. Ensure USB debugging is enabled and the device is connected." >&2
  adb devices
  exit 1
fi

./gradlew :app:installDebug
adb -s "$DEVICE_SERIAL" shell am start -n com.freeup.textactions/.MainActivity
