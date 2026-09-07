#!/usr/bin/env bash
# Only run on a disposable test emulator; playback checks intentionally run offline.
set -euo pipefail

report_dir=smoke/build/reports/release-smoke
mkdir -p "${report_dir}"
collect_diagnostics() {
  adb logcat -d > "${report_dir}/logcat.txt" || true
  adb pull /sdcard/Download/comsat-smoke \
    "${report_dir}/screenshots" > "${report_dir}/screenshot-pull.txt" 2>&1 || true
}
trap collect_diagnostics EXIT

adb shell svc wifi disable
adb shell svc data disable
./gradlew -Pcomsat.testBuildType=release :smoke:connectedReleaseAndroidTest --stacktrace
