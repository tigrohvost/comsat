#!/usr/bin/env bash
# Only run on a disposable test emulator; playback checks intentionally run offline.
set -euo pipefail

# Resolve the target before changing connectivity or installing test packages.
# A developer may have a personal phone connected beside the test emulator.
selected_serial=${ANDROID_SERIAL:-}
if [[ -z "${selected_serial}" ]]; then
  device_list=$(adb devices)
  emulator_serials=()
  while read -r serial state _; do
    [[ "${state}" == "device" ]] || continue
    is_emulator=$(adb -s "${serial}" shell getprop ro.kernel.qemu | tr -d '\r')
    if [[ "${is_emulator}" == "1" ]]; then
      emulator_serials+=("${serial}")
    fi
  done <<< "${device_list}"
  if [[ ${#emulator_serials[@]} -ne 1 ]]; then
    echo "Expected one online emulator; set ANDROID_SERIAL to a disposable emulator." >&2
    exit 1
  fi
  selected_serial=${emulator_serials[0]}
fi

is_emulator=$(adb -s "${selected_serial}" shell getprop ro.kernel.qemu | tr -d '\r')
if [[ "${is_emulator}" != "1" ]]; then
  echo "Refusing offline smoke test: ${selected_serial} is not an emulator." >&2
  exit 1
fi
export ANDROID_SERIAL="${selected_serial}"

report_dir=smoke/build/reports/release-smoke
mkdir -p "${report_dir}"
collect_diagnostics() {
  adb -s "${ANDROID_SERIAL}" logcat -d > "${report_dir}/logcat.txt" || true
  adb -s "${ANDROID_SERIAL}" pull /sdcard/Download/comsat-smoke \
    "${report_dir}/screenshots" > "${report_dir}/screenshot-pull.txt" 2>&1 || true
}
trap collect_diagnostics EXIT

adb -s "${ANDROID_SERIAL}" shell cmd connectivity airplane-mode enable
adb -s "${ANDROID_SERIAL}" shell svc wifi disable
adb -s "${ANDROID_SERIAL}" shell svc data disable
# AGP 9.3.1's --serial path mutates an immutable Gradle ListProperty and fails
# before installing the APK. Its connected-device provider honors ANDROID_SERIAL.
./gradlew -Pcomsat.testBuildType=release :smoke:connectedReleaseAndroidTest --stacktrace
