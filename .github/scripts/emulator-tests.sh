#!/usr/bin/env bash
# Runs the emulator tests (called from android-emulator.yml), with one retry.
#
# - The whole device log is saved to logs/, so a test process that is lost or a run that
#   gets stuck can be looked at afterwards (each test's own logcat only covers that test).
# - Each attempt is stopped after 15 minutes (a full run takes about 6).
# - Before the retry the app, the tests and the test orchestrator are removed, so the
#   retry does not trip over a stopped attempt that is still installed or running.
# - The retry runs only the tests that failed. On Android 14 the test process is sometimes
#   lost between two tests (a race in the emulator when the orchestrator clears the app's
#   data), so re-running all of them gives the race another 30 chances.
set -u
api="$1"
mkdir -p logs "screenshots/api-$api"

adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent 82 || true
adb logcat -c || true
adb logcat -v threadtime > logs/device-logcat.txt 2>&1 &
logcat_pid=$!

# $1: where to keep the output; $2 (optional): only these tests, "Class#method,..."
attempt() {
  local only=()
  if [ -n "${2:-}" ]; then
    only=("-Pandroid.testInstrumentationRunnerArguments.class=$2")
  fi
  set -o pipefail
  timeout 15m ./gradlew connectedDebugAndroidTest "${only[@]}" 2>&1 | tee "$1"
}

# The failed tests, from Gradle's summary lines: "1) byName(il.org.ay.navigation.ui.SearchActivityTest)"
failed_tests() {
  grep -oE '^[0-9]+\) [A-Za-z0-9_]+\([A-Za-z0-9_.]+\)' "$1" \
    | sed -E 's/^[0-9]+\) ([A-Za-z0-9_]+)\(([A-Za-z0-9_.]+)\)/\2#\1/' \
    | sort -u | paste -sd, -
}

clean_device() {
  for p in il.org.ay.navigation.test il.org.ay.navigation \
           androidx.test.orchestrator androidx.test.services; do
    adb shell am force-stop "$p" || true
    adb uninstall "$p" > /dev/null 2>&1 || true
  done
}

status=0
if ! attempt logs/attempt-1.txt; then
  cp -r app/build/outputs/androidTest-results logs/attempt-1 2>/dev/null || true
  adb shell dumpsys activity processes > logs/attempt-1-processes.txt 2>&1 || true
  failed=$(failed_tests logs/attempt-1.txt)
  echo "::warning::first attempt failed (${failed:-no test named: the whole run}); retrying ${failed:+only those}"
  clean_device
  attempt logs/attempt-2.txt "$failed" || status=1
fi

adb pull /sdcard/Pictures/aloney/. "screenshots/api-$api/" || true
adb shell ls /data/tombstones > logs/tombstones.txt 2>&1 || true
kill "$logcat_pid" 2>/dev/null || true
exit $status
