# Aro data and privacy

Aro is a development-stage Android music app. This document describes its current behavior; it is not a store privacy certification.

## On-device data

Aro stores music metadata, playlists, favorites, listening history, settings, artwork caches, and connected-account session information on the device. Android media permissions let the app discover local songs; notification and foreground-service permissions support playback controls and background playback. Other permissions are used by optional device and provider features.

Android backup is enabled. Backup rules exclude named provider preference stores, Spotify session DataStore, and settings DataStore because they can contain credentials. Do not assume every cache or third-party WebView/TDLib file is excluded; a complete account-storage audit remains a release task. Exported backups and logs can contain personal library information and should be handled carefully.

## Unreleased phone import and playlist artwork

The current source restores an explicit **Library → Import** phone-music flow. A confirmation precedes Android audio permission and a separate background worker reads MediaStore metadata only after that choice. Audio files remain on the device; imports store metadata and content references in Room. Repeat imports preserve existing edits, favorites, and playlists. The worker does not upload audio. Audio permission is no longer required to complete onboarding. These changes have not yet been built or published in an APK.

Selecting a custom playlist cover copies that image into Aro's internal files. Spotify cover overrides are stored locally and do not change the Spotify account's playlists. Offline library filtering changes visibility and retains cached service data; reconnecting restores visibility.

## Connected services

YouTube/YouTube Music receives catalog and playback requests. Spotify receives sign-in and catalog/playlist requests; Aro stores a session cookie in private app preferences and matches Spotify metadata to YouTube for playback. Spotify handles credentials on its own sign-in page; Aro has no separate device-email chooser.

Optional server and cloud features send requests to the service you connect. The unreleased source removes AI provider clients, model/key settings, and generation workers. The published 1.0.2 APK predates this removal. Existing AI preferences/cache/history are not actively used by the new source; legacy Room tables remain solely to preserve upgrade compatibility. These services operate under their own terms and privacy policies. Aro is not affiliated with them.

## Update checks and issue reports

Aro checks the public GitHub releases API for newer APKs, caching release metadata and check times on the device. This sends a network request to GitHub without Aro account credentials or listening history. When the asset includes a SHA-256 digest, Aro downloads it in resumable ranges into private cache using a background worker. It verifies the digest and the installed Aro signing certificate before offering Android installation. Android may ask you to allow installation from Aro. Assets without a digest open in the browser. GitHub is the only download host.

Report controls open the repository’s issue chooser. Crash details are not uploaded automatically; copying or sharing them remains your choice.

## Song image sharing

Aro renders the selected song’s title, artist, and artwork on the device. Artwork may be fetched from its existing provider URL. Sharing creates a PNG in private cache and grants temporary read access through Android’s share sheet; Aro does not automatically send it to another person or service. Old share images are removed when generating later cards after 24 hours, or when Android clears the cache.

Listening history can retain track title, artist, album, artwork URL, and genre so streaming statistics remain available when catalog caches change. This stays on the device unless you export or back up the data.

## Managing data

Use account disconnect/logout controls where available. Android's app settings can clear app data or uninstall Aro; clearing data removes local app state and requires account reconnection. Copies exported outside the app and information held by external services are managed separately.

Before sharing a report, remove credentials, account identifiers, private playlist details, and sensitive filenames. For security concerns, follow [SECURITY.md](SECURITY.md).
