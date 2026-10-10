# Meld Spotify integration in Aro

Connect Spotify from **Settings → Accounts → Spotify**. Aro retains its existing
player, search, library, and playlist layouts; the account connection is added to
the existing service list.

The port uses Meld's web-session authentication (`sp_dc`), GraphQL catalog client,
REST profile fallback, persisted hash refresh, profile cache, recommendation
scoring/diversification, and title/artist/duration matching with studio-version
preference. Developer client credentials are not required by this authentication
path and are not embedded in the APK.

When connected, Spotify supplies search results, profile-based home songs, liked
songs, and library playlists. Tracks resolve to YouTube Music audio through the
existing InnerTube engine. Streaming URLs remain ephemeral. When Spotify is
unavailable, the existing YouTube catalog remains available.

Aro's numeric song IDs, album/artist navigation, media item construction,
engagement history, and Room DAOs are adapted explicitly. Local listening ranks
reorder Spotify profile tracks through cached YouTube matches, without passing
local numeric IDs to Spotify. Matching caches preserve manual overrides.

Radio recommendation generation is deferred until after the seed starts playing.
The port includes progressive Spotify playlist and liked-song queue providers,
source-level shuffle, and authenticated page fetching. Existing playlist screens
resolve full playlists into their ordered Aro song lists; playlist previews from
search are limited to the first 50 source entries. Unmatchable songs are skipped.

The v41 → v42 migration creates the match cache without resetting the existing
library. Spotify tokens and session cookies are excluded from Android backup.
Logout clears the session and profile caches. Network cancellation propagates
when a search or playback source is replaced.

The web token endpoint, rotating TOTP data, and GraphQL hashes are upstream
service dependencies. Successful compilation and automated tests cannot verify
account-specific login or guarantee that these private endpoints keep working.
Live login and audio playback must be checked with a Spotify account.

Spotify playlist mutation APIs are included in the client. Existing local playlist
editing controls do not currently write edits back to Spotify.

## Home, radio, and playback fixes

Home emits cached Room songs while refreshing the catalog. Playlist-only YouTube
home shelves resolve into tracks, concurrent requests share their in-flight work,
and the Spotify home request has a bounded wait before YouTube fallback.

Playing a search result starts the selected song and its radio provider. It no
longer inserts every literal search match into the queue. YouTube radio continuation
tokens and subtitle durations are parsed from the actual response structures.

The player routes `sabr://` streams through InnerTubeX's SABR audio demultiplexer
and retains extraction headers for ordinary HTTP streams.

Verification on 2026-10-06: debug and instrumentation APKs built, 55 targeted unit
tests passed, and the opt-in `InnerTubeLivePlaybackTest` passed on the connected
Android phone. The live test checked nonempty home tracks, related Bhagwaan radio
results, and decoded audio advancing beyond 1.5 seconds. It used muted playback;
Spotify account login was not part of this test.

Home now preserves YouTube Music's named catalog shelves instead of flattening
all tiles into songs. Explore contributes new albums/singles and trending music;
continuation pages load as the user scrolls. Album and artist browse IDs are
registered and tile headers are cached before opening existing detail screens.
Playlist tracks are fetched on tap, rather than fetching every home tile at startup.

Artwork parsing accepts queue thumbnails, list-row thumbnails, and carousel
thumbnail renderers, choosing the largest dimensions. Google artwork requests
1080px images; home cards decode at their displayed pixel size. Previously cached
YouTube songs with missing artwork use a video-thumbnail fallback, and restored
media items preserve known covers when artwork metadata is absent.

Remote detail loading now saves search album/artist headers before navigation.
Album browse tracks retain the selected album ID and source order. Remote track
persistence creates every required parent (including missing-metadata IDs) and
song/artist junction rows, so artist pages include featured tracks. Canonical
artist headers and custom artwork survive track refreshes. Unknown remote artist
counts display an artist label rather than claiming zero songs.

