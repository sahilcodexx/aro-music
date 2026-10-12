# Aro 1.0.0 release validation

Updated 2026-10-12. This repository currently prepares **1.0.0-rc.1**, version code 13. Stable 1.0.0 publication is not cleared yet. A successful compilation is not proof that every setting, account, or phone works.

## Evidence so far

- Full debug unit suite: 237 tests passed, including the additional release-settings test.
- Repository-owned InnerTubeX source: debug APK and instrumentation APK build successfully without Maven-local dependencies.
- Connected ARM64 phone: live YouTube home/catalog/radio/artwork and muted audio playback passed after the source migration; playback advanced beyond 0:00.
- Connected phone: Spotify account playlist counts and persisted/progressive playlist loading passed with an existing authenticated account.
- Connected phone: theme contrast, sign-in email prefill, and home section loading-layout tests passed.
- Connected phone: the broader connected-service/database/theme/sign-in/home run passed all 17 tests on 2026-10-12 before the final migration and branding changes; final rerun remains required.
- Database test fixtures were found stale: parent artist/album records were missing, and one incremental-upsert test simultaneously expected removal and preservation. Fixtures and expectations were corrected; rerun results must be recorded before release.
- Debug APK ARM64 ELF load-segment alignment: all five native libraries meet the 16 KiB minimum. Release packaging and actual 16 KiB-device behavior still need verification.
- GitHub workflow syntax passes actionlint. External actions are pinned to commit SHAs.
- A production environment and encrypted signing secrets are configured. The signing key and password files are excluded from Git.
- Initial Android lint reported 556 errors. Runtime exception handling and indentation fixes reduced this to 548 errors. Existing debt is checked into `app/lint-baseline.xml`; subsequent lint checks detect new issues. This baseline is NOT evidence that these errors are fixed.

- Emulator checks exposed a Navidrome migration nullability mismatch; the migration now keeps disc numbers non-null and normalizes legacy null values to zero. On the connected phone, all 11 migration/database tests passed, including cached songs with null disc numbers and upgrades from schemas 25–41 to schema 42.
- CI SDK installation uses the exact `platforms;android-37.0` package. Android 11 emulator boot required freeing runner disk space; both fixes are in the build environment.
- APK splits and the AAB build run in separate Gradle invocations because the Android Gradle Plugin rejects a split shrunk-resource directory when creating a bundle.
- Inherited source filenames, compiler/shrinker package rules, and widget snapshot names now use Aro. Original credits and license notices remain; an unrelated inherited startup profile was removed.

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
