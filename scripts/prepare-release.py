#!/usr/bin/env python3
"""Stage named standalone APKs and release notes; never publish or build."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path


def prepare(source: Path, destination: Path, version: str, repository: str, notes: Path):
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError("Invalid version")
    if not re.fullmatch(r"[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    metadata = json.loads((source / "output-metadata.json").read_text())
    if metadata["applicationId"] != "com.aro.music":
        raise ValueError("Not a production Aro build")
    expected = {"universal", "arm64-v8a", "armeabi-v7a"}
    outputs = {}
    version_codes = {item["versionCode"] for item in metadata["elements"]}
    if len(version_codes) != 1 or next(iter(version_codes), 0) <= 0:
        raise ValueError("APK version codes must match")
    for item in metadata["elements"]:
        if item["versionName"] != version:
            raise ValueError("APK metadata does not match the requested release")
        filters = item["filters"]
        abi = "universal" if not filters else next(f["value"] for f in filters if f["filterType"] == "ABI")
        filename = item["outputFile"]
        if Path(filename).name != filename or abi not in expected or abi in outputs:
            raise ValueError("Unexpected APK output")
        outputs[abi] = source / filename
    if set(outputs) != expected or not all(path.is_file() for path in outputs.values()):
        raise ValueError("All three standalone APKs are required")
    destination.mkdir(parents=True, exist_ok=True)
    rows, checksums = [], []
    descriptions = {
        "arm64-v8a": "Most modern ARM64 phones — smaller download",
        "armeabi-v7a": "32-bit ARM phones only — smaller download",
        "universal": "Not sure? Choose this — includes all packaged architectures",
    }
    for abi in ("arm64-v8a", "armeabi-v7a", "universal"):
        filename = f"Aro-{version}-{abi}.apk"
        target = destination / filename
        shutil.copyfile(outputs[abi], target)
        with target.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        checksums.append(f"{digest}  {filename}\n")
        url = f"https://github.com/{repository}/releases/download/v{version}/{filename}"
        rows.append(f"| [{filename}]({url}) | {descriptions[abi]} | {target.stat().st_size / 1048576:.1f} MiB |")
    (destination / "SHA256SUMS.txt").write_text("".join(checksums))
    notes.write_text(
        "## Download Aro\n\nRequires **Android 11 or newer**. If unsure, choose **universal**. "
        "Install one APK; these are complete installers, not parts that must be combined.\n\n"
        f"[**Download universal APK — choose if unsure**](https://github.com/{repository}/releases/download/v{version}/Aro-{version}-universal.apk)\n\n"
        "| APK | Choose for | Size |\n| --- | --- | --- |\n" + "\n".join(rows) +
        "\n\nAll variants contain the same Aro features and use the same production signing key and version code. "
        "ARM64 and ARMv7 downloads omit other architectures; universal also includes x86 and x86_64. "
        "An incompatible architecture cannot run on your phone. You can switch to a compatible variant on a later update "
        "without uninstalling or clearing data. Android will ask before installation.\n\n"
        "GitHub is the only host. A smaller APK reduces bytes to transfer but does not guarantee a faster GitHub route. "
        "Hardware, Android behavior, and services can vary; universal packaging is not proof that every phone is tested. "
        "See RELEASE_CHECKLIST.md for actual verification.\n\n"
        "Development testing release. Original notices are preserved; see LICENSE_STATUS.md for unresolved distribution rights.\n\n"
    )

    changelog = Path(__file__).resolve().parents[1] / "CHANGELOG.md"
    sections = re.split(r"(?m)^## ", changelog.read_text())
    selected = next((section for section in sections[1:] if section.startswith(f"{version} —")), None)
    if selected is None:
        selected = next((section for section in sections[1:] if section.startswith("Unreleased\n")), "")
    changes = selected.partition("\n")[2].strip()
    if changes:
        with notes.open("a") as stream:
            stream.write("## Changes\n\n" + changes + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--repository", default="sahilcodexx/aro-music")
    parser.add_argument("--notes", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source, args.destination, args.version, args.repository, args.notes)
