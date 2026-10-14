# Aro changelog

## Unreleased

- Browse-by-genre now uses the YouTube catalog instead of the legacy streaming endpoint, caches successful results, and shows loading placeholders and Retry for empty or failed requests.
- Added a newer-version popup and Settings → App updates. Checks use GitHub release metadata; Download APK opens the matching direct APK asset in the default browser. Automatic checks are cached, and older releases are never offered as updates.
- Enabled repository issues and corrected inherited announcement defaults. Both issue reporting and the crash dialog open Aro’s issue chooser; crash details remain under the user’s control.

- Fixed Accounts waiting for unrelated cloud-library flows before showing a connected Spotify account.
- Recover existing in-app Spotify sessions at startup and keep connection verification alive when leaving the login screen, while respecting explicit logout.
- Simplified Spotify connection to one sign-in page, removing device-email selection and the browser OAuth experiment. Kept the existing account playlist/liked-song loading and caches. The local phone candidate is 1.0.0-rc.4 (code 16); it has not been published.

- Linked the header, hero, and download section directly to the versioned APK, retaining release notes/checksums as a secondary link. Added a compact mobile navigation menu with Appearance controls, keyboard/outside dismissal, and reduced-motion transitions; aligned and enlarged footer supporting text.

- Removed the blue hero-phone backdrop while retaining the heading panel and font. Made mockup proportions consistent across desktop and mobile, with the annotation below the phones.
- Added minimal scrollbars, reduced-motion-aware Lenis scrolling, Motion selection transitions, persistent light/dark themes, and local Privacy and Terms pages based on the project’s documented behavior and license status.

- Refined the website with Material-inspired rounded surfaces, illustrated music-source cards, segmented preview controls, and tonal installation guidance while preserving Aro’s heading font and paired hero phones.
- Installed the `transitions-dev` design skill and used its panel reveal and icon swap recipes for responsive screen previews and question controls; inactive previews are hidden from assistive technology, changes are announced, and reduced motion is respected.

- Redesigned the app website around Aro’s own rounded typography, blue and plum colors, and real screenshots. Added an accessible four-screen preview, music-source explanations, Android installation steps, and native expandable questions.
- Corrected website download guidance to Android 11+ ARM64 and linked the versioned candidate release page. Removed the custom CSS loader from the website configuration; motion respects reduced-motion preferences.

## 1.0.0-rc.1 — 2026-10-12

First Aro phone release candidate. A development prerelease was authorized by the maintainer; compatible component distribution rights remain unresolved. See [release validation](RELEASE_CHECKLIST.md) and [license status](LICENSE_STATUS.md).

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
