# Aro data and privacy

Aro is a development-stage Android music app. This document describes its current behavior; it is not a store privacy certification.

## On-device data

Aro stores music metadata, playlists, favorites, listening history, settings, artwork caches, and connected-account session information on the device. Android media permissions let the app discover local songs; notification and foreground-service permissions support playback controls and background playback. Other permissions are used by optional device and provider features.

Android backup is enabled. Backup rules exclude named provider preference stores, Spotify session DataStore, and settings DataStore because they can contain credentials. Do not assume every cache or third-party WebView/TDLib file is excluded; a complete account-storage audit remains a release task. Exported backups and logs can contain personal library information and should be handled carefully.

## Connected services

YouTube/YouTube Music receives catalog and playback requests. Spotify receives sign-in and catalog/playlist requests; Aro stores a session cookie in private app preferences and matches Spotify metadata to YouTube for playback. Spotify handles credentials on its own sign-in page; Aro has no separate device-email chooser.

Optional server and cloud features send requests to the service you connect. Optional AI features send configured requests to the selected AI provider. These services operate under their own terms and privacy policies. Aro is not affiliated with them.

## Update checks and issue reports

Aro checks the public GitHub releases API for newer APKs, caching release metadata and check times on the device. This sends a network request to GitHub without Aro account credentials or listening history. Download APK opens the selected release asset in your default browser; Android handles installation.

Report controls open the repository’s issue chooser. Crash details are not uploaded automatically; copying or sharing them remains your choice.

## Managing data

Use account disconnect/logout controls where available. Android's app settings can clear app data or uninstall Aro; clearing data removes local app state and requires account reconnection. Copies exported outside the app and information held by external services are managed separately.

Before sharing a report, remove credentials, account identifiers, private playlist details, and sensitive filenames. For security concerns, follow [SECURITY.md](SECURITY.md).
