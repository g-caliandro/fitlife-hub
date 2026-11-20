#!/usr/bin/env bash
# Install the debug APK (arm64) on a phone connected through Windows adb (from WSL2).
# Usage: ./install.sh          -> install the latest built APK
#        ./install.sh --build  -> build first (assembleDebug), then install
# Override the adb path with: ADB=/mnt/c/.../adb.exe ./install.sh
set -euo pipefail

ADB="${ADB:-/mnt/c/Users/$USER/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"

if [[ "${1:-}" == "--build" ]]; then
  (cd "$ROOT" && ./gradlew assembleDebug)
fi

[[ -f "$APK" ]] || { echo "APK not found: $APK (run with --build)"; exit 1; }

"$ADB" install -r "$(wslpath -w "$APK")"
