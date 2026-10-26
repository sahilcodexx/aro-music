# Developing Aro

## Requirements

- JDK 21; use the Gradle wrapper and checked-in toolchain configuration.
- Android SDK `platforms;android-37.0`, build tools 36.0.0, and platform tools.
- An Android 11/API 30 or newer phone or emulator for device tests.
- Adequate free disk and memory for Android tooling, native libraries, and R8.

Set `sdk.dir=/absolute/path/to/Android/Sdk` in ignored `local.properties` if needed. Do not commit machine paths or credentials. Gradle can download the configured JDK toolchain and required native components.

## Repository map

| Path | Responsibility |
| --- | --- |
| `app/` | Phone app: Compose UI, repositories, Room database, playback, and integrations |
| `shared/` | Shared models and functionality |
| `innertubex/` | Adapted YouTube protocol and stream-resolution sources |
| `wear/` | Wear OS sources; outside current release validation |
| `baselineprofile/` | Profile-generation infrastructure |
| `.github/workflows/` | Android validation and signed release preparation |
| `scripts/check-apk-native.py` | ARM64 native ELF alignment validation |

## Build and test

Run Gradle operations sequentially in one checkout:

```sh
./gradlew :app:assembleDebug --no-daemon --console=plain
./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./gradlew :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain
./gradlew :app:connectedDebugAndroidTest -Paro.enableAbiSplits=false --no-daemon
```

Album query regression checks can also run without a device using the SQL extracted from `MusicDao.kt`:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_library_album_queries.py'
```

On a computer with about 8 GB of RAM, a large Gradle heap and many R8 threads can exhaust memory while other applications are open. If Linux kills the Java process, try this bounded local release build:

```sh
./gradlew :app:assembleRelease --no-daemon --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx2560m -Xms256m -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
  -Dorg.gradle.workers.max=1
```

The command-line heap setting overrides a larger value in user Gradle properties. Run one Gradle operation at a time; fewer threads can take longer but reduce peak memory. A `Killed process ... (java)` kernel entry establishes an operating-system memory kill rather than a Kotlin compilation error. Check the build log before rerunning.

The full connected suite includes live network/account tests. CI selects account-independent tests on API 30 and 36; inspect `phone-debug.yml` for the exact list. A skipped live test is not evidence that a provider works. The checked-in lint baseline records inherited debt; green lint checks do not resolve those existing errors.

For database changes, preserve legacy data and test migrations into the current schema. For stream changes, check actual decoding and advancing playback time rather than only URL resolution. Verify shrinking-sensitive changes in a signed release build.

## Signing and packaging

Version name and code live in `gradle.properties`. Increase the version code for Android updates. Production uses `com.aro.music`; debug uses `com.aro.music.debug`.

Local release signing accepts `ARO_KEYSTORE_PATH`, `ARO_STORE_PASSWORD`, `ARO_KEY_PASSWORD`, and `ARO_KEY_ALIAS`, or ignored `keystore.properties` keys `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Never commit signing material. Back up the original key and password securely outside this checkout.

APK splits and the AAB must be built separately:

```sh
./gradlew :app:assembleRelease --no-daemon
# Preserve APKs before changing packaging mode.
./gradlew :app:bundleRelease -Paro.enableAbiSplits=false --no-daemon
```

AGP can leave split shrunk-resource intermediates that conflict with bundle packaging. The release workflow preserves APKs and clears only the generated release resource directory before building the AAB. Use a separate clean checkout for manual bundle builds if necessary.

## GitHub workflows

`phone-debug.yml` validates pushes and pull requests to `main`: unit tests, lint, APK builds, and minimum/current Android emulator checks. Actions are pinned to commit SHAs.

`phone-release.yml` runs manually from `main` in the `production` environment. Its secrets are `ARO_KEYSTORE_BASE64`, `ARO_STORE_PASSWORD`, `ARO_KEY_PASSWORD`, and `ARO_KEY_ALIAS`. It runs release checks, builds signed APKs/AAB, verifies APK signatures and native alignment, and prepares checksums and a draft prerelease. Draft creation is not permission to publish.

```sh
gh run list
gh workflow run phone-debug.yml --ref main
```

Read the [licensing status](LICENSE_STATUS.md) and [release checklist](RELEASE_CHECKLIST.md) before dispatching a release. The maintainer authorized a development candidate; public/store distribution still requires compatible rights. Review the exact signed artifacts on a real device before publication. Do not replace the signing key or publish a stable version just to bypass a failed gate.

