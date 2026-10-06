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
