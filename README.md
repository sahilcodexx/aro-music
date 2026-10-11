# Aro Music

Aro is an Android music player combining your local library, YouTube Music discovery, Spotify playlist discovery, and optional personal music servers. The first Aro release is being prepared as **1.0.0-rc.1**; it is a development release, not a promise of compatibility with every device or provider.

## Features

- Local songs, albums, artists, favorites, playlists, lyrics, listening history, and an equalizer.
- YouTube Music search, artist collections, radio queues, and playlist imports.
- Spotify sign-in, account playlists, liked songs, catalog browsing, and playlist imports. Spotify tracks are matched to YouTube for playback; Aro does not play Spotify's licensed audio streams.
- Recently played, speed dial, daily mixes, and cached recommendations. Pull to refresh the home feed; changing tracks does not rebuild it.
- Playlist snapshots, progressive track loading, and artwork-based colors across the player and main screens.
- Optional Telegram, Subsonic/Navidrome, Jellyfin, NetEase Music, and QQ Music integrations. These require your own accounts or servers and are not all verified for the release candidate. Google Drive is disabled pending OAuth configuration.

## Install and compatibility

Android **11 or newer** is required (`minSdk 30`). Phone packages target Android 16 (`targetSdk 36`). Use the ARM64 APK on most modern phones, ARMv7 for older supported ARM devices, or the universal APK when unsure. The Android App Bundle is for store distribution, not direct installation.

Development installs use `com.aro.music.debug`; signed release installs use `com.aro.music`. The release signing key must remain the same for future upgrades. Wear OS sources are included, but Wear OS is outside the first phone release's verified scope.

See [release validation and limitations](RELEASE_CHECKLIST.md) before distributing a build. GitHub artifacts in this private repository require access to the repository.

## Build

Use JDK 21 and Android SDK platform 37. Install build tools 36.0.0 and let Gradle install other required SDK/NDK components. Use the checked-in Gradle wrapper; the YouTube library is built from source in `innertubex/`, with no dependency on a developer's Maven cache.

```sh
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Some device tests access live services and require authenticated accounts. CI runs a separate account-independent device suite on Android API 30 and 36. Configure `sdk.dir` in an ignored `local.properties` when the SDK is not available through your environment.

## CI and release builds

[Android validation](.github/workflows/phone-debug.yml) runs unit tests, lint, phone builds, and emulator checks on pushes and pull requests to `main`. Existing lint debt is recorded in `app/lint-baseline.xml`; a green lint job means no new errors beyond that baseline, not that all existing issues have been fixed.

[Aro release candidate](.github/workflows/phone-release.yml) is manually dispatched from `main`. It validates release code, builds signed APKs and an AAB, verifies APK signatures, computes SHA-256 checksums, and creates a **draft prerelease**. It does not automatically publish a stable release.

Release signing uses GitHub `production` environment secrets `ARO_KEYSTORE_BASE64`, `ARO_STORE_PASSWORD`, `ARO_KEY_PASSWORD`, and `ARO_KEY_ALIAS`. Locally, use `ARO_KEYSTORE_PATH` and the same password/alias environment variables, or an ignored `keystore.properties` containing `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Never commit a key, password, token, or account cookie. Back up the signing key securely before shipping.

```sh
gh workflow run phone-debug.yml --ref main
gh workflow run phone-release.yml --ref main
gh run list
gh release list
```

## Accounts and data

Spotify authentication currently uses an embedded web sign-in and a stored Spotify session cookie. Device-email selection prefills an email; the account provider still handles authentication. Session expiry can require reconnecting. Imported playlists are local copies; subsequent edits are not automatically written back to Spotify or YouTube.

Aro stores library metadata, settings, history, and playlist caches on the device. Cloud services receive the requests necessary for their features. Optional AI functions require separately configured provider credentials. Review provider terms and permissions before connecting accounts. See [Spotify integration](SPOTIFY_INTEGRATION.md) and [the release checklist](RELEASE_CHECKLIST.md) for details.

## Contributing and license

Report reproducible problems through [Aro issues](https://github.com/sahilcodexx/aro-music/issues), including app version, Android version, device model, provider, and steps. Remove credentials and private playlist details from logs. This project is licensed under GNU GPL v3; see [LICENSE](LICENSE). Preserve upstream copyright and license notices when redistributing source or builds.

## Credits

Aro's UI is inspired by and derived from [PixelPlay](https://github.com/theovilardo/PixelPlay); credit belongs to its authors and contributors. Music discovery and queue integration draw on [Meld](https://github.com/FrancescoGrazioso/Meld) and the Meld project supplied locally during development. [InnerTubeX](https://github.com/MetrolistGroup/innertubex) supplies YouTube Music protocol and playback tools; its GPL license and adapted sources are included in `innertubex/`.

Aro uses YouTube/YouTube Music and Spotify as external services, with additional integrations for Telegram/TDLib, Subsonic/Navidrome, Jellyfin, NetEase Music, and QQ Music. Service names and artwork belong to their respective owners; this app is not affiliated with those services. Thanks also to AndroidX, Jetpack Compose, Media3, Kotlin, Ktor, Coil, Room, Hilt, QuickJS, and the other open-source dependencies whose notices and licenses remain applicable.
