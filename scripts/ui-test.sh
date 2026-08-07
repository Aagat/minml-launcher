#!/usr/bin/env bash
set -uo pipefail

PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$PROJECT_ROOT"

AVD_NAME=${AVD_NAME:-medium_phone}
KEEP_AVD=${KEEP_AVD:-0}
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
REPORT_DIR="$PROJECT_ROOT/app/build/reports/launcher-ui"
TEMP_DIR=$(mktemp -d -t minml-launcher-ui.XXXXXX)
STARTED_EMULATOR=0
SERIAL=${ANDROID_SERIAL:-}
START_TIME=$(date +%s)

if command -v flock >/dev/null 2>&1; then
    exec 9>"${TMPDIR:-/tmp}/minml-launcher-ui-test.lock"
    if ! flock -n 9; then
        echo "Another launcher UI test run is already active." >&2
        exit 2
    fi
fi

cleanup() {
    if [[ "$STARTED_EMULATOR" == 1 && "$KEEP_AVD" != 1 && -n "$SERIAL" ]]; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    fi
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT INT TERM

if [[ ! -x "$ADB" ]]; then
    echo "adb was not found under $ANDROID_SDK_ROOT" >&2
    exit 2
fi

if [[ -z "$SERIAL" ]]; then
    SERIAL=$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')
fi

if [[ -z "$SERIAL" ]]; then
    if [[ ! -x "$EMULATOR" ]]; then
        echo "No device is connected and the emulator was not found under $ANDROID_SDK_ROOT" >&2
        exit 2
    fi
    "$EMULATOR" "@$AVD_NAME" -gpu swangle -no-window -no-audio -no-boot-anim -no-snapshot-save >"$TEMP_DIR/emulator.log" 2>&1 &
    STARTED_EMULATOR=1
    for _ in $(seq 1 90); do
        SERIAL=$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')
        [[ -n "$SERIAL" ]] && break
        sleep 1
    done
fi

if [[ -z "$SERIAL" ]]; then
    echo "No emulator became available." >&2
    exit 2
fi

for _ in $(seq 1 120); do
    BOOTED=$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [[ "$BOOTED" == 1 ]] && break
    sleep 1
done
if [[ "${BOOTED:-}" != 1 ]]; then
    echo "Emulator $SERIAL did not finish booting." >&2
    exit 2
fi

"$ADB" -s "$SERIAL" shell input keyevent WAKEUP >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell svc power stayon true >/dev/null 2>&1 || true

set +e
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest >"$TEMP_DIR/build.log" 2>&1
BUILD_EXIT=$?
set -e

UI_EXIT=1
if [[ "$BUILD_EXIT" == 0 ]]; then
    set +e
    "$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk >"$TEMP_DIR/install.log" 2>&1
    INSTALL_EXIT=$?
    if [[ "$INSTALL_EXIT" == 0 ]]; then
        "$ADB" -s "$SERIAL" shell cmd role add-role-holder android.app.role.HOME dev.obvious.minimallauncher >>"$TEMP_DIR/install.log" 2>&1 || true
        "$ADB" -s "$SERIAL" shell rm -rf /sdcard/Download/minml-launcher-ui-test-artifacts >/dev/null 2>&1 || true
        ANDROID_SERIAL="$SERIAL" ./gradlew connectedDebugAndroidTest >"$TEMP_DIR/ui.log" 2>&1
        UI_EXIT=$?
    else
        UI_EXIT=1
    fi
    set -e
fi

mkdir -p "$REPORT_DIR/artifacts"
cp "$TEMP_DIR/build.log" "$REPORT_DIR/build.log"
[[ -f "$TEMP_DIR/install.log" ]] && cp "$TEMP_DIR/install.log" "$REPORT_DIR/install.log"
[[ -f "$TEMP_DIR/ui.log" ]] && cp "$TEMP_DIR/ui.log" "$REPORT_DIR/ui.log"
[[ -f "$TEMP_DIR/emulator.log" ]] && cp "$TEMP_DIR/emulator.log" "$REPORT_DIR/emulator.log"

"$ADB" -s "$SERIAL" pull /sdcard/Download/minml-launcher-ui-test-artifacts/. "$REPORT_DIR/artifacts" >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" exec-out screencap -p >"$REPORT_DIR/final-screen.png" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell uiautomator dump /sdcard/minml-launcher-final.xml >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" pull /sdcard/minml-launcher-final.xml "$REPORT_DIR/final-hierarchy.xml" >/dev/null 2>&1 || true
{
    "$ADB" -s "$SERIAL" shell getprop ro.build.version.release
    "$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk
    "$ADB" -s "$SERIAL" shell wm size
    "$ADB" -s "$SERIAL" shell wm density
    "$ADB" -s "$SERIAL" shell cmd role get-role-holders android.app.role.HOME
    "$ADB" -s "$SERIAL" shell dumpsys package dev.obvious.minimallauncher | rg -m2 'versionCode|versionName'
} >"$REPORT_DIR/device.txt" 2>&1 || true
"$ADB" -s "$SERIAL" logcat -d -v threadtime | rg 'minimallauncher|AndroidRuntime|FATAL EXCEPTION|System.err' >"$REPORT_DIR/logcat.txt" || true

END_TIME=$(date +%s)
python3 scripts/ui_test_report.py \
    --project-root "$PROJECT_ROOT" \
    --output "$REPORT_DIR/index.html" \
    --serial "$SERIAL" \
    --avd "$AVD_NAME" \
    --build-exit "$BUILD_EXIT" \
    --ui-exit "$UI_EXIT" \
    --duration "$((END_TIME - START_TIME))"

echo "UI validation report: $REPORT_DIR/index.html"
if [[ "$BUILD_EXIT" != 0 || "$UI_EXIT" != 0 ]]; then
    exit 1
fi
