#!/usr/bin/env python3
"""Clarify an existing release without rebuilding or re-uploading its APK."""
import json
from pathlib import Path
import subprocess
import tempfile

REPO = "sahilcodexx/aro-music"
TAG = "v1.0.0-rc.1"
EXPECTED_DIGEST = "sha256:fc31dcec44cddacdc2f62b9d8c49d8711ddb28af2d3ab0cd1540da0f03f6284d"


def api(path, method="GET", payload=None):
    command = ["gh", "api", path, "--method", method]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None


def main():
    release = api(f"repos/{REPO}/releases/tags/{TAG}")
    candidates = [asset for asset in release["assets"] if asset.get("digest") == EXPECTED_DIGEST]
    if len(candidates) != 1:
        raise SystemExit("Expected signed APK not uniquely identified; no changes made.")
    apk = candidates[0]
    backup = Path(tempfile.mkdtemp(prefix="aro-release-backup-"))
    (backup / "release.json").write_text(json.dumps(release, indent=2))
    extras = [asset for asset in release["assets"] if asset["name"] in ("BUILD_INFO.txt", "SHA256SUMS.txt")]
    for asset in extras:
        subprocess.run(["gh", "release", "download", TAG, "--repo", REPO,
                        "--pattern", asset["name"], "--dir", str(backup)], check=True)
    api(f"repos/{REPO}/releases/assets/{apk['id']}", "PATCH", {"name": "Aro.apk", "label": ""})
    body = release.get("body") or ""
    start, end = "<!-- aro-download -->", "<!-- /aro-download -->"
    if body.startswith(start) and end in body:
        body = body.split(end, 1)[1].lstrip()
    url = f"https://github.com/{REPO}/releases/download/{TAG}/Aro.apk"
    download = (f"{start}\n## Download\n\n[**Download Aro.apk**]({url})\n\n"
                f"For Android 11 or newer on ARM64 phones (most modern Android phones). "
                f"Download the APK and open it to install Aro.\n{end}\n\n")
    api(f"repos/{REPO}/releases/{release['id']}", "PATCH", {"body": download + body})
    for asset in extras:
        api(f"repos/{REPO}/releases/assets/{asset['id']}", "DELETE")
    updated = api(f"repos/{REPO}/releases/{release['id']}")
    verified = next(asset for asset in updated["assets"] if asset["id"] == apk["id"])
    if verified["name"] != "Aro.apk" or verified.get("digest") != EXPECTED_DIGEST:
        raise SystemExit("Release verification failed; inspect the release before sharing it.")
    print(f"Updated release: {updated['html_url']}")
    print(f"Download: {verified['browser_download_url']}")
    print(f"Technical files and previous release notes backed up at {backup}")


if __name__ == "__main__":
    main()
