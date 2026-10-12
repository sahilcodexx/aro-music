# Developing Aro

## Requirements

- JDK 21; use the Gradle wrapper and checked-in toolchain configuration.
- Android SDK `platforms;android-37.0`, build tools 36.0.0, and platform tools.
- An Android 11/API 30 or newer phone or emulator for device tests.
- Adequate free disk and memory for Android tooling, native libraries, and R8.

Set `sdk.dir=/absolute/path/to/Android/Sdk` in ignored `local.properties` if needed. Do not commit machine paths or credentials. Gradle can download the configured JDK toolchain and required native components.

## Repository map

| Path | Responsibility |
| --- | --- |
| `app/` | Phone app: Compose UI, repositories, Room database, playback, and integrations |
| `shared/` | Shared models and functionality |
| `innertubex/` | Adapted YouTube protocol and stream-resolution sources |
| `wear/` | Wear OS sources; outside current release validation |
| `baselineprofile/` | Profile-generation infrastructure |
| `.github/workflows/` | Android validation and signed release preparation |
| `scripts/check-apk-native.py` | ARM64 native ELF alignment validation |

## Build and test

Run Gradle operations sequentially in one checkout:

```sh
./gradlew :app:assembleDebug --no-daemon --console=plain
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./gradlew :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain
./gradlew :app:connectedDebugAndroidTest -Paro.enableAbiSplits=false --no-daemon
```

The full connected suite includes live network/account tests. CI selects account-independent tests on API 30 and 36; inspect `phone-debug.yml` for the exact list. A skipped live test is not evidence that a provider works. The checked-in lint baseline records inherited debt; green lint checks do not resolve those existing errors.

For database changes, preserve legacy data and test migrations into the current schema. For stream changes, check actual decoding and advancing playback time rather than only URL resolution. Verify shrinking-sensitive changes in a signed release build.

## Signing and packaging

Version name and code live in `gradle.properties`. Increase the version code for Android updates. Production uses `com.aro.music`; debug uses `com.aro.music.debug`.

Local release signing accepts `ARO_KEYSTORE_PATH`, `ARO_STORE_PASSWORD`, `ARO_KEY_PASSWORD`, and `ARO_KEY_ALIAS`, or ignored `keystore.properties` keys `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Never commit signing material. Back up the original key and password securely outside this checkout.

APK splits and the AAB must be built separately:

```sh
./gradlew :app:assembleRelease --no-daemon
# Preserve APKs before changing packaging mode.
./gradlew :app:bundleRelease -Paro.enableAbiSplits=false --no-daemon
```

AGP can leave split shrunk-resource intermediates that conflict with bundle packaging. The release workflow preserves APKs and clears only the generated release resource directory before building the AAB. Use a separate clean checkout for manual bundle builds if necessary.

## GitHub workflows

`phone-debug.yml` validates pushes and pull requests to `main`: unit tests, lint, APK builds, and minimum/current Android emulator checks. Actions are pinned to commit SHAs.

`phone-release.yml` runs manually from `main` in the `production` environment. Its secrets are `ARO_KEYSTORE_BASE64`, `ARO_STORE_PASSWORD`, `ARO_KEY_PASSWORD`, and `ARO_KEY_ALIAS`. It runs release checks, builds signed APKs/AAB, verifies APK signatures and native alignment, and prepares checksums and a draft prerelease. Draft creation is not permission to publish.

```sh
gh run list
gh workflow run phone-debug.yml --ref main
```

Before dispatching a release, resolve [license gates](LICENSE_STATUS.md) and complete the [release checklist](RELEASE_CHECKLIST.md). Review the exact signed artifacts on a real device before publication. Do not replace the signing key or publish a stable version just to bypass a failed gate.

## Common failures

- SDK not found: install the exact `platforms;android-37.0` package, not `platforms;android-37`.
- Emulator cannot boot: check runner disk, RAM, virtualization support, and image architecture.
- KSP/build cache corruption: stop overlapping Gradle processes before rebuilding.
- No artifacts: inspect the earlier failed setup/build step first.
- Provider failures: distinguish network, session expiry, region restrictions, and catalog parsing; sanitize logs.
