# Aro performance validation

Aro uses bounded mapping concurrency, source pagination, persistent Spotify playlist snapshots, and cached home feeds to reduce repeated work. Full playlist snapshots live for six hours; previews do not overwrite them. The home feed does not rebuild for every track change, and users can explicitly refresh it.

Home loading placeholders reserve the recently played and speed-dial sections before daily mix, preventing the earlier visible jump. Empty-state and loading-state behavior must be tested separately. Changing artwork updates colors without needing to invalidate the recommendation cache.

The measured warm-match Spotify playlist loading results are documented in [SPOTIFY_INTEGRATION.md](../SPOTIFY_INTEGRATION.md). They are not cold-start benchmarks. No current evidence establishes startup time, memory use, battery consumption, or jank on every supported phone.

Before stable release, measure a cold launch, cached launch, large local library, large remote playlist, slow/offline network, background playback, and rotation/resizing. Use Android Studio profiling or the benchmark module on an appropriate test device. Keep account tokens and personal library details out of shared performance traces.