Verification on 2026-10-09: 63 targeted unit tests passed. Three live Android
checks passed after installing a fresh development build: NO FORMULA and Raga
search-to-detail loading, home continuation plus 1080px radio artwork, and actual
Bhagwaan audio decoding beyond 0:00. The installed development APK now uses the
local Gradle debug signing key; future builds with that key can update it in place.

## Artist catalog, listening feed, and saved library (2026-10-09)

- Artist details now retain named catalog shelves from Meld's YouTube artist-page approach: top songs, albums, singles/EPs, videos, featured playlists, and related artists. Real subscriber/monthly-audience text comes from the artist header. Supported section endpoints open additional releases/tracks, and catalog failures can be retried.
- Remote artist artwork keeps its catalog image rather than replacing it with an unrelated name-based artist lookup. Browse ID mappings persist across process restarts without changing the database schema.
- Home observes recent listening and likes, includes recent plays and a pinnable song speed dial, and obtains related tracks from the latest song's radio. These tracks take priority in the existing Your Mix grid. Older requests cannot overwrite a newer seed. Discovery play/shuffle actions use their displayed songs.
- Library songs now observe Room instead of fetching trending songs. Cached YouTube/Spotify audio mappings become library members when liked locally or included in a user playlist. Album/artist/folder lists, sorted queues, pagination, and counts use the same membership rule. Cached catalog entries remain available for browsing/playback/history.
- Played remote song metadata is retained even when playback starts outside Home. Saving a search result also writes its required parent rows and artist relationships.
- Final build and 50 targeted unit tests passed. Tests cover all six Raga artist shelves, real header counts, saved-library reads, playback regressions, current-song recommendations, pin/unpin persistence, and cancellation of older recommendation requests.
- Playlist deletion now removes its song memberships in one Room transaction. Library queries join memberships to existing playlists, so rows left by older deletions cannot keep tracks in the library.
- Final on-device verification passed: **5 live tests** in 19.988 seconds, covering full Raga catalog/extra releases, album navigation, saving and removing library tracks, playlist deletion, queue/count consistency, online/offline filters, persisted browse IDs, listening-based home recommendations, speed-dial pins, high-resolution artwork, and actual audio decoding. The final build also passed **50 unit tests**, with no failures/errors.

## Stable home feed and artwork theme (2026-10-10)

- Home orders recent plays, speed dial, Daily Mix, then Your Mix and catalog shelves. Song changes and pin/like updates rebuild local shelves without replacing the recommendation snapshot.
- Catalog shelves and recommendations persist on disk for six hours. Fresh snapshots restore across screen recreation and process restarts without browsing again. Manual pull-to-refresh bypasses the backend home cache and uses the latest listening seed; an expired snapshot stays visible until the replacement is ready. Failed refreshes retain existing content. Pagination loading is separate from the pull indicator.
- Daily Mix and the fallback Your Mix also persist complete song metadata, renew after 24 hours, and share an active fetch instead of cancelling/restarting it. Home no longer starts a separate trending request every time its screen appears.
- The shared app theme uses the active player's artwork palette. Dark page backgrounds composite 35% of the player's container color over soft charcoal, while the navigation surface uses the player's actual container color and paired contrast roles. Home, Search, Library, and their bottom fades share the tint. Light mode uses a smaller tint over white.
- 47 targeted unit tests pass, covering snapshot restoration, expiry, manual refresh, failure retention, duplicate request prevention, song/pin stability, daily mix restoration, and artwork text/tab contrast. Neutral artwork desaturation uses Android Color APIs and is also checked in the on-device contrast test.
- Final device verification: **6 tests passed** in 22.612 seconds, including persistent/stable home recommendations, recent/dial ordering, artist/album/library regressions, high-resolution covers, real audio decoding, and text/tab contrast across all four palette styles with colored, white, and black artwork in both light and dark modes. Contrast roles retain their generated tones when readable and use a contrasting neutral endpoint when grayscale conversion lowers contrast.
- Manual phone checks confirmed visible artwork tint on Home, Search, and the main Library surface, identical mini-player/tab surface colors, unchanged disk snapshots across song changes and reopening, and a new snapshot/seed after a pull gesture. The final debug APK is installed on the connected phone.
