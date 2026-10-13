#!/usr/bin/env python3
"""Check and install an APK, reporting Android's actual package error."""
import hashlib
from pathlib import Path
import subprocess
import sys
import zipfile

EXPECTED = "fc31dcec44cddacdc2f62b9d8c49d8711ddb28af2d3ab0cd1540da0f03f6284d"


def adb(*args):
    result = subprocess.run(["adb", *args], text=True, capture_output=True)
    output = (result.stdout + result.stderr).strip()
    if output:
        print(output)
    return result


def main():
    default = (Path(__file__).resolve().parent.parent / "app/build/release-validation/"
               "v1.0.0-rc.1-main-8363fd3/app-arm64-v8a-release.apk")
    apk = Path(sys.argv[1]).expanduser() if len(sys.argv) > 1 else default
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")
    with apk.open("rb") as file:
        digest = hashlib.file_digest(file, "sha256").hexdigest()
    print(f"APK: {apk}\nSize: {apk.stat().st_size} bytes\nSHA256: {digest}")
    if digest != EXPECTED:
        raise SystemExit("This file differs from the published APK. Do not install it as the verified release.")
    try:
        with zipfile.ZipFile(apk) as archive:
            bad = archive.testzip()
            if bad:
                raise SystemExit(f"Damaged APK entry: {bad}")
    except zipfile.BadZipFile:
        raise SystemExit("The downloaded file is not a complete APK/ZIP archive.")
    devices = adb("devices")
    if devices.returncode:
        raise SystemExit("ADB cannot connect. Run this on the host with USB debugging enabled.")
    connected = [line for line in devices.stdout.splitlines() if line.endswith("\tdevice")]
    if len(connected) != 1:
        raise SystemExit("Connect exactly one authorized phone or select it using ANDROID_SERIAL.")
    for label, prop in [("Phone", "ro.product.model"), ("Android", "ro.build.version.release"),
                        ("API", "ro.build.version.sdk"), ("ABIs", "ro.product.cpu.abilist")]:
        print(f"{label}:")
        adb("shell", "getprop", prop)
    print("Installing the verified APK as an update; no app data will be cleared:")
    result = adb("install", "-r", str(apk.resolve()))
    if result.returncode:
        print("Send the Failure [INSTALL_FAILED_...] line and the device details above.")
    else:
        print("Local verified APK installs successfully. Compare your downloaded Aro.apk using this script's optional file argument.")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
