# Aro changelog

## 1.0.5 — 2026-10-23

Code 25. The maintainer tested the dev3 preview on their phone and reported all features working. Signed production validation is recorded in RELEASE_CHECKLIST.md.

- Downloads now opens an All songs card for all completed Aro downloads. Album/playlist cards open full offline pages with the existing artwork header, play/shuffle, ordered local tracks, and Download remaining; loose track rows and the details sheet are replaced.

- Start manual playlist song selection on All sources; apply the same local/cloud classification during search and liked filtering.
- Make Library Albums and Artists explicit saved collections. Save or remove them from their detail pages independently of song likes and playlists. Existing liked songs, playlists, and cached metadata are retained.
- Hide deleted Spotify playlists only in Aro and persist that choice across reloads. Library → Playlists can restore hidden entries; Spotify itself is unchanged.
- Replace default genre icons with bundled music photos in rounded, petal, and scalloped shapes. Keep custom icon overrides and existing theme/type settings.
- Join share, download, and lyrics into one player action group with rounded outer ends. Keep the existing queue/output controls.
- Download YouTube audio, including Spotify-matched tracks, through Aro’s resolver and background workers into system Music/Aro. Library Downloads shows only completed files created by this app, with progress, cancel, and retry controls. Files from other apps remain local music. Downloads use AAC/M4A and Opus/Ogg audio, validate size/duration, and remove failed partial transfers. Interrupted transfers restart rather than resume.

- Correct Android audio-library registration: save Opus in Ogg (`.opus`, `audio/ogg`), preserve the original encoded samples, and handle negative initial Opus timestamps.
- Keep album/playlist download cards, original track order, completion counts, offline cover copies, and local-only collection playback in Downloads. Missing tracks can be retried from each card.
- Fold the old Folders/Artists alias into the real Artists tab while preserving the saved tab order.

Unknown artist song totals show Artist instead of an incorrect zero/negative count.

Add Download all to album and playlist details, including continuation pages. Queue submission survives page navigation, skips duplicates and existing downloads, and limits active transfers to two. Downloads shows progress cards with percentage, transferred size, queue/resolve/finalize states, cancel, and retry.

Downloads use a separate resolution cache and finalize streamed audio into a standalone container without re-encoding. Failures now distinguish unavailable audio, interrupted transfer, file finalization, and storage.

This is a local development preview, not a published release. Live account and device coverage is recorded in `RELEASE_CHECKLIST.md`.

## 1.0.4 — 2026-10-18

- Playlist details move loading feedback below the songs and show the scrollbar after the cover collapses, with reserved space beside the list.
- Library Albums groups tracks by actual release identity. Missing/unknown releases and play/view-count labels are hidden without deleting songs, favorites, or playlist membership. Catalog parsing requires an album link instead of guessing from subtitle position; later valid metadata can repair an invalid cached album title. Genuine singles remain separate releases.

## 1.0.3 — 2026-10-17

- Replace inherited app branding across in-app marks, adaptive monochrome layers, legacy launcher resources, and the website with supplied Aro artwork. White is the default; Appearance offers eight persistent icon choices. All choices have safe foreground padding. Notifications use the filled silhouette from `coloroutline.svg`.
- Refine the original illustrated setup pages while retaining their text hierarchy, permission gates, and bottom forward/progress bar. No phone-music scan occurs during setup.
- Add compact playlist/artist cards and saved card/list choices, stronger tonal surfaces, editable playlist artwork, and an Artist-style collapsing playlist cover header. Home song titles and artists stay together without an empty reserved line. Aro branding and Settings open beta details.
- Show Spotify connection state alongside remaining available services. Phone music requires Library → Songs → Import, confirmation, and audio permission. Online Library includes local and saved cloud/Spotify content; offline Library shows local content and opens automatically when imported songs exist. Remote visibility returns after reconnecting.
- Keep lyrics modes in the player capsule and let horizontal equalizer gestures reach the band pager, with tappable page indicators.
- Remove AI provider integration, SDK, key/model/prompt settings, generation, background requests, usage UI, and backup selection. Existing generated playlists remain usable. Legacy Room tables are retained for compatibility, with old queued generation work canceled at startup. Manual playlists, smart rules, Daily Mix, and song editing remain.
- Acknowledge PixelPlay UI inspiration in visible credits; retain code provenance and upstream obligations in `THIRD_PARTY_NOTICES.md` and the existing licenses. Website downloads follow the latest public release, with candidate build status linked separately.

Published from `15d544f` after signed workflow `39058129041` passed. See `RELEASE_CHECKLIST.md` for validation limits and unresolved licensing.

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
