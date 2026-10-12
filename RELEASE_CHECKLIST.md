# Aro 1.0.0 release validation

Updated 2026-10-12. This repository currently prepares **1.0.0-rc.1**, version code 13. Publication is blocked by unresolved component licensing; see [LICENSE_STATUS.md](LICENSE_STATUS.md). Stable 1.0.0 validation is also incomplete. A successful compilation is not proof that every setting, account, or phone works.

## Recorded validation

- Final source at `46fdda1`: all 237 debug and all 237 release unit tests passed locally, with no failures, errors, or skipped tests. Local release lint passed; GitHub debug lint passed against the inherited baseline.
- Repository-owned InnerTubeX sources build without unpublished Maven-local artifacts. Connected ARM64 phone tests verified live YouTube home/catalog/radio/artwork and real decoded playback advancing beyond 0:00.
- An authenticated API 36 phone verified Spotify playlist counts and progressive/persisted playlist loading. The broader connected-service/theme/sign-in/home suite passed 17 tests before final release changes.
- Database and migration checks passed all 11 tests on the connected phone, including schemas 25–41 upgrading to 42 and cached Navidrome songs with null disc numbers preserved as zero.
- Account-independent native JavaScript evaluation and local audio decoding/clock advancement passed on the connected phone. API 30 and 36 emulator jobs passed these checks in run [38212925614](https://github.com/sahilcodexx/aro-music/actions/runs/38212925614).
- That earlier run exposed duplicate home-refresh requests. Refresh and pagination now register lazy jobs under synchronization before starting; completion permits the next request before publishing idle state. Final run [38221040493](https://github.com/sahilcodexx/aro-music/actions/runs/38221040493) passed all three jobs: unit/lint/APK/native-alignment validation and API 30/36 emulator suites. API 30 ran all 18 selected device tests. Final `main` commit `8363fd3` also passed all three jobs in [run 38224549131](https://github.com/sahilcodexx/aro-music/actions/runs/38224549131). These changes were merged to `main` in PR [#4](https://github.com/sahilcodexx/aro-music/pull/4).
- Earlier complete CI run [38203784349](https://github.com/sahilcodexx/aro-music/actions/runs/38203784349) passed build/lint/unit and API 30/36 emulator jobs at commit `5f284f1`.
- Signed ARM64, ARMv7, and universal APKs and an AAB built locally. ARM64 APK signatures and 16 KiB ZIP alignment verify; all five ARM64 native libraries meet 16 KiB ELF load-segment alignment. The signed ARM64 build installs and launches on the Samsung SM-M346B/API 36. The previously built signed candidate also decoded a silent local WAV: MediaSession reported PLAYING at 8,929 ms, 15,000 ms buffered, and no error. The temporary audio file was removed. Signed APKs and the AAB were rebuilt from `8363fd3`; all APK signatures and the AAB signature verify, ARM64 native alignment passes, and checksums are prepared in the ignored release-validation directory. The rebuilt ARM64 APK was installed as an update on the phone. Signed online playback of Bhagwaan/KARMA reported PLAYING at 20,188 ms with a 50-song queue and no error. Playback was paused and the original device volume restored. Actual 16 KiB-device behavior remains unverified.
- APK splits and the AAB use separate Gradle invocations to avoid AGP's split-resource limitation. SDK installation uses the exact `platforms;android-37.0` package. Workflow syntax passes actionlint and third-party actions are pinned.
- The `production` environment has encrypted signing secrets. Signing material is ignored by Git. The original key must be securely backed up and preserved for upgrades.
- Initial lint reported 556 errors; targeted fixes reduced this to 548. Existing debt remains in `app/lint-baseline.xml`. A green lint check detects new issues; it does not establish that the baseline errors are fixed. Local release lint passed in a separate invocation after combined analysis hit a generated-Hilt-source tooling error. Final clean-run GitHub debug lint passed. An extra local debug lint attempt was interrupted and is not counted as successful.
- Aro source branding, shrinker rules, widget snapshot names, and release information replace inherited app names. Original legal notices and credits remain. The inherited root license conflicts with included GPL components; publication is explicitly gated pending resolution.

## Provider coverage

| Provider | Current implementation and release status |
| --- | --- |
| Local music | Room persistence, scanner, library filters, playlists, lyrics and playback implemented. Large-library, removable-storage and permission-revocation checks remain. |
| YouTube Music | Live home, radio, catalog and decoded playback verified on the connected phone. Provider/network/region changes can affect availability. |
| Spotify | Account playlists, counts, snapshots and YouTube matching verified on an authenticated phone. Expired sessions, account switching and very large private playlists still need dedicated checks. |
| Subsonic / Navidrome | Credential validation, salted API authentication, catalog and stream endpoints exist. No real server account was supplied for end-to-end release verification. |
| Jellyfin | Authentication, token headers, library and stream endpoints exist. No real server account was supplied for end-to-end release verification. |
| Telegram | TDLib auth and audio sync exist. Current implementation uses inherited API application identifiers; register Aro-specific Telegram application credentials and verify login/sync/seek/logout before stable distribution. |
| NetEase Music | Cookie login, encrypted requests and stream resolution exist. Account and regional playback verification remain. |
| QQ Music | Cookie login, signed/encrypted requests and stream resolution exist. Account and regional playback verification remain. |
| Google Drive | Disabled. Web OAuth client ID is a placeholder; configure an Aro Google Cloud project and verify consent, token refresh and streaming before enabling. |

Unverified providers must not be advertised as fully tested. The cloud menu's disabled Google Drive entry reflects its actual setup status.

## Settings coverage

The settings screens route changes through `SettingsViewModel` to DataStore repositories. Existing tests cover preferences reset/import, limits and defaults; backup tests cover format detection, manifest validation, favorites, stats and restore handling. The added release-settings test covers read-back through a recreated repository for crossfade, shuffle, album grouping, library visibility, Telegram playlist visibility, haptics, player dismissal, navigation style and artwork cache limits.

This establishes persistence for those settings, not every setting's physical effect. Validate these categories on the signed release build:

- Library: granted/revoked audio permissions, folder allow/deny rules, minimum duration, artist parsing, rescan and empty library.
- Appearance: light/dark/system themes, artwork palettes, contrast, fonts, navigation styles and rotation.
- Playback: repeat/shuffle, crossfade/gapless, replay gain, sleep timer, audio focus, Bluetooth/headset controls, notification controls and queue restoration.
- Behavior: gestures, haptics, launch tab, back navigation and accessibility/font scaling.
- Equalizer/device capability: output-specific effects and unsupported-device fallback; HiFi remains hardware dependent.
- AI: configured/invalid provider credentials, model selection, request failure and usage limits. CI does not contain AI keys.
- Backup/restore: actual exported file round trip, cancellation, corrupt file and permission loss.
- Accounts: sign-in, expired session, reconnect/logout, cancellation and clearing stale account caches for each supported provider.

## Device and release gates

Android 11/API 30 is the minimum, not all Android phones. Automated account-independent emulator checks are configured for API 30 and 36. Their successful GitHub run must be recorded. Verify ARM64, ARMv7, a low-memory phone, a tablet/foldable layout, and battery-restricted background playback before broad compatibility claims. Wear OS is outside this milestone.

Before any publication, resolve the root-license/GPL component conflict documented in `LICENSE_STATUS.md`.

Before stable publication:

1. Record final debug/release unit, lint, emulator and phone instrumentation results, including any skipped tests.
2. Review the 548 baseline errors, especially restricted APIs, opt-in usage, formatting, missing translations and missing Android Auto voice-search support. Baseline acceptance alone does not clear stable release.
3. Validate the R8-shrunk signed release, launch, local playback, online playback, account persistence and an upgrade signed by the same key.
4. Complete provider/account and settings scenarios above, or explicitly exclude unsupported features from stable scope.
5. Securely back up `.release-signing/aro-release.jks` and its password outside the development machine. Losing the key prevents compatible APK updates.
6. Run the release workflow from `main`, review the draft prerelease assets/signatures/checksums, and test those exact APKs.
7. Publish the candidate for development testing. Promote to stable only after recording the outstanding results; store publication additionally needs store metadata and review.

## Build pipeline

`phone-debug.yml` handles pushes/PRs to `main`, unit tests, lint, APK artifacts and API 30/36 emulator tests. `phone-release.yml` is a manual release build in the GitHub `production` environment. It reads signing secrets, runs release unit tests/lint, produces APKs and an AAB, verifies signatures, and creates a draft prerelease. It does not publish stable automatically.

Neither workflow relies on an unpublished local AAR or creates a disposable release signing key. Logs must never contain cookies, signing passwords, personal account data or API keys.

## Dependency updates

Passing release validation applies to the tested source and dependency versions. Dependabot PR #5 updates only `actions/checkout` and passed. PR #6 groups 74 Gradle dependency updates and fails Kotlin compilation in Material3 slider calls; it remains unmerged and does not affect the verified candidate. Resolve dependency API migrations in separate reviewed changes before adopting those updates.

## Intermittent genre-test correction

A later documentation-only run at `a5c3a96` exposed `MusicRepositoryImplTest` assuming the genre flow always delivers a fallback emission before the database result. The production flow intentionally conflates updates, so skipping one emission could discard the only delivered database result. All three genre tests now wait for a database-backed genre ID rather than an emission count. The false-unknown case also explicitly verifies that Unknown is absent. No application behavior or dependency versions changed. After this correction, all 237 debug and all 237 release unit tests passed locally; the next CI run must validate the pushed correction.
