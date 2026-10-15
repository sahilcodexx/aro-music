# Preparing an Aro release

The published download must be clearly named and installable without developer tools. The manual release workflow stages a draft prerelease with three **standalone** APKs and checksums:

| File | Intended device |
| --- | --- |
| `Aro-<version>-arm64-v8a.apk` | Most modern ARM64 phones; smaller download |
| `Aro-<version>-armeabi-v7a.apk` | 32-bit ARM phones; smaller download |
| `Aro-<version>-universal.apk` | Choose when unsure; includes ARM64, ARMv7, x86, and x86_64 |

All require Android 11 or newer. Download and install **one** APK. Each has the same application ID, version code, production signer, and features. Changing between compatible variants in a later update preserves app data. The smaller APK removes unused native architectures; it does not remove app features. An incompatible ABI will not run. Universal packaging does not prove compatibility with every phone or vendor setting; record tested devices in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

Aro’s updater prefers a compatible architecture APK from the release metadata and falls back to universal. In current source, assets with a published SHA-256 digest download through a resumable background worker and are checked against that digest and the installed production signer. Older builds may open the APK in a browser. Published 1.0.1 uses stable-channel update filtering. Version 1.0.2 is published without the prerelease flag at the maintainer’s request so those users can discover it. GitHub remains the only host: fewer bytes can help transfer time, but slow GitHub routes remain possible.

## Maintainer steps

1. Read [LICENSE_STATUS.md](LICENSE_STATUS.md), preserve upstream notices, and satisfy the applicable release gates. A draft does not establish distribution rights.
2. Increase `APP_VERSION_CODE` and set `APP_VERSION_NAME` in `gradle.properties`. Update the matching version section in `CHANGELOG.md` with actual changes and dates.
3. Run the behavior-specific tests and record concrete results and skipped coverage in the checklist. Validate the shrunk release and account persistence on a connected phone. Keep the production signing key; never substitute a temporary key.
4. Run the manual **Aro release candidate** workflow from `main`. It builds split APKs first and the AAB in a separate invocation, verifies signatures/native alignment, then prepares a draft. It publishes nothing automatically.
5. Review the generated download table, architecture names, sizes, source commit, APK metadata, checksums, and signatures. `scripts/prepare-release.py` requires matching production metadata and all three APKs before staging them. AAB and developer reports stay in the workflow artifact; users install APKs.
6. Test the exact staged APKs, including an update over the previous production build without uninstalling. Recheck the publicly downloaded bytes after publication. Test ARMv7 separately before claiming physical ARMv7 coverage.
7. Publish only with maintainer authorization and the required release gates satisfied. Never replace a published version with different binary contents; use a new version.

Release notes should lead with the download table and plain installation guidance, then list user-facing changes and the actual validation limits. Keep credits and original licensing notices in the project documentation.
