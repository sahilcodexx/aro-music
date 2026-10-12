# Aro development status

The current milestone is the first phone release candidate, 1.0.0-rc.1. The release code increases to 13 to preserve Android upgrade ordering from development builds.

The maintainer authorized a development prerelease despite [unresolved licensing](LICENSE_STATUS.md).

The authoritative checklist is [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). Do not mark a stable release ready merely because the APK builds or the unit suite passes. Real provider accounts, signed release behavior, minimum-SDK coverage, device variations, and inherited lint debt require separate evidence.

Completed development work includes Spotify/YouTube playlist loading, catalog pages, cached home shelves, artwork themes, and local-library persistence. Current release work covers reproducible builds, signing, validation workflows, Aro documentation, and release metadata.

PR #4 is merged into `main`. Final Android validation run 38221040493 passed all three jobs; local debug/release unit suites each passed 237 tests, and local release lint passed. The signed candidate passed a local-audio smoke test. Stable/public distribution still requires compatible licensing and the broader coverage described in the checklist.
