# Aro Music

Your library, your playlists, and a home feed shaped by what you listen to.

Aro is an Android music app with local playback, YouTube Music discovery, Spotify playlist imports, and artwork-based colors. **1.0.0-rc.1** is our first phone release candidate. The development candidate is distributed as a prerelease in this repository; no stable release is declared. Component licensing remains unresolved.

## What you can do

- Play local music and organize songs, artists, albums, favorites, and playlists.
- Search YouTube Music, explore artist collections, and start related-song queues.
- Connect Spotify to browse account playlists and liked songs. Spotify metadata is matched to YouTube for audio playback.
- Import YouTube and Spotify playlists as local copies. Remote edits are not automatically synchronized back.
- Open a home feed with recently played, speed dial, daily mix, and discovery shelves. Cached content and loading placeholders preserve the layout; pull to refresh recommendations.
- Use lyrics, listening history, an equalizer, and artwork colors across the player, navigation, and backgrounds.

Optional server/account integrations include Subsonic/Navidrome, Jellyfin, Telegram, NetEase Music, and QQ Music. Their live release verification is incomplete. Google Drive remains disabled pending OAuth setup. See the [validation record](RELEASE_CHECKLIST.md) for tested features and remaining work.

## Installation

Aro requires **Android 11 or newer**. The phone build targets Android 16.

| Package | Use |
| --- | --- |
| ARM64 APK | Most modern Android phones |
| ARMv7 APK | Supported 32-bit ARM devices |
| Universal APK | When unsure; larger download |
| AAB | Store distribution tooling; cannot be installed directly |

Signed candidate packages and checksums are available under [GitHub Releases](https://github.com/sahilcodexx/aro-music/releases). This repository is public; release APKs can be downloaded without repository access. Only install packages from a trusted source.

Release builds use `com.aro.music`; development builds use `com.aro.music.debug` and keep separate app data. Keep the release signing key for all future updates. Wear OS sources are included but are outside this phone milestone's verified scope.

## Getting started

1. Grant audio access if you want Aro to scan music stored on your phone. Your local collection appears in Library.
2. Use Search to find online songs, albums, artists, and playlists. Start a song or radio queue from its results.
3. Connect Spotify through Aro's account flow to browse your playlists and liked songs. Aro opens Spotify's sign-in page and verifies the session after you log in.
4. Use Library's **Import** action with a supported YouTube or Spotify playlist link to create a local playlist copy. Unavailable or unmatched songs may be omitted from the playable copy.
5. Browse genres in Search to discover online songs. Empty or failed requests offer Retry.
6. Open **Settings → App updates** to check for a newer release and download its APK. Aro also checks automatically, with cached metadata to limit requests. Install the downloaded APK through Android’s installer.
7. Long-press a home song to pin or unpin it in speed dial. Pull down on Home when you want to refresh discovery.

**Recently played** follows listening history. **Speed dial** prioritizes pinned and other quick picks instead of simply duplicating that history. Recommendations stay cached across track changes; switching songs changes playback and artwork colors without rebuilding the whole home feed. First-time loading uses placeholders to reserve the personal shelves before daily mix appears.

A remote playlist's source count can differ from its playable local count. Spotify songs must find a usable YouTube match, and removed or region-restricted items may not play. Large uncached imports can take longer than cached reopening; see [Spotify loading behavior](SPOTIFY_INTEGRATION.md).

## Build from source

Install JDK 21, Android SDK package `platforms;android-37.0`, and build tools 36.0.0. Set `sdk.dir` in ignored `local.properties` or configure your Android SDK environment. Use the checked-in wrapper:

```sh
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon
```

InnerTubeX is built from repository sources; an unpublished Maven-local AAR is not required. For emulator tests, signing, bundle builds, and troubleshooting, read the [development guide](DEVELOPMENT.md).

## App showcase website

The `web/` folder contains a responsive Next.js app website with real Aro screenshots, a keyboard-accessible screen selector, Android installation guidance, and release links. It uses Aro’s rounded type, Material-inspired tonal surfaces, original music illustrations, reduced-motion-aware transitions, Lenis scrolling, light/dark themes, and local Privacy and Terms pages. Run `bun install --frozen-lockfile` and `bun run dev` from `web/` to preview it locally. See the [website guide](web/README.md) for build commands and design details.

## Project documentation

| Document | Contents |
| --- | --- |
| [Contributing](CONTRIBUTING.md) | Issues, pull requests, and review expectations |
| [Development](DEVELOPMENT.md) | Setup, project structure, tests, and release workflow |
| [Changelog](CHANGELOG.md) | Aro changes by version |
| [Release validation](RELEASE_CHECKLIST.md) | Evidence, compatibility limits, and release gates |
| [Spotify integration](SPOTIFY_INTEGRATION.md) | Authentication, playlist caching, and playback matching |
| [Privacy](PRIVACY.md) | On-device data, permissions, and connected services |
| [Security](SECURITY.md) | Reporting vulnerabilities and handling credentials |
| [License status](LICENSE_STATUS.md) | Existing restrictions and the publication blocker |
| [Agent instructions](AGENTS.md) | Repository rules for coding agents |

## Accounts and privacy

Spotify uses one in-app web sign-in page and a stored session cookie. Aro does not offer a device-email picker or browser OAuth flow. Sessions may expire and require reconnecting; see [Spotify behavior and limitations](SPOTIFY_INTEGRATION.md). Aro does not stream Spotify's licensed audio.

Library metadata, preferences, listening history, and playlist caches are stored on your device. Connecting a service sends requests to that service. Optional AI features use the provider you configure. Do not include passwords, cookies, signing material, or private account information in issue reports.

## License and release status

The existing [root license](LICENSE) is a restrictive license attributed to Theo Vilardo. Included [InnerTubeX](innertubex/LICENSE) and Meld-derived code have GPLv3 obligations. Aro cannot currently claim one resolved GPLv3 license for the combined app. Original notices are preserved. The maintainer authorized a development prerelease while compatible distribution rights remain undocumented; see [LICENSE_STATUS.md](LICENSE_STATUS.md). Changing branding does not change those rights.

## Credits

Aro's UI is inspired by and derived from [PixelPlay](https://github.com/theovilardo/PixelPlay); credit belongs to its authors and contributors. Music discovery and queue integration draw on [Meld](https://github.com/FrancescoGrazioso/Meld) and the Meld project supplied locally during development. [InnerTubeX](https://github.com/MetrolistGroup/innertubex) supplies YouTube Music protocol and playback tools; its GPL license and adapted sources are included in `innertubex/`.

Aro uses YouTube/YouTube Music and Spotify as external services, with additional integrations for Telegram/TDLib, Subsonic/Navidrome, Jellyfin, NetEase Music, and QQ Music. Service names and artwork belong to their respective owners; this app is not affiliated with those services. Thanks also to AndroidX, Jetpack Compose, Media3, Kotlin, Ktor, Coil, Room, Hilt, QuickJS, and the other open-source dependencies whose notices and licenses remain applicable.
