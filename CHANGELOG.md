# Aro changelog

## Unreleased

- Home shelves keep each song title and artist together without reserving a second title line. Equalizer band sliders wait for vertical drag intent so horizontal swipes can reach the pager; page indicators are tappable and page count follows the number of bands. Playlist details use full-width artwork with title/count over a readability fade. Google Drive is hidden from new connection options while unavailable; existing stored account data is retained. These changes are source-only.

- Welcome and setup pages now share a scrollable, left-aligned layout with restrained typography, tonal panels, and explicit radio choices. Notification and navigation previews replace decorative collages; completion summarizes the selected theme and navigation. The bottom progress/forward panel and permission gates are unchanged. This redesign has not been built or visually verified.

- Playlist cards use compact title/count spacing and stronger tonal contrast; artist cards use the same surface level. Home’s Aro logo/name opens beta details, also available in Settings. These refinements have not been rebuilt.

- Playlists now offer matching tonal artwork cards and list rows, controlled by a layout icon beside sorting. Playlist and artist layouts each default to cards and retain independent saved choices across restarts.

- Artists support a saved card/list layout choice, circular artwork, names and song counts. Missing artist artwork falls back to a song cover, then the artist icon. The layout toggle joins filter and sort with small inner corners and fully rounded outside ends.

- Home displays Aro instead of the beta badge. The cloud-services sheet includes Spotify, shows its connection state, and opens linked Spotify playlists. Accounts keeps the remaining unconnected providers visible below linked services, with an accurate remaining-service count.
- Library Import offers phone music or an M3U playlist file. Phone music requires confirmation, then Android audio permission; a separate background worker imports metadata without modifying audio files, replacing existing edits, or deleting cloud/library rows. Onboarding no longer requires audio permission or scans phone storage.
- Online Library includes local and saved remote content. Offline Library filters songs, albums, artists, favorites, folders, and default playlist visibility to local content without deleting caches or changing the saved online filter. When imported songs exist, offline startup or an offline Home/Search visit opens Library once per offline session. Reconnecting restores remote library content; cloud maintenance is not newly enqueued while offline.

- Lyrics Synced/Static modes share one inset, rounded two-segment control matching the player’s shuffle/repeat/favorite group, with active selection and accessibility state preserved.

- Playlist details now have a scrollable cover header with artwork-derived colors. An existing playlist cover takes priority; when absent or unavailable, the first song's artwork is used.
- Playlist options include **Change artwork**, with a system image picker and **Use first song art** reset. Custom images are copied into Aro's private storage. Spotify overrides are local to Aro and persist across playlist reloads.
- Cards, Library tab tiles, settings groups, and sheets use shared tonal surface levels from the active artwork/system palette instead of unrelated neutral dark panels. Foreground colors are chosen for contrast across those levels.
- These changes are unreleased. An earlier development snapshot was installed separately and passed 255 unit tests. Later spacing, contrast, and beta-details refinements are source-only; full lint was stopped and device database checks remain pending. No production release or browser preview is included.

## 1.0.2 — 2026-10-16

- Release preparation now stages clearly named smaller ARM64/ARMv7 standalone APKs alongside universal, with a download-choice table and checksums.

- Listening categories include streamed songs and retain track metadata in history. Stable history field names also read older shrunk release records.
- Home opens Listening Stats in a bottom sheet with a compact title/reload row and no back icon; Changelog moves to Settings.
- Song lists and artwork show loading skeletons while requests are pending, respecting disabled system animations.
- The full player previews an artwork-colored song card with bold Aro typography, a contrasting inner surface, and Artwork/Soft/Dark choices. Title and artist sit together, with a compact Aro icon/name inside the card and no separate tagline footer. The whole image fits above separate share controls; landscape uses a side-by-side layout. Sharing exports the preview as a PNG through Android's share sheet.
- In-app updates use four resumable GitHub byte ranges when the release asset provides a SHA-256 digest. Downloads continue in the background; checksum and signing-certificate checks precede Android installation. GitHub remains the only host, so routing-related slowdowns can still occur. Assets without a digest retain browser download behavior.

## 1.0.1 — 2026-10-14

Phone development release. GitHub Android validation and signed release checks passed; see RELEASE_CHECKLIST.md for evidence and remaining limits.

- Spotify uses one in-app sign-in page, recovers saved sessions, and appears in Accounts without waiting for other providers.
- Genre discovery uses the YouTube catalog, caches successful results, and offers loading placeholders and Retry.
- Automatic update notifications and Settings → App updates select a newer, compatible APK directly from GitHub.
- Issue and crash reporting target Aro’s enabled GitHub issue chooser.

## Earlier unreleased development changes

- Browse-by-genre now uses the YouTube catalog instead of the legacy streaming endpoint, caches successful results, and shows loading placeholders and Retry for empty or failed requests.
- Added a newer-version popup and Settings → App updates. Checks use GitHub release metadata; Download APK opens the matching direct APK asset in the default browser. Automatic checks are cached, and older releases are never offered as updates.
- Enabled repository issues and corrected inherited announcement defaults. Both issue reporting and the crash dialog open Aro’s issue chooser; crash details remain under the user’s control.

- Fixed Accounts waiting for unrelated cloud-library flows before showing a connected Spotify account.
- Recover existing in-app Spotify sessions at startup and keep connection verification alive when leaving the login screen, while respecting explicit logout.
- Simplified Spotify connection to one sign-in page, removing device-email selection and the browser OAuth experiment. Kept the existing account playlist/liked-song loading and caches. These changes were phone-tested in 1.0.0-rc.4 (code 16) before preparing 1.0.1 (code 17).

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
