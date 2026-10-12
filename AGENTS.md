# Aro repository instructions

## Scope and behavior

These instructions apply to this repository. Keep Aro branding and the established UI style. Preserve upstream copyright and license notices. Read `LICENSE_STATUS.md` before changing licensing or preparing publication; branding changes do not confer redistribution rights.

## Implementation

Use `rg` for searches and the checked-in Gradle wrapper. Run Gradle operations sequentially in a checkout. Do not use unpublished Maven-local artifacts. Keep secrets, signing keys, passwords, session cookies, and personal device data out of source, logs, and documentation.

The phone app lives in `app/`; shared code in `shared/`; the adapted YouTube library in `innertubex/`. Wear OS is outside the first phone release's verified scope. Package names are `com.aro.music` and `com.aro.music.debug`.

## Verification

Run tests appropriate to changed behavior. Database changes require migration coverage that preserves legacy rows. Playback changes require real decoding/position checks. Account tests require an explicitly connected account; record skipped coverage honestly. Validate shrinking-sensitive behavior in release builds.

Use JDK 21, SDK package `platforms;android-37.0`, and build tools 36.0.0. Common tasks are `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:testReleaseUnitTest`, and `:app:lintRelease`. CI lists its account-independent device classes in `.github/workflows/phone-debug.yml`.

Do not regenerate `app/lint-baseline.xml` to conceal new errors. A successful build does not prove every phone, setting, or cloud service works. Update `RELEASE_CHECKLIST.md` with concrete evidence and limitations.

## Documentation and releases

Keep `README.md`, `CHANGELOG.md`, and affected guides aligned with actual behavior. Preserve credits at the end of the README. Do not invent release history or claim unresolved licensing is GPL-compatible.

Increase Android version codes for upgrades, preserve the production signing key, and build split APKs and AAB in separate invocations. The manual release workflow prepares a draft prerelease; publication requires the project's release gates to be satisfied and user authorization.
