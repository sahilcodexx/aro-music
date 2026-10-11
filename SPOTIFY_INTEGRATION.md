# Spotify in Aro

Spotify supplies account playlists, liked songs, search results, artists, albums, and recommendation seeds. Audio playback resolves matching YouTube Music tracks; Spotify audio streaming is not implemented.

## Authentication

`SpotifyConnectActivity` hosts the provider's web sign-in. The current integration persists the Spotify session cookie and restores the client session. Device-account selection only prefills email fields on trusted Spotify/Google hosts; passwords and authentication remain with the provider. A reconnect or logout clears playlist snapshots to prevent showing the previous account's data. This is not a native Spotify OAuth SDK integration, and provider changes can break the session flow.

## Playlist loading

`SpotifyRepository` fetches the remote header and paginated tracks. A bounded mapper resolves tracks against YouTube in source order and emits incremental results. `PlaylistViewModel` persists newly resolved tracks without rewriting the entire playlist for every callback. The page displays the remote title/count immediately and a progress indicator while additional tracks arrive.

`SpotifyPlaylistCache` stores complete snapshots for six hours, including header, artwork, songs, and source count. Reopening a fresh snapshot avoids mapping and network work. Preview requests do not replace complete snapshots; explicit imports force a fresh load. A full cache write occurs only after all source pages finish. Playlist imports create local editable copies, not a bidirectional Spotify sync.

## Validation

Unit coverage includes snapshot persistence/expiry, single-flight loading, previews, forced refresh, progressive mapping, pagination, queue behavior, and import parsing. Device tests cover trusted-host email prefill and authenticated playlist counts/loading. Live tests require an authenticated development device and network access; emulator CI does not contain a real Spotify account.

Earlier device measurements for a 29-track playlist with existing YouTube matches were approximately 1.1 seconds to first track, 1.5 seconds to completion, and 60 milliseconds for cached reopening. These are measured warm-match results, not a guarantee for cold mapping, large playlists, or other networks.

Private, unavailable, region-restricted, removed, or unmatched tracks can produce fewer playable songs than the remote count. Source counts describe the Spotify playlist; local track counts describe the playable/imported copy. Report discrepancies with the source playlist and unavailable-track information, without posting cookies or tokens.
