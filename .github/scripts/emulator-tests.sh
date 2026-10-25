#!/usr/bin/env bash
# Runs the emulator tests (called from android-emulator.yml), with one retry.
#
# - The whole device log is saved to logs/, so a test process that is lost or a run that
#   gets stuck can be looked at afterwards (each test's own logcat only covers that test).
# - Each attempt is stopped after 15 minutes (a full run takes about 6).
# - Before the retry the app, the tests and the test orchestrator are removed, so the
#   retry does not trip over a stopped attempt that is still installed or running.
set -u
api="$1"
mkdir -p logs "screenshots/api-$api"

adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent 82 || true
adb logcat -c || true
adb logcat -v threadtime > logs/device-logcat.txt 2>&1 &
logcat_pid=$!

attempt() {
  timeout 15m ./gradlew connectedDebugAndroidTest
}

clean_device() {
  for p in il.org.ay.navigation.test il.org.ay.navigation \
           androidx.test.orchestrator androidx.test.services; do
    adb shell am force-stop "$p" || true
    adb uninstall "$p" > /dev/null 2>&1 || true
  done
}

status=0
if ! attempt; then
  echo "::warning::first attempt failed; keeping its results in logs/attempt-1 and retrying"
  cp -r app/build/outputs/androidTest-results logs/attempt-1 2>/dev/null || true
  adb shell dumpsys activity processes > logs/attempt-1-processes.txt 2>&1 || true
  clean_device
  attempt || status=1
fi

adb pull /sdcard/Pictures/aloney/. "screenshots/api-$api/" || true
adb shell ls /data/tombstones > logs/tombstones.txt 2>&1 || true
kill "$logcat_pid" 2>/dev/null || true
exit $status
