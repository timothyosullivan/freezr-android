#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.freezr.debug"
MAIN_ACTIVITY="com.freezr.MainActivity"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
GRADLE_CMD="./gradlew"

usage() {
  cat <<EOF
Usage: tools/validate.sh [--mark]

Steps:
 1. Assemble debug
 2. Install on first connected emulator/device
 3. Launch main activity
 4. (Optional) Touch .validated when --mark provided

Environment:
  ANDROID_HOME may override default sdk path.
EOF
}

MARK=0
if [[ ${1:-} == "--help" ]]; then usage; exit 0; fi
if [[ ${1:-} == "--mark" ]]; then MARK=1; fi

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "ADB not found at $ADB" >&2; exit 1
fi

echo "[1/4] Building :app:assembleDebug"; $GRADLE_CMD :app:assembleDebug -q

echo "[2/4] Installing"; $GRADLE_CMD :app:installDebug -q

echo "[3/4] Launching $APP_ID/$MAIN_ACTIVITY"; "$ADB" shell am start -n "$APP_ID/$MAIN_ACTIVITY" >/dev/null || {
  echo "Launch failed" >&2; exit 1; }

# Wait for process (some devices/emulators delay spawn)
ATTEMPTS=10
PID=""
for i in $(seq 1 $ATTEMPTS); do
  PID=$("$ADB" shell pidof $APP_ID || true)
  if [[ -n "$PID" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -z "$PID" ]]; then
  echo "App process not detected after $((ATTEMPTS*500))ms" >&2
  # Show last 30 log lines mentioning package for diagnostics
  "$ADB" logcat -d | grep -i "$APP_ID" | tail -n 30 || true
  exit 1
fi
echo "Running PID: $PID"

if [[ $MARK -eq 1 ]]; then
  rm -f NEEDS_EMULATOR_CHECK
  touch .validated
  echo ".validated written"
fi

echo "Validation complete."
