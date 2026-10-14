# Spotify in Aro

Spotify supplies account playlists, liked songs, search results, artists, albums, and recommendation seeds. Audio playback resolves matching YouTube Music tracks; Spotify audio streaming is not implemented.

## Authentication

`SpotifyConnectActivity` opens one Spotify sign-in page inside Aro. There is no Aro device-email picker or browser OAuth flow. Spotify handles email/password and any provider sign-in choices it offers on that page; Aro does not supply a separate Google account chooser.

After sign-in, Aro reads its own WebView session cookies and verifies the Spotify connection. Verification continues if the screen is closed; a failure shows a retry action. Startup restores a saved session or recovers an existing signed-in WebView session unless the user explicitly logged out. Accounts shows Spotify independently of unrelated cloud providers' library loading.

A reconnect or logout clears account caches to prevent showing the previous account's data. No Spotify Developer callback or client ID setup is needed for this flow. Provider changes can break the cookie integration; sessions may expire and require sign-in again.

## Playlist loading

`SpotifyRepository` fetches the remote header and paginated tracks. A bounded mapper resolves tracks against YouTube in source order and emits incremental results. `PlaylistViewModel` persists newly resolved tracks without rewriting the entire playlist for every callback. The page displays the remote title/count immediately and a progress indicator while additional tracks arrive.

`SpotifyPlaylistCache` stores complete snapshots for six hours, including header, artwork, songs, and source count. Reopening a fresh snapshot avoids mapping and network work. Preview requests do not replace complete snapshots; explicit imports force a fresh load. A full cache write occurs only after all source pages finish. Playlist imports create local editable copies, not a bidirectional Spotify sync.

## Validation

Unit coverage includes snapshot persistence/expiry, single-flight loading, previews, forced refresh, progressive mapping, pagination, queue behavior, and import parsing. Device tests cover authenticated playlist counts/loading; the retired device-email prefill tests are removed. Live tests require an authenticated development device and network access; emulator CI does not contain a real Spotify account.

Earlier device measurements for a 29-track playlist with existing YouTube matches were approximately 1.1 seconds to first track, 1.5 seconds to completion, and 60 milliseconds for cached reopening. These are measured warm-match results, not a guarantee for cold mapping, large playlists, or other networks.

Private, unavailable, region-restricted, removed, or unmatched tracks can produce fewer playable songs than the remote count. Source counts describe the Spotify playlist; local track counts describe the playable/imported copy. Report discrepancies with the source playlist and unavailable-track information, without posting cookies or tokens.
