# Aro changelog

## 1.0.0-rc.1 — 2026-10-12

First Aro phone release candidate. Validation is ongoing; publication is blocked pending compatible component licensing. See [release validation](RELEASE_CHECKLIST.md) and [license status](LICENSE_STATUS.md).

- YouTube Music discovery, catalog details, radio queues, and playlist imports.
- Spotify account playlists, liked songs, artist collections, and YouTube playback matching.
- Home sections ordered recently played, speed dial, daily mix, then discovery.
- Cached home feeds, manual refresh, and loading placeholders that preserve layout.
- Progressive playlist loading, persistent snapshots, and source playlist counts.
- Artwork colors across the player, navigation, and main screen backgrounds.
- Removed the inherited beta 0.5 clean-install prompt from home.
- Aro version information, changelog, and issue links replace inherited release metadata.
- Repository-owned InnerTubeX sources and GitHub validation/release workflows.
- Android 11 widget service exception handling corrected during release review.
- Legacy Navidrome database migration preserves songs with missing disc numbers.
- Native JavaScript and local audio decoding checks added for Android API 30 and 36.
- Genre repository tests now handle conflated updates without intermittent failures.
- Home refresh completion ordering corrected so an immediate refresh is accepted.
- Contribution, development, security, privacy, and coding-agent guides added.

Google Drive needs OAuth setup and remains disabled. Other cloud accounts, settings, release shrinking, and broader device coverage remain subject to the recorded validation gates. Earlier upstream project releases are not Aro releases; original licensing and credits are preserved.
