#!/usr/bin/env bash
# Installa l'APK debug (arm64) sul telefono collegato via adb di Windows.
# Uso: ./install.sh          -> installa l'ultimo APK già buildato
#      ./install.sh --build  -> builda prima (assembleDebug), poi installa
set -euo pipefail

ADB="/mnt/c/Users/gabri/AppData/Local/Android/Sdk/platform-tools/adb.exe"
ROOT="$(cd "$(dirname "$0")" && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"

if [[ "${1:-}" == "--build" ]]; then
  (cd "$ROOT" && ./gradlew assembleDebug)
fi

[[ -f "$APK" ]] || { echo "APK non trovato: $APK (lancia con --build)"; exit 1; }

"$ADB" install -r "$(wslpath -w "$APK")"
