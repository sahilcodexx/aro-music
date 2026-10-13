#!/usr/bin/env python3
"""Verify the public APK and refresh its URL without running a build or CI."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

REPO = "sahilcodexx/aro-music"
TAG = "v1.0.0-rc.1"
NAME = "Aro-1.0.0-rc.1.apk"
EXPECTED = "fc31dcec44cddacdc2f62b9d8c49d8711ddb28af2d3ab0cd1540da0f03f6284d"


def api(path, method="GET", payload=None):
    args = ["gh", "api", path, "--method", method]
    if payload is not None:
        args += ["--input", "-"]
    result = subprocess.run(args, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None


def digest(path):
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def main():
    root = Path(__file__).resolve().parent.parent
    source = root / "app/build/release-validation/v1.0.0-rc.1-main-8363fd3/app-arm64-v8a-release.apk"
    if not source.is_file() or digest(source) != EXPECTED:
        raise RuntimeError("Original verified APK is missing or changed; no release changes made.")
    release = api(f"repos/{REPO}/releases/tags/{TAG}")
    candidates = [a for a in release["assets"] if a["name"] in
                  (NAME, "Aro.apk", "app", "app-arm64-v8a-release.apk")]
    if len(candidates) != 1:
        raise RuntimeError("Cannot uniquely identify the current APK; no changes made.")
    old = candidates[0]
    folder = Path(tempfile.mkdtemp(prefix="aro-public-download-"))
    (folder / "release-before.json").write_text(json.dumps(release, indent=2))
    uploaded = False
    if old.get("digest") != "sha256:" + EXPECTED:
        print("Remote asset digest differs. Uploading the original verified APK.", flush=True)
        upload = folder / NAME
        shutil.copy2(source, upload)
        env = os.environ.copy()
        env["GODEBUG"] = "http2client=0"
        subprocess.run(["gh", "release", "upload", TAG, str(upload), "--repo", REPO,
                        "--clobber"], env=env, check=True)
        uploaded = True
    else:
        print("GitHub reports the correct original digest. Refreshing the download URL.", flush=True)
        api(f"repos/{REPO}/releases/assets/{old['id']}", "PATCH", {"name": NAME, "label": ""})
    url = f"https://github.com/{REPO}/releases/download/{TAG}/{NAME}"
    downloaded = folder / "download-verification.apk"
    print("Downloading the public URL to verify its actual bytes…", flush=True)
    try:
        subprocess.run(["curl", "--fail", "--location", "--http1.1", "--retry", "2",
                        "--connect-timeout", "30", "--max-time", "900",
                        "--output", str(downloaded), f"{url}?verify={time.time_ns()}"], check=True)
        actual = digest(downloaded)
        if actual != EXPECTED:
            raise RuntimeError(f"Public download is still altered: {actual}. Release link was not updated.")
    except Exception:
        if not uploaded:
            api(f"repos/{REPO}/releases/assets/{old['id']}", "PATCH",
                {"name": old["name"], "label": old.get("label") or ""})
        raise
    body = release.get("body") or ""
    start, end = "<!-- aro-download -->", "<!-- /aro-download -->"
    if body.startswith(start) and end in body:
        body = body.split(end, 1)[1].lstrip()
    heading = (f"{start}\n## Download\n\n[**Download {NAME}**]({url})\n\n"
               "For Android 11 or newer on ARM64 phones. Download the APK and open it to install Aro. "
               "If an older download failed, delete that downloaded file and download this versioned file.\n"
               f"{end}\n\n")
    api(f"repos/{REPO}/releases/{release['id']}", "PATCH", {"body": heading + body})
    if uploaded and old["name"] != NAME:
        api(f"repos/{REPO}/releases/assets/{old['id']}", "DELETE")
    print(f"VERIFIED public download: {url}")
    print(f"SHA256: {EXPECTED}")
    print(f"Downloaded proof and previous release metadata: {folder}")
    print("No build or CI was started. Install the downloaded proof file normally to check the installer flow.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