## Common failures

- SDK not found: install the exact `platforms;android-37.0` package, not `platforms;android-37`.
- Emulator cannot boot: check runner disk, RAM, virtualization support, and image architecture.
- KSP/build cache corruption: stop overlapping Gradle processes before rebuilding.
- No artifacts: inspect the earlier failed setup/build step first.
- Provider failures: distinguish network, session expiry, region restrictions, and catalog parsing; sanitize logs.

## Saved collections and audio downloads

Album/artist membership and Spotify playlist hiding are stored separately from song likes in the existing Preferences DataStore. Library collection flows read those explicit saves; Room’s catalog parents remain metadata, not membership. No Room schema change or destructive migration is introduced.

Audio downloads reuse the InnerTube resolver and Media3 HTTP/SABR transports in a Hilt WorkManager worker. Completed files are published through MediaStore into Music/Aro and indexed as local audio. A separate record of created MediaStore IDs, checked against the owning package, drives Library Downloads. Existing local music is not automatically marked downloaded. MediaStore IDs keep local copies out of the remote album track list. Supported sources are YouTube and Spotify’s YouTube matches; other cloud providers are not downloaded by this preview. Work retries restart the transfer instead of resuming bytes.

Host checks include `SavedLibraryRepositoryTest`, `SpotifyPlaylistImportTest`, and `AudioDownloadsTest`. Verify real transfer, cancellation, process death, external file deletion, file visibility, and offline decoding/position advancement on a device before publishing this feature. Do not install a production package over the maintainer’s regular app to test the preview.

Download extraction disables HLS manifests and uses a separate SABR cache from playback. Compressed samples pass through MediaExtractor/MediaMuxer to finalize M4A/Ogg-Opus containers; no audio effects or encoding are applied. `NativePlaybackCompatibilityTest.fragmentedDownloadFinalizesWithoutChangingAudioSamples` compares compressed sample digests and checks real decoding/position with synthetic audio.

`aro_download_collections_v1` stores album/playlist identity, title, ordered source IDs, and cover references independently of saved-library membership. Cover copies live under app-private `download-covers`. Downloads landing contains an All songs card plus collection cards; `downloaded_collection/{collectionId}` opens a full offline page using the shared playlist artwork header. All songs includes standalone and collection files, while collection playback resolves only owned completed download IDs in source order; missing/deleted copies remain partial. Legacy `FOLDERS`/`ARTISTS` tab keys normalize to `ARTIST` before deduplication.

Android rejects `audio/webm` in the audio media collection on the tested phone. Opus is remuxed into Ogg and registered as `audio/ogg` with an `.opus` extension; AAC remains `.m4a`. Negative Opus preroll timestamps are normalized instead of ending extraction early.

## Home layout, player motion and lyric cards

`HomeLayoutPreferences` uses the existing settings DataStore with `home_section_order_v1` and `home_hidden_sections_v1`. It normalizes unknown/duplicate section IDs, appends new defaults, and retains at least one visible group. Saving replaces only these two keys. Home groups keep their stable LazyColumn item keys and existing loading/playback actions.

`PlayerArtworkMotion` lives inside the player layers and tracks measured mini/full artwork bounds. The overlay follows the sheet expansion fraction; original images remain visible whenever endpoints or the loaded overlay are unavailable. Orientation and track changes reset the geometry. No playback/DSP or database changes are involved. Unit checks cover layout persistence/normalization and interpolation. `EditHomeSheetDeviceTest` covers saving visibility and resetting; run it on an emulator or designated test device before release.

Dev2 makes the artwork overlay a zero-size layout so its cover never contributes to the player container measurement. Delayed album rendering retains its existing transition. `PlaylistSongResolver` persists missing remote metadata from supplied songs or the stored playback queue before explicit playlist saves, verifies all selected IDs are readable, and avoids overwriting existing rows. Old playlists can only recover uncached rows while their metadata is still in that queue snapshot.

The update popup renders a bounded Markdown subset with stacked table cards. HTTPS links are clickable; unsafe schemes are rendered as plain labels, and remote images are not loaded.

Lyric cards use `LyricCardSheet` for up to six selected lines/300 characters in original song order, then reuse `SongShareSheet` and `SongShareCard` for palette, preview and temporary PNG sharing. Synced text is preferred, with plain lyrics as fallback; no additional lyrics request or storage permission is needed. The lyric cover fits text without ellipsis and retains the existing metadata/brand panel. Home-editor and lyric-card labels are currently English.
