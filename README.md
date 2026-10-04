# Aro

> An Android music player. Work in progress — this file is a placeholder and will be
> rewritten properly once the current milestone settles.

## Status

Early development. The app builds and installs, but the catalog layer is being
reworked and several screens are known to be incomplete.

## What it does (so far)

- Local library browsing (albums, artists, genres, folders)
- Playback via Media3, with a dual-player pre-roll for gapless transitions
- Multiple cloud sources — Telegram, NetEase, Navidrome/Subsonic, Jellyfin, Google Drive, QQ Music
- YouTube Music (InnerTube) as a streaming source
- Playlists, favourites, listening history, lyrics (LRCLIB)
- Android Auto, Wear OS, media notification, quick settings tiles

## Build

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Requirements

- JDK 17 or newer
- Android SDK with API 37 platform
- `local.properties` pointing at your SDK

## Note

The YouTube Music backend depends on a local build of
[InnerTubeX](https://github.com/MetrolistGroup/innertubex), published to `~/.m2`.
See `settings.gradle.kts` and `gradle/libs.versions.toml`.

## License

GPL-3.0 — see `LICENSE`.