#!/usr/bin/env python3
"""Exercise release smoke device selection without connecting to Android devices."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SMOKE_SCRIPT = Path(__file__).resolve().with_name("run_release_smoke.sh")

FAKE_ADB = r'''
import json
import os
import sys

args = sys.argv[1:]
with open(os.environ["SMOKE_TEST_CALLS"], "a", encoding="utf-8") as log:
    log.write(json.dumps({"tool": "adb", "args": args}) + "\n")
devices = json.loads(os.environ["SMOKE_TEST_DEVICES"])
if args == ["devices"]:
    print("List of devices attached")
    for serial, device in devices.items():
        print(serial + "\t" + device["state"])
    sys.exit(0)
if len(args) < 3 or args[0] != "-s":
    sys.exit("Every device command must include an explicit serial")
device = devices.get(args[1])
if device is None or device["state"] != "device":
    sys.exit("Device unavailable")
command = args[2:]
if command == ["shell", "getprop", "ro.kernel.qemu"]:
    print(device["qemu"] + "\r")
elif command not in (
    ["shell", "cmd", "connectivity", "airplane-mode", "enable"],
    ["shell", "svc", "wifi", "disable"],
    ["shell", "svc", "data", "disable"],
    ["logcat", "-d"],
) and command[:1] != ["pull"]:
    sys.exit("Unexpected adb command: " + repr(command))
'''

FAKE_GRADLE = r'''
import json
import os
import sys

with open(os.environ["SMOKE_TEST_CALLS"], "a", encoding="utf-8") as log:
    log.write(json.dumps({
        "tool": "gradle",
        "args": sys.argv[1:],
        "serial": os.environ.get("ANDROID_SERIAL"),
    }) + "\n")
'''


class ReleaseSmokeGuardTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="comsat-smoke-guard-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.calls_path = self.directory / "calls.jsonl"
        self.report_dir = self.directory / "smoke/build/reports/release-smoke"
        bin_dir = self.directory / "bin"
        bin_dir.mkdir()
        for path, source in (
            (bin_dir / "adb", FAKE_ADB),
            (self.directory / "gradlew", FAKE_GRADLE),
        ):
            path.write_text("#!" + sys.executable + "\n" + source, encoding="utf-8")
            path.chmod(0o755)
        self.environment = {
            **os.environ,
            "PATH": str(bin_dir) + os.pathsep + os.environ.get("PATH", ""),
            "SMOKE_TEST_CALLS": str(self.calls_path),
        }
        self.environment.pop("ANDROID_SERIAL", None)

    def run_smoke(self, devices, serial=None):
        self.environment["SMOKE_TEST_DEVICES"] = json.dumps(devices)
        if serial is not None:
            self.environment["ANDROID_SERIAL"] = serial
        result = subprocess.run(
            ["bash", str(SMOKE_SCRIPT)],
            cwd=self.directory,
            env=self.environment,
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
        calls = [
            json.loads(line)
            for line in self.calls_path.read_text(encoding="utf-8").splitlines()
        ]
        return result, calls

    def assert_rejected_before_writes(self, result, calls):
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(self.report_dir.exists(), "Reports created before device validation")
        for call in calls:
            self.assertEqual(call["tool"], "adb", "Gradle ran for an unsafe target")
            args = call["args"]
            self.assertTrue(
                args == ["devices"]
                or (args[:1] == ["-s"] and args[2:] == ["shell", "getprop", "ro.kernel.qemu"]),
                "Unsafe command executed before rejecting the device: " + repr(args),
            )

    def assert_selected(self, result, calls, serial):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        gradle_calls = [call for call in calls if call["tool"] == "gradle"]
        self.assertEqual(len(gradle_calls), 1)
        gradle = gradle_calls[0]
        self.assertEqual(gradle["serial"], serial)
        self.assertIn(":smoke:connectedReleaseAndroidTest", gradle["args"])
        self.assertIn("-Pcomsat.testBuildType=release", gradle["args"])
        self.assertNotIn("--serial", gradle["args"], "AGP 9.3.1 cannot mutate its serial list")
        device_commands = []
        for call in calls:
            if call["tool"] != "adb" or call["args"] == ["devices"]:
                continue
            args = call["args"]
            if args[2:] == ["shell", "getprop", "ro.kernel.qemu"]:
                continue
            self.assertEqual(args[:2], ["-s", serial])
            device_commands.append(args[2:])
        for command in (
            ["shell", "cmd", "connectivity", "airplane-mode", "enable"],
            ["shell", "svc", "wifi", "disable"],
            ["shell", "svc", "data", "disable"],
            ["logcat", "-d"],
        ):
            self.assertIn(command, device_commands)
        self.assertTrue(any(command[:2] == ["pull", "/sdcard/Download/comsat-smoke"] for command in device_commands))

    def test_phone_only_is_rejected_before_any_device_changes(self):
        self.assert_rejected_before_writes(*self.run_smoke({
            "personal-phone": {"state": "device", "qemu": "0"},
        }))

    def test_explicit_phone_is_rejected_even_with_an_emulator_available(self):
        self.assert_rejected_before_writes(*self.run_smoke({
            "personal-phone": {"state": "device", "qemu": "0"},
            "emulator-5554": {"state": "device", "qemu": "1"},
        }, serial="personal-phone"))

    def test_phone_and_one_emulator_selects_and_scopes_to_emulator(self):
        result, calls = self.run_smoke({
            "personal-phone": {"state": "device", "qemu": "0"},
            "emulator-5554": {"state": "device", "qemu": "1"},
        })
        self.assert_selected(result, calls, "emulator-5554")

    def test_multiple_emulators_require_explicit_selection(self):
        self.assert_rejected_before_writes(*self.run_smoke({
            "emulator-5554": {"state": "device", "qemu": "1"},
            "emulator-5556": {"state": "device", "qemu": "1"},
        }))

    def test_explicit_emulator_selection_scopes_all_changes(self):
        result, calls = self.run_smoke({
            "emulator-5554": {"state": "device", "qemu": "1"},
            "emulator-5556": {"state": "device", "qemu": "1"},
        }, serial="emulator-5556")
        self.assert_selected(result, calls, "emulator-5556")

    def test_offline_emulator_is_rejected_without_selection(self):
        self.assert_rejected_before_writes(*self.run_smoke({
            "emulator-5554": {"state": "offline", "qemu": "1"},
        }))

    def test_explicit_offline_emulator_is_rejected(self):
        self.assert_rejected_before_writes(*self.run_smoke({
            "emulator-5554": {"state": "offline", "qemu": "1"},
        }, serial="emulator-5554"))


if __name__ == "__main__":
    unittest.main()
